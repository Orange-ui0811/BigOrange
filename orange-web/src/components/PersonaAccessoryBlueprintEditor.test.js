import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PersonaAccessoryBlueprintEditor from './PersonaAccessoryBlueprintEditor.vue'

const mountEditor = () => mount(PersonaAccessoryBlueprintEditor, {
  props: {
    slotCode: 'GOAL_CROWN',
    title: '方向罗盘',
    defaultColor: '#f59e0b',
  },
})

describe('PersonaAccessoryBlueprintEditor', () => {
  it('builds a layered accessory and mirrors the selected part', async () => {
    const wrapper = mountEditor()
    const libraryButtons = wrapper.findAll('.shape-library button')

    await libraryButtons[0].trigger('click')
    await libraryButtons[2].trigger('click')
    await wrapper.findAll('.part-actions button')[3].trigger('click')

    const changes = wrapper.emitted('change')
    const blueprint = changes.at(-1)[0]
    expect(blueprint.parts).toHaveLength(3)
    expect(blueprint.parts.map(part => part.shape)).toEqual(['TORUS', 'STAR', 'STAR'])
    expect(blueprint.parts[1].transform.x).toBeGreaterThan(0)
    expect(blueprint.parts[2].transform.x).toBeLessThan(0)
  })

  it('limits a blueprint to fifteen parts', async () => {
    const wrapper = mountEditor()
    const addRing = wrapper.find('.shape-library button')

    for (let index = 0; index < 16; index += 1) await addRing.trigger('click')

    const changes = wrapper.emitted('change')
    expect(changes.at(-1)[0].parts).toHaveLength(15)
    expect(addRing.attributes('disabled')).toBeDefined()
  })
})
