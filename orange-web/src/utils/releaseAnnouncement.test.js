import { beforeEach, describe, expect, it } from 'vitest'
import {
  dismissReleaseAnnouncement,
  hasDismissedReleaseAnnouncement,
  releaseAnnouncementStorageKey,
} from './releaseAnnouncement'

describe('release announcement dismissal', () => {
  beforeEach(() => localStorage.clear())

  it('shows the announcement until the current user dismisses it', () => {
    expect(hasDismissedReleaseAnnouncement('7')).toBe(false)
    dismissReleaseAnnouncement('7')
    expect(hasDismissedReleaseAnnouncement('7')).toBe(true)
  })

  it('keeps dismissal state isolated between accounts', () => {
    dismissReleaseAnnouncement('7')
    expect(hasDismissedReleaseAnnouncement('8')).toBe(false)
    expect(releaseAnnouncementStorageKey('7')).not.toBe(releaseAnnouncementStorageKey('8'))
  })

  it('does not show before an authenticated account is available', () => {
    expect(hasDismissedReleaseAnnouncement('')).toBe(true)
  })
})
