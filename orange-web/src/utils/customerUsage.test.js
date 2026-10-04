import { describe, it, expect } from 'vitest'
import { customerDuration } from './customerUsage'
describe('customer elapsed time', () => {
  it('uses server milliseconds across a minute boundary', () => expect(customerDuration(2794, '2026-10-03 15:10', '2026-10-03 15:11')).toBe('2.8'))
  it('does not display fabricated zero or sixty seconds for old minute precision', () => expect(customerDuration(null, '2026-10-03 15:10', '2026-10-03 15:11')).toBe(''))
  it('preserves actual zero', () => expect(customerDuration(0)).toBe('0.0'))
})
