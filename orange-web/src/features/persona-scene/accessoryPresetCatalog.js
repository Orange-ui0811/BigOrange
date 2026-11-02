const clone = value => JSON.parse(JSON.stringify(value))
const transform = (x = 0, y = 0, z = 0, extra = {}) => ({
  x, y, z, scaleX: 1, scaleY: 1, scaleZ: 1,
  rotationX: 0, rotationY: 0, rotationZ: 0, ...extra,
})
const material = (color, extra = {}) => ({ color, opacity: 1, metalness: .35,
  roughness: .34, emissiveIntensity: 0, ...extra })
const part = (id, label, shape, parameters, position, color, materialExtra = {}) => ({
  id, label, shape, visible: true, locked: false, parameters,
  transform: position, material: material(color, materialExtra),
})
const light = '#FFF7ED'
const dark = '#3F2A22'
const orange = '#FFB347'

const goalPreset = (variant, color) => {
  const parts = [part('crown-ring', '罗盘主环', 'TORUS', { radius: .75, tube: .055 }, transform(0, 1.98, 0, { rotationX: 90 }), color, { metalness: .5 })]
  parts.push(part('crown-arrow', '方向指针', 'CONE', { radius: .14, height: .48 }, transform(0, 2.08, .03, { rotationZ: variant === 'BEACON' ? 0 : -14 }), light))
  if (variant === 'ORBIT') parts.push(part('crown-orb', '轨道光点', 'SPHERE', { radius: .11 }, transform(.78, 1.98, 0), color, { emissiveIntensity: .35 }))
  if (variant === 'BEACON') parts.push(part('crown-beacon', '方向信标', 'CYLINDER', { radius: .07, height: .5 }, transform(0, 2.26, 0), orange, { emissiveIntensity: .2 }))
  return parts
}
const interestPreset = (variant, color) => {
  const parts = [part('orbit-ring', '兴趣主轨道', 'TORUS', { radius: 1.8, tube: .022 }, transform(0, .12, 0, { rotationX: 64, rotationY: 14, rotationZ: 10 }), color, { opacity: .82 })]
  const count = variant === 'CONSTELLATION' ? 7 : variant === 'SPARK' ? 5 : 4
  for (let index = 0; index < count; index += 1) {
    const angle = index / count * Math.PI * 2
    parts.push(part(`orbit-star-${index}`, `兴趣星点 ${index + 1}`, 'OCTAHEDRON', { radius: index % 2 ? .095 : .13 },
      transform(Math.cos(angle) * 1.7, Math.sin(angle) * .74, Math.sin(angle) * 1.35), index % 2 ? color : light, { emissiveIntensity: .18 }))
  }
  return parts
}
const skillPreset = (variant, color) => {
  const width = variant === 'WIDE' ? .55 : variant === 'ROUND' ? .38 : .44
  const scaleY = variant === 'ROUND' ? 1 : .62
  return [
    part('visor-left', '左目镜', 'TORUS', { radius: width, tube: .042 }, transform(-.43, .35, 1.39, { scaleY }), color, { opacity: .72, metalness: .55 }),
    part('visor-right', '右目镜', 'TORUS', { radius: width, tube: .042 }, transform(.43, .35, 1.39, { scaleY }), color, { opacity: .72, metalness: .55 }),
    part('visor-bridge', '目镜连接桥', 'CYLINDER', { radius: .035, height: .23 }, transform(0, .35, 1.39, { rotationZ: 90 }), color, { metalness: .55 }),
  ]
}
const resourcePreset = (variant, color) => {
  const scale = variant === 'COMPACT' ? .78 : 1
  const parts = [part('pack-shell', '能量包外壳', 'BOX', { width: .82 * scale, height: 1.05 * scale, depth: .42 }, transform(-.98, .05, -.92, { rotationY: -13 }), color)]
  const cells = variant === 'TEAM' ? 3 : 2
  for (let index = 0; index < cells; index += 1) parts.push(part(`pack-cell-${index}`, `能量单元 ${index + 1}`, 'CYLINDER', { radius: .1, height: .68 * scale }, transform(-1.24 + index * .25, .05, -.66), light, { opacity: .82, emissiveIntensity: .25 }))
  return parts
}
const growthPreset = (variant, color) => {
  const count = variant === 'STEPS' ? 5 : variant === 'RINGS' ? 4 : 3
  return Array.from({ length: count }, (_, index) => part(`trail-ring-${index}`, `成长轨迹 ${index + 1}`, 'TORUS',
    { radius: .55 + index * .23, tube: .025 + index * .006 }, transform(0, -1.52 - index * .025, 0, { rotationX: 90 }),
    color, { opacity: .72 - index * .08, emissiveIntensity: .16 }))
}
const swordPreset = color => [
  part('sword-pommel', '剑首', 'OCTAHEDRON', { radius: .11 }, transform(0, -.12, .02), orange, { metalness: .62 }),
  part('sword-grip', '缠绕剑柄', 'CYLINDER', { radius: .075, height: .42 }, transform(0, .08, 0), dark, { roughness: .62 }),
  part('sword-guard', '护手横格', 'BOX', { width: .54, height: .075, depth: .14 }, transform(0, .31, 0), orange, { metalness: .58 }),
  part('sword-gem', '护手宝石', 'OCTAHEDRON', { radius: .09 }, transform(0, .32, .12), color, { emissiveIntensity: .25 }),
  part('sword-blade', '剑身', 'BOX', { width: .15, height: .9, depth: .075 }, transform(0, .79, .02), color, { metalness: .82, roughness: .16 }),
  part('sword-ridge', '剑脊', 'BOX', { width: .035, height: .82, depth: .09 }, transform(0, .78, .08), light, { metalness: .8 }),
  part('sword-tip', '剑尖', 'CONE', { radius: .125, height: .3 }, transform(0, 1.38, .02, { rotationY: 45 }), color, { metalness: .82, roughness: .16 }),
]
const knifePreset = color => [
  part('knife-pommel', '刀首', 'SPHERE', { radius: .095 }, transform(0, -.1, 0), orange),
  part('knife-grip', '防滑刀柄', 'CYLINDER', { radius: .08, height: .36 }, transform(0, .08, 0), dark, { roughness: .68 }),
  part('knife-guard', '短护手', 'BOX', { width: .36, height: .07, depth: .13 }, transform(0, .28, 0), orange),
  part('knife-blade', '短刀刃', 'BOX', { width: .19, height: .5, depth: .07 }, transform(.04, .55, .02, { rotationZ: -7 }), color, { metalness: .84, roughness: .14 }),
  part('knife-tip', '刀尖', 'CONE', { radius: .14, height: .27 }, transform(-.005, .91, .02, { rotationY: 45, rotationZ: -7 }), color, { metalness: .84, roughness: .14 }),
]
const shieldPreset = color => [
  part('shield-core', '盾牌主体', 'CYLINDER', { radius: .58, height: .13 }, transform(0, .2, .08, { rotationX: 90 }), color, { metalness: .55 }),
  part('shield-rim', '盾牌包边', 'TORUS', { radius: .5, tube: .055 }, transform(0, .2, .16), orange, { metalness: .68 }),
  part('shield-boss', '盾牌中心', 'OCTAHEDRON', { radius: .17 }, transform(0, .2, .23), light, { metalness: .7 }),
  ...[[-.3,.48],[.3,.48],[-.3,-.08],[.3,-.08]].map(([x,y], index) => part(`shield-rivet-${index}`, `盾钉 ${index + 1}`, 'SPHERE', { radius: .055 }, transform(x, y, .2), orange, { metalness: .72 })),
]
const blasterPreset = color => [
  part('blaster-body', '能量枪机身', 'BOX', { width: .7, height: .29, depth: .3 }, transform(.05, .25, .02), color, { metalness: .62 }),
  part('blaster-shell', '机身上盖', 'BOX', { width: .42, height: .1, depth: .34 }, transform(-.03, .43, .02), light, { metalness: .48 }),
  part('blaster-barrel', '能量枪管', 'CYLINDER', { radius: .1, height: .56 }, transform(-.42, .27, .02, { rotationZ: 90 }), dark, { metalness: .7 }),
  part('blaster-muzzle', '枪口稳定环', 'TORUS', { radius: .14, tube: .035 }, transform(-.72, .27, .02, { rotationY: 90 }), orange, { emissiveIntensity: .24 }),
  part('blaster-grip', '握把', 'BOX', { width: .19, height: .42, depth: .2 }, transform(.15, -.06, .02, { rotationZ: -12 }), dark, { roughness: .64 }),
  part('blaster-cell', '能量核心', 'CYLINDER', { radius: .09, height: .3 }, transform(.31, .27, .2, { rotationZ: 90 }), orange, { opacity: .88, emissiveIntensity: .38 }),
  part('blaster-sight', '瞄准器', 'TORUS', { radius: .1, tube: .025 }, transform(-.05, .54, .03), color, { emissiveIntensity: .2 }),
]

export const accessoryVariantLabels = { CLASSIC: '经典', ORBIT: '环航', BEACON: '信标', SPARK: '火花', CONSTELLATION: '星座', ROUND: '圆框', WIDE: '宽屏', COMPACT: '轻量', TEAM: '协作', RINGS: '年轮', STEPS: '阶梯', SWORD: '剑', KNIFE: '刀', SHIELD: '盾牌', BLASTER: '能量枪' }
export const variantsForSlot = slot => ({
  GOAL_CROWN: ['CLASSIC', 'ORBIT', 'BEACON'], INTEREST_ORBIT: ['CLASSIC', 'SPARK', 'CONSTELLATION'],
  SKILL_VISOR: ['CLASSIC', 'ROUND', 'WIDE'], RESOURCE_PACK: ['CLASSIC', 'COMPACT', 'TEAM'],
  GROWTH_TRAIL: ['CLASSIC', 'RINGS', 'STEPS'], LEFT_HAND_WEAPON: ['SWORD', 'KNIFE', 'SHIELD', 'BLASTER'],
  RIGHT_HAND_WEAPON: ['SWORD', 'KNIFE', 'SHIELD', 'BLASTER'],
}[slot] || [])
export const accessoryPreset = (slot, variant, color = '#F37021') => {
  const factories = { GOAL_CROWN: goalPreset, INTEREST_ORBIT: interestPreset, SKILL_VISOR: skillPreset,
    RESOURCE_PACK: resourcePreset, GROWTH_TRAIL: growthPreset }
  let parts
  if (slot.endsWith('_HAND_WEAPON')) parts = ({ SWORD: swordPreset, KNIFE: knifePreset,
    SHIELD: shieldPreset, BLASTER: blasterPreset }[variant] || swordPreset)(color)
  else parts = factories[slot]?.(variant, color) || []
  return clone({ name: `我的${accessoryVariantLabels[variant] || variant}`, anchor: slot, parts })
}
