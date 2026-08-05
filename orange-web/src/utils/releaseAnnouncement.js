export const RELEASE_ANNOUNCEMENT_VERSION = '2026-08-05-persona-knowledge'

export const releaseAnnouncementStorageKey = accountId =>
  `orangevc:release-announcement:${RELEASE_ANNOUNCEMENT_VERSION}:${String(accountId || 'anonymous')}`

export const hasDismissedReleaseAnnouncement = accountId => {
  if (!accountId) return true
  return localStorage.getItem(releaseAnnouncementStorageKey(accountId)) === 'dismissed'
}

export const dismissReleaseAnnouncement = accountId => {
  if (!accountId) return
  localStorage.setItem(releaseAnnouncementStorageKey(accountId), 'dismissed')
}
