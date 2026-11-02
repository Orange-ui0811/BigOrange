import { describe, it, expect } from 'vitest'
import { normalizeCustomerText, customerDifficulty } from './customerText'
describe('客服文字与难度归一化',()=>{
  it('旧回答转义段落还原，代码和路径不改',()=>{
    expect(normalizeCustomerText('你好\\n\\n继续')).toBe('你好\n\n继续')
    const code='`\\n\\n`\n```js\n"\\n\\n"\n``` C:\\new'
    expect(normalizeCustomerText(code)).toBe(code)
  })
  it('数据库难度显示中文，未知不伪造',()=>{
    expect(customerDifficulty('ADVANCED')).toBe('进阶')
    expect(customerDifficulty('EASY')).toBe('入门')
    expect(customerDifficulty(null)).toBe('待核实')
  })
})
