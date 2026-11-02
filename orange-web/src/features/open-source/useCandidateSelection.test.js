// @vitest-environment jsdom

import { beforeEach, describe, expect, it } from 'vitest'
import { useCandidateSelection } from './useCandidateSelection'

const issue = id => ({
  id,
  titleZh: `候选 ${id}`,
  titleOriginal: `Candidate ${id}`,
  repositoryFullName: 'orangevc/example',
  participationStatus: 'TRY',
  difficulty: 'EASY',
  summaryZh: '适合测试候选持久化。',
})

describe('useCandidateSelection', () => {
  beforeEach(() => {
    sessionStorage.clear()
    sessionStorage.setItem('myId', '6')
  })

  it('persists candidate snapshots for the current account', () => {
    const first = useCandidateSelection()
    first.toggle(issue(1))
    first.toggle(issue(2))

    const restored = useCandidateSelection()
    expect(restored.selectedIds.value).toEqual([1, 2])
    expect(restored.candidates.value[0].titleZh).toBe('候选 1')
  })

  it('removes and clears persisted candidates', () => {
    const selection = useCandidateSelection()
    selection.toggle(issue(1))
    selection.toggle(issue(2))
    selection.remove(1)

    expect(selection.selectedIds.value).toEqual([2])
    selection.clear()
    expect(selection.candidates.value).toEqual([])
    expect(sessionStorage.getItem('orangevc:open-source:candidates:6')).toBeNull()
  })

  it('keeps no more than twenty candidates', () => {
    const selection = useCandidateSelection()
    for (let id = 1; id <= 22; id += 1) selection.toggle(issue(id))

    expect(selection.candidates.value).toHaveLength(20)
    expect(selection.selectedIds.value[0]).toBe(3)
    expect(selection.selectedIds.value.at(-1)).toBe(22)
  })

  it('refreshes stored snapshots when current-page data is available', () => {
    const selection = useCandidateSelection()
    selection.toggle(issue(1))
    selection.hydrate([{ ...issue(1), titleZh: '更新后的标题' }])

    expect(selection.candidates.value[0].titleZh).toBe('更新后的标题')
  })
})
