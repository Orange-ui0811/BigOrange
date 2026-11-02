import request from './request'

export const DEFAULT_REWARDS = Object.freeze({
  work: 50,
  job: 50,
  competition: 20
})

export async function getRewardPricing() {
  try {
    const response = await request.get('/app/config/rewards')
    return {
      ...DEFAULT_REWARDS,
      ...(response.data || {})
    }
  } catch {
    return { ...DEFAULT_REWARDS }
  }
}
