"""Local acceptance: credentials in memory, persistent 50-request ledger, never an 80-run legacy sweep."""
import json, os, re, subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPORT = ROOT / "docs/customer/workflow-v2"

def main():
    REPORT.mkdir(parents=True, exist_ok=True)
    config_raw = subprocess.run(["docker", "compose", "--env-file", str(ROOT / ".env.local"), "-f", str(ROOT / "compose.local.yml"), "config", "--format", "json"], capture_output=True, text=True, timeout=30)
    if config_raw.returncode:
        raise RuntimeError("Cannot resolve local Compose configuration; credentials withheld")
    config = json.loads(config_raw.stdout)["services"]["backend"]["environment"]
    backend = subprocess.run(["docker", "inspect", "nxz-bigorange-backend-1"], capture_output=True, text=True, timeout=15)
    if backend.returncode:
        raise RuntimeError("Local backend container is required; run the existing local launcher first")
    inspected = json.loads(backend.stdout)[0]
    network = next(iter(inspected["NetworkSettings"]["Networks"]))
    cache = next((m.get("Name", m["Source"]) for m in inspected["Mounts"] if m["Destination"] == "/root/.m2"), None)
    allowed = {k: str(v) for k, v in config.items() if k.startswith(("MYSQL_", "REDIS_", "AI_", "RAG_", "GITEE_", "CUSTOMER_", "ORANGE_", "SPRING_"))}
    allowed.update(RUN_WORKFLOW_V2_ACCEPTANCE="true", CUSTOMER_ACCEPTANCE_LIMIT="50", CUSTOMER_ACCEPTANCE_LEDGER="/workspace/docs/customer/workflow-v2/provider-calls.jsonl", WORKFLOW_V2_REPORT="/workspace/docs/customer/workflow-v2/provider-result.json", CUSTOMER_EXECUTION_ENGINE="LANGGRAPH4J", TZ="Asia/Shanghai")
    env = dict(os.environ, **allowed)
    args = ["docker", "run", "--rm", "--name", "orange-workflow-v2-acceptance", "--network", network, "-v", str(ROOT)+":/workspace", "-w", "/workspace"]
    if cache: args += ["-v", cache+":/root/.m2"]
    for key in allowed: args += ["-e", key]
    args += ["maven:3.9.9-eclipse-temurin-21", "mvn", "-B", "-pl", "orange-server", "-am", "test", "-Dtest=CustomerWorkflowProviderAcceptanceTest", "-Dsurefire.failIfNoSpecifiedTests=false"]
    outcome = subprocess.run(args, env=env, capture_output=True, text=True, encoding="utf-8", errors="replace")
    log = outcome.stdout+outcome.stderr
    for key, value in allowed.items():
        if re.search("KEY|TOKEN|PASSWORD|SECRET", key) and len(value)>4: log=log.replace(value,"[REDACTED]")
    (REPORT / "provider-test.log").write_text(log, encoding="utf-8")
    ledger=REPORT / "provider-calls.jsonl"
    print(json.dumps({"exitCode":outcome.returncode,"requests":len(ledger.read_text().splitlines()) if ledger.exists() else 0,"limit":50,"releaseApproved":False}))
    raise SystemExit(outcome.returncode)

if __name__ == "__main__": main()
