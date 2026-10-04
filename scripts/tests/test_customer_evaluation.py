import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('customer_eval', Path(__file__).parents[1] / 'customer_evaluation.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class CustomerEvaluationTest(unittest.TestCase):
    def test_unknown_usage_is_not_reported_as_zero_cost(self):
        row = module.evaluate([{'metrics': {'modelCalls': [{'status': 'FAILED'}]}}])['B']
        self.assertEqual(row['callsWithUnknownUsage'], 1)
        self.assertIsNone(row['costUsd'])
        self.assertFalse(row['releaseApproved'])

    def test_duplicate_rounds_are_not_independent_cases(self):
        rows = [{'caseId': 'one', 'manualPassed': True} for _ in range(40)]
        # Approval additionally needs the frozen dataset audit; this utility does not invent it.
        self.assertEqual(module.evaluate(rows)['B']['runs'], 40)
        self.assertFalse(module.evaluate(rows)['B']['releaseApproved'])

    def test_critical_failure_blocks_passing_aggregate(self):
        rows = [{'caseId': str(i), 'manualPassed': True, 'criticalFailure': i == 0} for i in range(40)]
        self.assertFalse(module.evaluate(rows)['B']['releaseApproved'])

    def test_p95_and_all_attempt_usage(self):
        rows = [{'durationMs': i, 'metrics': {'modelCalls': [{'usage': {'total_tokens': 10}}]}} for i in range(1, 21)]
        result = module.evaluate(rows)['B']
        self.assertEqual(result['p95Ms'], 19)
        self.assertEqual(result['reportedTotalTokens'], 200)

if __name__ == '__main__':
    unittest.main()
