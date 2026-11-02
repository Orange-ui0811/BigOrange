import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import CustomerRecommendationReport from './CustomerRecommendationReport.vue'
describe('客服推荐报告', () => {
  it('展示领域、项目与学习路径，同时标注建议和时间边界', () => {
    const wrapper = mount(CustomerRecommendationReport, { props: { report: {
      direction: 'AI应用开发包含RAG', approach: '建议先做可演示功能', scopeNote: '未接入市场统计',
      timeline: '180天是用户上限，不是完成承诺', learning: [{key:'E:1',name:'acme/demo',personalized:true,steps:['运行示例','改造','测试']}],
    } }, slots: { default: '<article>真实项目卡片</article>' } })
    expect(wrapper.text()).toContain('领域洞察'); expect(wrapper.text()).toContain('真实项目卡片')
    expect(wrapper.text()).toContain('学习路径'); expect(wrapper.findAll('li')).toHaveLength(3)
    expect(wrapper.text()).toContain('不是完成承诺'); expect(wrapper.text()).toContain('实践建议')
  })
  it('历史消息没有报告时仍展示原项目，不捏造新报告', () => {
    const wrapper = mount(CustomerRecommendationReport, {slots:{default:'历史项目'}})
    expect(wrapper.text()).toBe('历史项目'); expect(wrapper.find('ol').exists()).toBe(false)
  })
})
