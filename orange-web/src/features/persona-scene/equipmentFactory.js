import * as THREE from 'three'
import { accessoryPreset } from './accessoryPresetCatalog'
import { focusGroup, material, mesh } from './primitives'

const colorOf = item => new THREE.Color(item.colorHex || '#f37021')

const goal = item => {
  const group = focusGroup('goal')
  const color = colorOf(item)
  const ring = mesh(new THREE.TorusGeometry(.75, .055, 16, 72),
    material(color, { metalness: .5, roughness: .25, emissive: color, emissiveIntensity: .12 }), [0, 1.98, 0])
  ring.rotation.x = Math.PI / 2
  group.add(ring)
  const arrow = mesh(new THREE.ConeGeometry(.14, .48, 4), material(0xfff7ed, { metalness: .35 }), [0, 2.08, .03])
  arrow.rotation.z = item.variantCode === 'BEACON' ? 0 : -.25
  group.add(arrow)
  if (item.variantCode === 'ORBIT') group.add(mesh(new THREE.SphereGeometry(.11, 16, 12),
    material(color, { emissive: color, emissiveIntensity: .35 }), [.78, 1.98, 0]))
  return group
}

const interest = item => {
  const group = focusGroup('interest')
  const color = colorOf(item)
  const ring = mesh(new THREE.TorusGeometry(1.84, .022, 10, 96),
    material(color, { metalness: .45, roughness: .3, transparent: true, opacity: .82 }))
  ring.rotation.set(1.12, .25, .18)
  group.add(ring)
  const count = item.variantCode === 'CONSTELLATION' ? 7 : item.variantCode === 'SPARK' ? 5 : 4
  for (let index = 0; index < count; index += 1) {
    const angle = index / count * Math.PI * 2
    group.add(mesh(new THREE.OctahedronGeometry(index % 2 ? .095 : .13, 0),
      material(index % 2 ? color : 0xfff7ed, { emissive: color, emissiveIntensity: .2 }),
      [Math.cos(angle) * 1.84, Math.sin(angle) * .82, Math.sin(angle) * 1.48]))
  }
  return group
}

const skill = item => {
  const group = focusGroup('skill')
  const color = colorOf(item)
  const width = item.variantCode === 'WIDE' ? .55 : item.variantCode === 'ROUND' ? .38 : .44
  const lens = material(color, { metalness: .55, roughness: .18, transparent: true,
    opacity: .72, emissive: color, emissiveIntensity: .12 })
  const left = mesh(new THREE.TorusGeometry(width, .042, 12, 48), lens, [-.43, .35, 1.39])
  const right = mesh(new THREE.TorusGeometry(width, .042, 12, 48), lens, [.43, .35, 1.39])
  if (item.variantCode !== 'ROUND') { left.scale.y = .62; right.scale.y = .62 }
  const bridge = mesh(new THREE.CylinderGeometry(.035, .035, .23, 10), lens, [0, .35, 1.39])
  bridge.rotation.z = Math.PI / 2
  group.add(left, right, bridge)
  return group
}

const resource = item => {
  const group = focusGroup('resource')
  const color = colorOf(item)
  const scale = item.variantCode === 'COMPACT' ? .78 : 1
  const pack = mesh(new THREE.BoxGeometry(.82 * scale, 1.05 * scale, .42),
    material(color, { metalness: .3, roughness: .35 }), [-.98, .05, -.92])
  pack.rotation.y = -.22
  group.add(pack)
  const cells = item.variantCode === 'TEAM' ? 3 : 2
  for (let index = 0; index < cells; index += 1) group.add(mesh(
    new THREE.CylinderGeometry(.1, .1, .68 * scale, 14),
    material(0xeafff7, { emissive: color, emissiveIntensity: .25, transparent: true, opacity: .82 }),
    [-1.24 + index * .25, .05, -.66]))
  return group
}

const growth = item => {
  const group = focusGroup('growth')
  const color = colorOf(item)
  const count = item.variantCode === 'STEPS' ? 5 : item.variantCode === 'RINGS' ? 4 : 3
  for (let index = 0; index < count; index += 1) {
    const ring = mesh(new THREE.TorusGeometry(.55 + index * .23, .025 + index * .006, 10, 64),
      material(color, { emissive: color, emissiveIntensity: .16, transparent: true,
        opacity: .72 - index * .08 }), [0, -1.52 - index * .025, 0])
    ring.rotation.x = Math.PI / 2
    group.add(ring)
  }
  return group
}

const weapon = item => {
  const side = item.slotCode === 'LEFT_HAND_WEAPON' ? 'left' : 'right'
  const preset = accessoryPreset(item.slotCode, item.variantCode, item.colorHex)
  const group = blueprint({ ...item, focusCode: `${side}_hand`, blueprint: preset })
  return group
}

const starGeometry = (radius, depth, points) => {
  const shape = new THREE.Shape()
  const count = Math.max(3, Math.min(12, Math.round(points || 5)))
  for (let index = 0; index < count * 2; index += 1) {
    const angle = -Math.PI / 2 + index * Math.PI / count
    const value = index % 2 === 0 ? radius : radius * .44
    if (index === 0) shape.moveTo(Math.cos(angle) * value, Math.sin(angle) * value)
    else shape.lineTo(Math.cos(angle) * value, Math.sin(angle) * value)
  }
  shape.closePath()
  const geometry = new THREE.ExtrudeGeometry(shape, { depth, bevelEnabled: true,
    bevelSize: Math.min(depth * .22, .025), bevelThickness: Math.min(depth * .18, .02), bevelSegments: 2 })
  geometry.center()
  return geometry
}

const blueprint = item => {
  const group = focusGroup((item.focusCode || '').toLowerCase())
  const parts = item.blueprint?.parts || []
  parts.filter(part => part.visible !== false).slice(0, 15).forEach(part => {
    const parameters = part.parameters || {}
    const radius = parameters.radius || .18
    const factories = {
      TORUS: () => new THREE.TorusGeometry(radius, parameters.tube || .06, 14, 64),
      CONE: () => new THREE.ConeGeometry(radius, parameters.height || .35, 24),
      STAR: () => starGeometry(radius, parameters.depth || .16, parameters.points || 5),
      SPHERE: () => new THREE.SphereGeometry(radius, 28, 20),
      CYLINDER: () => new THREE.CylinderGeometry(radius, radius, parameters.height || .35, 24),
      BOX: () => new THREE.BoxGeometry(parameters.width || .45, parameters.height || .35, parameters.depth || .16),
      OCTAHEDRON: () => new THREE.OctahedronGeometry(radius, 0),
    }
    const geometry = factories[part.shape]?.()
    if (!geometry) return
    const source = part.material || {}
    const color = new THREE.Color(source.color || item.colorHex || '#f37021')
    const object = mesh(geometry, material(color, { metalness: source.metalness ?? .1,
      roughness: source.roughness ?? .45, transparent: (source.opacity ?? 1) < 1,
      opacity: source.opacity ?? 1, emissive: color, emissiveIntensity: source.emissiveIntensity ?? 0 }))
    const transform = { x: 0, y: 0, z: 0, scaleX: 1, scaleY: 1, scaleZ: 1,
      rotationX: 0, rotationY: 0, rotationZ: 0, ...(part.transform || {}) }
    object.position.set(transform.x, transform.y, transform.z)
    object.scale.set(transform.scaleX, transform.scaleY, transform.scaleZ)
    object.rotation.set(THREE.MathUtils.degToRad(transform.rotationX),
      THREE.MathUtils.degToRad(transform.rotationY), THREE.MathUtils.degToRad(transform.rotationZ))
    group.add(object)
  })
  return group
}

const creators = { GOAL_CROWN: goal, INTEREST_ORBIT: interest, SKILL_VISOR: skill,
  RESOURCE_PACK: resource, GROWTH_TRAIL: growth, LEFT_HAND_WEAPON: weapon, RIGHT_HAND_WEAPON: weapon }

export const createEquipmentRoot = (equipment, appearance = {}) => {
  const root = new THREE.Group()
  let interestOrbit = null
  const hands = { leftX: -1.72, leftY: -.12, leftZ: .18, rightX: 1.72, rightY: -.12, rightZ: .18,
    ...(appearance?.handConfig || {}) }
  equipment.filter(item => item.equipped && item.unlocked).forEach(item => {
    const creator = item.blueprint?.parts?.length ? blueprint : creators[item.slotCode]
    if (!creator) return
    const object = creator(item)
    const transform = { scale: 1, offsetX: 0, offsetY: 0, offsetZ: 0,
      rotationX: 0, rotationY: 0, rotationZ: 0, ...(item.transform || {}) }
    object.scale.setScalar(transform.scale)
    object.position.set(transform.offsetX, transform.offsetY, transform.offsetZ)
    if (item.slotCode === 'LEFT_HAND_WEAPON') object.position.add(new THREE.Vector3(hands.leftX, hands.leftY, hands.leftZ + .12))
    if (item.slotCode === 'RIGHT_HAND_WEAPON') object.position.add(new THREE.Vector3(hands.rightX, hands.rightY, hands.rightZ + .12))
    object.rotation.x += THREE.MathUtils.degToRad(transform.rotationX)
    object.rotation.y += THREE.MathUtils.degToRad(transform.rotationY)
    object.rotation.z += THREE.MathUtils.degToRad(transform.rotationZ)
    root.add(object)
    if (item.slotCode === 'INTEREST_ORBIT') interestOrbit = object
  })
  return { root, interestOrbit }
}
