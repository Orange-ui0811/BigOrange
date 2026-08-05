import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { usePersonaStoryRail } from './usePersonaStoryRail'

describe('usePersonaStoryRail', () => {
  beforeEach(() => {
    window.matchMedia = vi.fn(() => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() }))
  })

  it('keeps compact navigation deterministic and exposes focus copy', () => {
    let feature
    const scrollIntoView = vi.fn()
    const wrapper = mount(defineComponent({
      setup() { feature = usePersonaStoryRail(); return () => null },
    }))
    const query = vi.spyOn(document, 'querySelector').mockReturnValue({ scrollIntoView })
    feature.scrollToFocus('skill')
    expect(feature.activeFocus.value).toBe('skill')
    expect(feature.focusMeta.value.title).toBe('能力目镜')
    expect(scrollIntoView).toHaveBeenCalledWith({ behavior: 'auto', block: 'center' })
    query.mockRestore()
    wrapper.unmount()
  })

  it('observes the most visible desktop node and releases observer state', () => {
    let callback
    const disconnect = vi.fn()
    const observe = vi.fn()
    window.matchMedia = vi.fn(() => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() }))
    window.IntersectionObserver = vi.fn(function (handler) {
      callback = handler
      this.disconnect = disconnect
      this.observe = observe
    })
    const nodes = [{ dataset: { focus: 'goal' } }, { dataset: { focus: 'growth' } }]
    const queryAll = vi.spyOn(document, 'querySelectorAll').mockReturnValue(nodes)
    let feature
    const wrapper = mount(defineComponent({ setup() { feature = usePersonaStoryRail(); return () => null } }))
    feature.observeNodes()
    expect(observe).toHaveBeenCalledTimes(2)
    callback([
      { isIntersecting: true, intersectionRatio: .2, target: nodes[0] },
      { isIntersecting: true, intersectionRatio: .8, target: nodes[1] },
    ])
    expect(feature.activeFocus.value).toBe('growth')
    feature.scrollToFocus('unknown')
    expect(feature.focusMeta.value.title).toBe('你的原创橙子')
    wrapper.unmount()
    expect(disconnect).toHaveBeenCalled()
    queryAll.mockRestore()
    delete window.IntersectionObserver
  })

  it('supports legacy media-query listeners', () => {
    const addListener = vi.fn()
    const removeListener = vi.fn()
    window.matchMedia = vi.fn(() => ({ matches: true, addListener, removeListener }))
    const wrapper = mount(defineComponent({ setup() { usePersonaStoryRail(); return () => null } }))
    expect(addListener).toHaveBeenCalledOnce()
    wrapper.unmount()
    expect(removeListener).toHaveBeenCalledOnce()
  })
})
