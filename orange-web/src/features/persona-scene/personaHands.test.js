import { describe, expect, it } from 'vitest'
import { createEquipmentRoot } from './equipmentFactory'
import { createOrange } from './orangeFactory'

describe('persona hands scene', () => {
  it('renders two orange hands using independent size and position settings', () => {
    const { group } = createOrange({ bodyColor: '#F37021', handConfig: {
      leftSize: 1.3, leftX: -2.1, leftY: .25, leftZ: .4,
      rightSize: .8, rightX: 1.9, rightY: -.3, rightZ: .1,
    } })
    const left = group.getObjectByName('left-orange-hand')
    const right = group.getObjectByName('right-orange-hand')

    expect(left.position.toArray()).toEqual([-2.1, .25, .4])
    expect(left.scale.x).toBe(1.3)
    expect(right.position.toArray()).toEqual([1.9, -.3, .1])
    expect(right.scale.x).toBe(.8)
  })

  it('anchors each equipped weapon to its corresponding edited hand', () => {
    const equipment = [{ slotCode: 'LEFT_HAND_WEAPON', itemCode: 'LEFT_WEAPON', variantCode: 'SWORD',
      colorHex: '#64748B', equipped: true, unlocked: true, transform: {} }]
    const { root } = createEquipmentRoot(equipment, { handConfig: { leftX: -2.05, leftY: .4, leftZ: .3 } })
    const weapon = root.children[0]

    expect(weapon.userData.focusCode).toBe('left_hand')
    expect(weapon.position.toArray()).toEqual([-2.05, .4, .42])
    expect(weapon.children.length).toBeGreaterThan(0)
  })
})
