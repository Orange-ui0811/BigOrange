import { beforeEach, describe, expect, it, vi } from 'vitest'

const { confirm, warning } = vi.hoisted(() => ({ confirm: vi.fn(), warning: vi.fn() }))
vi.mock('element-plus', () => ({ ElMessage: { warning }, ElMessageBox: { confirm } }))

import { openExternalSafely } from './useSafeExternalNavigation'

describe('openExternalSafely', () => {
  beforeEach(() => {
    confirm.mockReset()
    warning.mockReset()
    vi.spyOn(window, 'open').mockReturnValue({ opener: 'previous' })
  })

  it('does not open an unsafe historical link', async () => {
    expect(await openExternalSafely('http://127.0.0.1/admin')).toBe(false)
    expect(warning).toHaveBeenCalledOnce()
    expect(window.open).not.toHaveBeenCalled()
  })

  it('does not open when the user cancels the departure prompt', async () => {
    confirm.mockRejectedValueOnce(new Error('cancel'))
    expect(await openExternalSafely('https://example.com/demo')).toBe(false)
    expect(window.open).not.toHaveBeenCalled()
  })

  it('opens with browser isolation after confirmation', async () => {
    confirm.mockResolvedValueOnce(true)
    expect(await openExternalSafely('https://example.com/demo')).toBe(true)
    expect(window.open).toHaveBeenCalledWith('https://example.com/demo', '_blank', 'noopener,noreferrer')
  })

  it('opens trusted GitHub links without an extra prompt', async () => {
    expect(await openExternalSafely('https://github.com/example/project')).toBe(true)
    expect(confirm).not.toHaveBeenCalled()
  })
})
