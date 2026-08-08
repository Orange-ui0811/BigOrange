import { describe, expect, it } from 'vitest'
import { formatWorkPrice } from './workPrice'

describe('formatWorkPrice', () => {
  it('formats zero and decimal prices without losing meaning', () => {
    expect(formatWorkPrice(0)).toBe('¥0')
    expect(formatWorkPrice('1299.50')).toBe('¥1,299.5')
  })

  it('uses negotiation copy when no public price exists', () => {
    expect(formatWorkPrice(null)).toBe('价格面议')
    expect(formatWorkPrice('')).toBe('价格面议')
    expect(formatWorkPrice('unknown')).toBe('价格面议')
  })
})
