import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AiRecordResult from './AiRecordResult.vue'

describe('AiRecordResult', () => {
  it('renders trend analysis as the original result modules instead of JSON', () => {
    const wrapper = mount(AiRecordResult, {
      props: {
        businessType: 'TREND_V2',
        result: {
          analysis: { trend: '增长稳定', prospect: '融合机会增加', demand: '需要复合型人才', summary: '抓住 AI + 游戏交叉点' },
          recommendations: [{ name: '游戏原型', description: '从小项目开始', difficulty: '入门', score: 92, recommendReason: '反馈快' }],
          learning: { timeline: '2-3 周完成第一步', tips: '先跑起来再理解', steps: ['创建窗口', '完成原型'] },
        },
      },
    })

    expect(wrapper.text()).toContain('领域洞察')
    expect(wrapper.text()).toContain('为你推荐')
    expect(wrapper.text()).toContain('学习路径')
    expect(wrapper.text()).toContain('增长稳定')
    expect(wrapper.text()).not.toContain('"analysis"')
  })

  it('renders job matches as readable cards', () => {
    const wrapper = mount(AiRecordResult, {
      props: { businessType: 'JOB_MATCH', result: { matches: [{ position: 'AI 游戏开发', reason: '能力匹配', focus: '智能 NPC' }] } },
    })
    expect(wrapper.text()).toContain('岗位匹配结果')
    expect(wrapper.text()).toContain('匹配原因')
    expect(wrapper.text()).toContain('智能 NPC')
  })
})
