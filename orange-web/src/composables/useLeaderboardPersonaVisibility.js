import { ref } from 'vue'

export const LEADERBOARD_PERSONA_MODEL_KEY = 'orangevc.leaderboardPersonaModelVisible'

export const useLeaderboardPersonaVisibility = (storage = localStorage) => {
  const personaModelVisible = ref(storage.getItem(LEADERBOARD_PERSONA_MODEL_KEY) !== 'false')
  const setPersonaModelVisible = value => {
    personaModelVisible.value = Boolean(value)
    storage.setItem(LEADERBOARD_PERSONA_MODEL_KEY, String(personaModelVisible.value))
  }
  return { personaModelVisible, setPersonaModelVisible }
}
