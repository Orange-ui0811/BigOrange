import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AnalysisStartedContent from './AnalysisStartedContent.vue'

describe('AnalysisStartedContent', () => {
  it('shows the estimate and automatic record message', () => {
    const wrapper = mount(AnalysisStartedContent, {
      props: { estimatedTime: '1～3 分钟' },
    })

    expect(wrapper.text()).toContain('此次分析预计耗时1～3 分钟')
    expect(wrapper.text()).toContain('可以体验平台的其他功能')
    expect(wrapper.text()).toContain('自动保存到左侧“AI 记录”中')
    expect(wrapper.text()).toContain('无需手动重试')
  })
})
