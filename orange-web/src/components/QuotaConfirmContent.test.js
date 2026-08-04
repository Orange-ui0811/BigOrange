import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import QuotaConfirmContent from './QuotaConfirmContent.vue'

describe('QuotaConfirmContent', () => {
  it('shows current, consumed, and remaining quota', () => {
    const wrapper = mount(QuotaConfirmContent, {
      props: { actionLabel: '本次趋势分析', balance: 125, cost: 40 },
    })

    expect(wrapper.text()).toContain('当前可用额度125')
    expect(wrapper.text()).toContain('本次预计消耗40')
    expect(wrapper.text()).toContain('完成后预计剩余85')
    expect(wrapper.text()).toContain('投稿作品')
    expect(wrapper.text()).toContain('投稿比赛')
    expect(wrapper.text()).toContain('投稿岗位')
    expect(wrapper.text()).toContain('审核通过后发放')
  })

  it('shows the quota gap when the balance is insufficient', () => {
    const wrapper = mount(QuotaConfirmContent, {
      props: { actionLabel: '生成贡献指南', balance: 15, cost: 40 },
    })

    expect(wrapper.text()).toContain('当前还差额度25')
    expect(wrapper.find('.quota-confirm-result').classes()).toContain('is-insufficient')
  })
})
