import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PersonaHandEditor from './PersonaHandEditor.vue'

describe('PersonaHandEditor', () => {
  it('edits each hand independently from the right-side controls', async () => {
    const wrapper = mount(PersonaHandEditor, { props: { modelValue: {} } })
    const leftSize = wrapper.find('input[aria-label="左手大小"]')
    const rightX = wrapper.find('input[aria-label="右手左右"]')

    await leftSize.setValue('1.35')
    await rightX.setValue('2.2')

    const updates = wrapper.emitted('update:modelValue')
    expect(updates[0][0].leftSize).toBe(1.35)
    expect(updates[1][0].rightX).toBe(2.2)
    expect(updates[1][0].leftX).toBe(-1.72)
  })

  it('restores symmetric safe defaults', async () => {
    const wrapper = mount(PersonaHandEditor, { props: { modelValue: { leftSize: .7, rightSize: 1.5 } } })
    await wrapper.find('header button').trigger('click')
    const reset = wrapper.emitted('update:modelValue').at(-1)[0]
    expect(reset).toMatchObject({ leftSize: 1, leftX: -1.72, rightSize: 1, rightX: 1.72 })
    expect(wrapper.emitted('change')).toHaveLength(1)
  })
})
