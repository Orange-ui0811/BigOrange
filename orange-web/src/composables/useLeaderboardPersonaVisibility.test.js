import { beforeEach, describe, expect, it } from 'vitest'
import { LEADERBOARD_PERSONA_MODEL_KEY, useLeaderboardPersonaVisibility } from './useLeaderboardPersonaVisibility'

describe('leaderboard persona visibility', () => {
  beforeEach(() => localStorage.clear())

  it('shows the model by default and persists the explicit hidden state', () => {
    const first = useLeaderboardPersonaVisibility()
    expect(first.personaModelVisible.value).toBe(true)
    first.setPersonaModelVisible(false)
    expect(localStorage.getItem(LEADERBOARD_PERSONA_MODEL_KEY)).toBe('false')

    const restored = useLeaderboardPersonaVisibility()
    expect(restored.personaModelVisible.value).toBe(false)
  })
})
