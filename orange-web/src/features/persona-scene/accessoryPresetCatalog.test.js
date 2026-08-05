import { describe, expect, it } from 'vitest'
import { accessoryPreset, variantsForSlot } from './accessoryPresetCatalog'

const slots = ['GOAL_CROWN', 'INTEREST_ORBIT', 'SKILL_VISOR', 'RESOURCE_PACK', 'GROWTH_TRAIL',
  'LEFT_HAND_WEAPON', 'RIGHT_HAND_WEAPON']

describe('accessory preset catalog', () => {
  it('makes every built-in accessory variant available as an editable blueprint', () => {
    slots.forEach(slot => variantsForSlot(slot).forEach(variant => {
      const preset = accessoryPreset(slot, variant, '#64748B')
      expect(preset.parts.length, `${slot}/${variant}`).toBeGreaterThan(0)
      expect(preset.parts.length, `${slot}/${variant}`).toBeLessThanOrEqual(15)
      expect(new Set(preset.parts.map(part => part.id)).size).toBe(preset.parts.length)
    }))
  })

  it('uses richer layered construction for every weapon type', () => {
    for (const variant of variantsForSlot('LEFT_HAND_WEAPON')) {
      expect(accessoryPreset('LEFT_HAND_WEAPON', variant).parts.length).toBeGreaterThanOrEqual(5)
    }
  })
})
