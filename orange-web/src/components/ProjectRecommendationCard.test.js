import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ProjectRecommendationCard from './ProjectRecommendationCard.vue'
const render=props=>mount(ProjectRecommendationCard,{props,global:{stubs:{'el-icon':true,TopRight:true}}})
describe('智能分析与客服共享推荐卡片',()=>{
  it('将通用搭建路线标为用途待核实并禁止导入',()=>{
    const wrapper=render({importable:false,project:{name:'ToolJet/ToolJet',purposeFit:{status:'UNKNOWN',message:'尚无原文证明项目已经具备进销存功能；可以搭建不等于现成功能。'}}})
    expect(wrapper.text()).toContain('用途核验');expect(wrapper.text()).toContain('可以搭建不等于现成功能');expect(wrapper.get('button').attributes()).toHaveProperty('disabled')
  })
  it('对初学者明确展示进阶项目门槛',()=>{
    const wrapper=render({project:{name:'alibaba/nacos',learningFit:{profile:'BEGINNER',status:'CHALLENGING',message:'资料标记为进阶，对初学者有明显门槛。'}}})
    expect(wrapper.text()).toContain('基础与门槛');expect(wrapper.text()).toContain('对初学者有明显门槛')
  })
  it('大型仓库即使目录标为入门也不显示绿色入门结论',()=>{
    const wrapper=render({project:{name:'alibaba/nacos',difficulty:'入门',learningFit:{profile:'BEGINNER',status:'SCOPE_UNVERIFIED',message:'仓库规模较大，适合新手的示例尚未核对。'}}})
    const badge=wrapper.get('.rec-difficulty');expect(badge.text()).toBe('门槛待核对');expect(badge.classes()).not.toContain('easy');expect(badge.attributes('title')).toBe('目录难度：入门')
  })
  it('智能分析的来源提示、参考和方案事件仍可用',async()=>{
    const wrapper=render({project:{name:'技术实践',description:'原分析说明',difficulty:'中等',score:78,evidenceStatus:'SOURCE_MATCHED',references:[{name:'真实仓库',url:'https://github.com/acme/demo'}]}})
    expect(wrapper.text()).toContain('参考链接可在本次资料中找到')
    expect(wrapper.find('a').attributes('href')).toBe('https://github.com/acme/demo')
    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('import')).toHaveLength(1)
  })
  it('客服缺少证据时禁止导入，分析字段和转义段落正确展示',async()=>{
    const wrapper=render({importable:false,scoreLabel:'资料评分',project:{name:'Java例子',difficulty:'ADVANCED',score:81,analysisReason:'说明\\n\\n第二段',modificationPlan:'增补小示例',conditionGaps:['周期尚未核实'],url:'javascript:alert(1)'}})
    expect(wrapper.text()).toContain('进阶');expect(wrapper.text()).toContain('资料评分 81/100')
    expect(wrapper.text()).not.toContain('\\n\\n');expect(wrapper.text()).toContain('周期尚未核实')
    expect(wrapper.find('a').exists()).toBe(false)
    expect(wrapper.get('button').attributes()).toHaveProperty('disabled')
    await wrapper.get('button').trigger('click');expect(wrapper.emitted('import')).toBeUndefined()
  })
})
