import { describe, expect, it } from 'vitest'
import { calculateTickerDurations } from './useSynchronizedTickerMotion'

describe('synchronized ticker motion', () => {
  it('keeps weekly at 30 seconds and gives every track the same pixel speed', () => {
    const distances = { weekly: 2400, beginner: 960, career: 1440 }
    const durations = calculateTickerDurations(distances)

    expect(durations).toEqual({ weekly: 30, beginner: 12, career: 18 })
    expect(distances.weekly / durations.weekly).toBe(distances.beginner / durations.beginner)
    expect(distances.weekly / durations.weekly).toBe(distances.career / durations.career)
  })

  it('uses the safe reference duration until the weekly track can be measured', () => {
    expect(calculateTickerDurations({ weekly: 0, beginner: 960, career: 1440 }))
      .toEqual({ weekly: 30, beginner: 30, career: 30 })
  })
})
