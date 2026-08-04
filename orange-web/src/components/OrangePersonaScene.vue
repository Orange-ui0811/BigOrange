<template>
  <div ref="host" class="orange-scene" :class="{ unavailable }">
    <div v-if="!unavailable" class="scene-zoom" aria-label="3D 视图缩放控制">
      <button type="button" aria-label="放大橙子" title="放大" @click="adjustZoom(-0.12)">＋</button>
      <span aria-live="polite">{{ zoomPercent }}%</span>
      <button type="button" aria-label="缩小橙子" title="缩小" @click="adjustZoom(0.12)">−</button>
      <button type="button" class="reset-zoom" aria-label="重置缩放" title="重置缩放" @click="resetZoom">复位</button>
      <button type="button" class="reset-zoom" aria-label="查看橙子全身" title="查看全身" @click="showFullBody">全身</button>
    </div>
    <div v-if="unavailable" class="scene-fallback" role="status">
      <PersonaOrangeBadge :equipment="equipment" />
      <strong>3D 预览暂不可用</strong>
      <span>画像编辑与装备设置仍可正常使用</span>
    </div>
    <div v-else class="scene-status" aria-live="polite">{{ ready ? '' : '正在唤醒橙子…' }}</div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as THREE from 'three'
import PersonaOrangeBadge from './PersonaOrangeBadge.vue'

const props = defineProps({
  equipment: { type: Array, default: () => [] },
  activeFocus: { type: String, default: 'overview' },
  appearance: { type: Object, default: () => ({}) },
})
const emit = defineEmits(['ready', 'reset-view'])

const host = ref(null)
const ready = ref(false)
const unavailable = ref(false)
const focusPositions = {
  overview: { camera: [0, -.5, 11.5], target: [0, -.7, 0] },
  goal: { camera: [0, 1.35, 7.5], target: [0, .55, 0] },
  interest: { camera: [4.55, 1.2, 4.55], target: [0, .55, 0] },
  skill: { camera: [0, .65, 4.6], target: [0, .3, .05] },
  resource: { camera: [-4.55, .55, 4.35], target: [-.15, .05, -.1] },
  growth: { camera: [0, -1.05, 5.65], target: [0, -.8, 0] },
  recommendation: { camera: [4.25, -.3, 5.1], target: [0, -.25, 0] },
}

let scene
let camera
let renderer
let root
let equipmentRoot
let interestOrbit
let eyeLeft
let eyeRight
let eyeTargetX = 0
let eyeTargetY = 0
let resizeObserver
let targetCamera = new THREE.Vector3(...focusPositions.overview.camera)
let targetLook = new THREE.Vector3(...focusPositions.overview.target)
let currentLook = targetLook.clone()
let pointer = new THREE.Vector2()
let dragging = false
let previousX = 0
let dragRotation = 0
let reduceMotion = false
const zoomScale = ref(1)
const zoomPercent = computed(() => Math.round(100 / zoomScale.value))

const material = (color, options = {}) => new THREE.MeshStandardMaterial({
  color,
  roughness: options.roughness ?? .55,
  metalness: options.metalness ?? .05,
  transparent: options.transparent ?? false,
  opacity: options.opacity ?? 1,
  emissive: options.emissive || 0x000000,
  emissiveIntensity: options.emissiveIntensity || 0,
})

const mesh = (geometry, meshMaterial, position = [0, 0, 0]) => {
  const value = new THREE.Mesh(geometry, meshMaterial)
  value.position.set(...position)
  value.castShadow = true
  value.receiveShadow = true
  return value
}

const focusGroup = code => {
  const group = new THREE.Group()
  group.userData.focusCode = code
  return group
}

const createOrange = () => {
  const group = new THREE.Group()
  const appearance = props.appearance || {}
  const face = {
    bodyWidth: 1, bodyHeight: 1, eyeSize: 1, eyeSpacing: 1, eyeHeight: 0, pupilSize: 1,
    browLength: 1, browThickness: 1, browHeight: 0, browAngle: 0,
    mouthWidth: 1, mouthCurve: 1, mouthHeight: 0, mouthThickness: 1, mouthOpen: 0,
    ...(appearance.faceConfig || {}),
  }
  const surfaceStyle = appearance.surfaceStyle || 'NATURAL'
  const geometry = new THREE.SphereGeometry(1.46, 72, 56)
  const position = geometry.attributes.position
  const vertex = new THREE.Vector3()
  for (let index = 0; index < position.count; index += 1) {
    vertex.fromBufferAttribute(position, index)
    const radius = vertex.length()
    const rippleScale = surfaceStyle === 'GLOSSY' ? .25 : surfaceStyle === 'MATTE' ? 1.25 : 1
    const ripple = (Math.sin(vertex.y * 11 + vertex.x * 4) * .013 + Math.sin(vertex.z * 17) * .008) * rippleScale
    vertex.normalize().multiplyScalar(radius + ripple)
    position.setXYZ(index, vertex.x * face.bodyWidth, vertex.y * .96 * face.bodyHeight, vertex.z)
  }
  geometry.computeVertexNormals()
  const body = mesh(geometry, material(appearance.bodyColor || '#f37021', {
    roughness: surfaceStyle === 'GLOSSY' ? .18 : surfaceStyle === 'MATTE' ? .94 : .72,
    metalness: surfaceStyle === 'GLOSSY' ? .12 : .03,
  }))
  body.name = 'original-orange-body'
  group.add(body)

  const dimple = mesh(new THREE.TorusGeometry(.28, .035, 12, 48), material(0xc94b0b), [0, 1.37 * face.bodyHeight, 0])
  dimple.rotation.x = Math.PI / 2
  group.add(dimple)
  const stem = mesh(new THREE.CylinderGeometry(.08, .12, .48, 14), material(0x5f3818, { roughness: .9 }), [.02, 1.64 * face.bodyHeight, -.02])
  stem.rotation.z = -.12
  group.add(stem)
  const leafStyle = appearance.leafStyle || 'CLASSIC'
  const leafSpecs = leafStyle === 'DOUBLE'
    ? [[[.34, 1.72, -.02], -.35, 1], [[-.27, 1.76, -.04], .5, .82]]
    : leafStyle === 'SPROUT'
      ? [[[.2, 1.83, -.04], -.12, .72], [[-.18, 1.84, -.04], .18, .72]]
      : [[[.34, 1.72, -.02], -.35, 1]]
  leafSpecs.forEach(([leafPosition, rotation, scale]) => {
    const adjustedPosition = [leafPosition[0], leafPosition[1] * face.bodyHeight, leafPosition[2]]
    const leaf = mesh(new THREE.SphereGeometry(.42, 24, 14), material(0x65a30d, { roughness: .75 }), adjustedPosition)
    leaf.scale.set(scale, .18 * scale, .48 * scale)
    leaf.rotation.z = rotation
    group.add(leaf)
  })

  const eyeMaterial = material(0x3f1d0b, { roughness: .38 })
  const scleraMaterial = material(0xfff8eb, { roughness: .28 })
  const shineMaterial = new THREE.MeshBasicMaterial({ color: 0xffffff })
  const createEye = position => {
    const eye = new THREE.Group()
    eye.position.set(...position)
    eye.add(mesh(new THREE.SphereGeometry(.15 * face.eyeSize, 24, 18), scleraMaterial))
    const pupil = mesh(new THREE.SphereGeometry(.082 * face.eyeSize * face.pupilSize, 20, 16), eyeMaterial, [0, 0, .122 * face.eyeSize])
    pupil.add(mesh(new THREE.SphereGeometry(.022, 10, 8), shineMaterial, [-.024, .026, .067]))
    eye.add(pupil)
    return eye
  }
  const eyeX = .42 * face.eyeSpacing * face.bodyWidth
  const eyeY = .34 * face.bodyHeight + face.eyeHeight
  eyeLeft = createEye([-eyeX, eyeY, 1.3])
  eyeRight = createEye([eyeX, eyeY, 1.3])
  group.add(eyeLeft, eyeRight)

  const faceStyle = appearance.faceStyle || 'FRIENDLY'
  const mouthY = -.12 * face.bodyHeight + face.mouthHeight
  if (face.mouthOpen > .08 || faceStyle === 'CURIOUS') {
    const openAmount = Math.max(face.mouthOpen, faceStyle === 'CURIOUS' ? .28 : 0)
    const mouth = mesh(new THREE.TorusGeometry(.12, .027 * face.mouthThickness, 10, 36), eyeMaterial, [0, mouthY - .08, 1.43])
    mouth.scale.set(face.mouthWidth, .55 + openAmount * 1.8, 1)
    group.add(mouth)
  } else {
    const smileCurve = new THREE.QuadraticBezierCurve3(
      new THREE.Vector3(-.27 * face.mouthWidth, mouthY, 1.42),
      new THREE.Vector3(0, mouthY - .22 * face.mouthCurve, 1.48),
      new THREE.Vector3(.27 * face.mouthWidth, mouthY, 1.42),
    )
    group.add(mesh(new THREE.TubeGeometry(smileCurve, 28, .026 * face.mouthThickness, 8, false), eyeMaterial))
  }
  const browY = .62 * face.bodyHeight + face.browHeight
  const browBaseAngle = faceStyle === 'COOL' ? -.16 : faceStyle === 'CURIOUS' ? .08 : 0
  const leftBrow = mesh(new THREE.BoxGeometry(.3 * face.browLength, .035 * face.browThickness, .035), eyeMaterial, [-eyeX, browY, 1.36])
  const rightBrow = mesh(new THREE.BoxGeometry(.3 * face.browLength, .035 * face.browThickness, .035), eyeMaterial, [eyeX, browY, 1.36])
  leftBrow.rotation.z = browBaseAngle + face.browAngle
  rightBrow.rotation.z = -browBaseAngle - face.browAngle
  group.add(leftBrow, rightBrow)
  return group
}

const colorOf = item => new THREE.Color(item.colorHex || '#f37021')

const createGoalEquipment = item => {
  const group = focusGroup('goal')
  const color = colorOf(item)
  const ring = mesh(new THREE.TorusGeometry(.75, .055, 16, 72), material(color, { metalness: .5, roughness: .25, emissive: color, emissiveIntensity: .12 }), [0, 1.98, 0])
  ring.rotation.x = Math.PI / 2
  group.add(ring)
  const arrow = mesh(new THREE.ConeGeometry(.14, .48, 4), material(0xfff7ed, { metalness: .35 }), [0, 2.08, .03])
  arrow.rotation.z = item.variantCode === 'BEACON' ? 0 : -.25
  group.add(arrow)
  if (item.variantCode === 'ORBIT') {
    const satellite = mesh(new THREE.SphereGeometry(.11, 16, 12), material(color, { emissive: color, emissiveIntensity: .35 }), [.78, 1.98, 0])
    group.add(satellite)
  }
  return group
}

const createInterestEquipment = item => {
  const group = focusGroup('interest')
  const color = colorOf(item)
  const ring = mesh(new THREE.TorusGeometry(1.84, .022, 10, 96), material(color, { metalness: .45, roughness: .3, transparent: true, opacity: .82 }))
  ring.rotation.set(1.12, .25, .18)
  group.add(ring)
  const count = item.variantCode === 'CONSTELLATION' ? 7 : item.variantCode === 'SPARK' ? 5 : 4
  for (let index = 0; index < count; index += 1) {
    const angle = index / count * Math.PI * 2
    const bead = mesh(new THREE.OctahedronGeometry(index % 2 ? .095 : .13, 0), material(index % 2 ? color : 0xfff7ed, { emissive: color, emissiveIntensity: .2 }), [Math.cos(angle) * 1.84, Math.sin(angle) * .82, Math.sin(angle) * 1.48])
    group.add(bead)
  }
  interestOrbit = group
  return group
}

const createSkillEquipment = item => {
  const group = focusGroup('skill')
  const color = colorOf(item)
  const width = item.variantCode === 'WIDE' ? .55 : item.variantCode === 'ROUND' ? .38 : .44
  const lensMaterial = material(color, { metalness: .55, roughness: .18, transparent: true, opacity: .72, emissive: color, emissiveIntensity: .12 })
  const left = mesh(new THREE.TorusGeometry(width, .042, 12, 48), lensMaterial, [-.43, .35, 1.39])
  const right = mesh(new THREE.TorusGeometry(width, .042, 12, 48), lensMaterial, [.43, .35, 1.39])
  if (item.variantCode !== 'ROUND') { left.scale.y = .62; right.scale.y = .62 }
  const bridge = mesh(new THREE.CylinderGeometry(.035, .035, .23, 10), lensMaterial, [0, .35, 1.39])
  bridge.rotation.z = Math.PI / 2
  group.add(left, right, bridge)
  return group
}

const createResourceEquipment = item => {
  const group = focusGroup('resource')
  const color = colorOf(item)
  const shell = material(color, { metalness: .3, roughness: .35 })
  const scale = item.variantCode === 'COMPACT' ? .78 : 1
  const pack = mesh(new THREE.BoxGeometry(.82 * scale, 1.05 * scale, .42), shell, [-.98, .05, -.92])
  pack.rotation.y = -.22
  group.add(pack)
  const cells = item.variantCode === 'TEAM' ? 3 : 2
  for (let index = 0; index < cells; index += 1) {
    const cell = mesh(new THREE.CylinderGeometry(.1, .1, .68 * scale, 14), material(0xeafff7, { emissive: color, emissiveIntensity: .25, transparent: true, opacity: .82 }), [-1.24 + index * .25, .05, -.66])
    group.add(cell)
  }
  return group
}

const createGrowthEquipment = item => {
  const group = focusGroup('growth')
  const color = colorOf(item)
  const count = item.variantCode === 'STEPS' ? 5 : item.variantCode === 'RINGS' ? 4 : 3
  for (let index = 0; index < count; index += 1) {
    const ring = mesh(new THREE.TorusGeometry(.55 + index * .23, .025 + index * .006, 10, 64), material(color, { emissive: color, emissiveIntensity: .16, transparent: true, opacity: .72 - index * .08 }), [0, -1.52 - index * .025, 0])
    ring.rotation.x = Math.PI / 2
    group.add(ring)
  }
  return group
}

const createStarGeometry = (radius, depth, points) => {
  const shape = new THREE.Shape()
  const count = Math.max(3, Math.min(12, Math.round(points || 5)))
  for (let index = 0; index < count * 2; index += 1) {
    const angle = -Math.PI / 2 + index * Math.PI / count
    const value = index % 2 === 0 ? radius : radius * .44
    const x = Math.cos(angle) * value
    const y = Math.sin(angle) * value
    if (index === 0) shape.moveTo(x, y)
    else shape.lineTo(x, y)
  }
  shape.closePath()
  const geometry = new THREE.ExtrudeGeometry(shape, { depth, bevelEnabled: true, bevelSize: Math.min(depth * .22, .025), bevelThickness: Math.min(depth * .18, .02), bevelSegments: 2 })
  geometry.center()
  return geometry
}

const createBlueprintEquipment = item => {
  const group = focusGroup((item.focusCode || '').toLowerCase())
  const parts = item.blueprint?.parts || []
  parts.filter(part => part.visible !== false).slice(0, 15).forEach(part => {
    const parameters = part.parameters || {}
    const radius = parameters.radius || .18
    const width = parameters.width || .45
    const height = parameters.height || .35
    const depth = parameters.depth || .16
    const geometries = {
      TORUS: () => new THREE.TorusGeometry(radius, parameters.tube || .06, 14, 64),
      CONE: () => new THREE.ConeGeometry(radius, height, 24),
      STAR: () => createStarGeometry(radius, depth, parameters.points || 5),
      SPHERE: () => new THREE.SphereGeometry(radius, 28, 20),
      CYLINDER: () => new THREE.CylinderGeometry(radius, radius, height, 24),
      BOX: () => new THREE.BoxGeometry(width, height, depth),
      OCTAHEDRON: () => new THREE.OctahedronGeometry(radius, 0),
    }
    const geometry = geometries[part.shape]?.()
    if (!geometry) return
    const sourceMaterial = part.material || {}
    const color = new THREE.Color(sourceMaterial.color || item.colorHex || '#f37021')
    const partMaterial = material(color, {
      metalness: sourceMaterial.metalness ?? .1,
      roughness: sourceMaterial.roughness ?? .45,
      transparent: (sourceMaterial.opacity ?? 1) < 1,
      opacity: sourceMaterial.opacity ?? 1,
      emissive: color,
      emissiveIntensity: sourceMaterial.emissiveIntensity ?? 0,
    })
    const object = mesh(geometry, partMaterial)
    const transform = { x: 0, y: 0, z: 0, scaleX: 1, scaleY: 1, scaleZ: 1, rotationX: 0, rotationY: 0, rotationZ: 0, ...(part.transform || {}) }
    object.position.set(transform.x, transform.y, transform.z)
    object.scale.set(transform.scaleX, transform.scaleY, transform.scaleZ)
    object.rotation.set(
      THREE.MathUtils.degToRad(transform.rotationX),
      THREE.MathUtils.degToRad(transform.rotationY),
      THREE.MathUtils.degToRad(transform.rotationZ),
    )
    group.add(object)
  })
  return group
}

const disposeGroup = group => {
  group?.traverse(object => {
    object.geometry?.dispose?.()
    if (Array.isArray(object.material)) object.material.forEach(value => value.dispose?.())
    else object.material?.dispose?.()
  })
}

const rebuildEquipment = () => {
  if (!root) return
  if (equipmentRoot) {
    root.remove(equipmentRoot)
    disposeGroup(equipmentRoot)
  }
  equipmentRoot = new THREE.Group()
  interestOrbit = null
  props.equipment.filter(item => item.equipped && item.unlocked).forEach(item => {
    const creators = {
      GOAL_CROWN: createGoalEquipment,
      INTEREST_ORBIT: createInterestEquipment,
      SKILL_VISOR: createSkillEquipment,
      RESOURCE_PACK: createResourceEquipment,
      GROWTH_TRAIL: createGrowthEquipment,
    }
    const creator = item.blueprint?.parts?.length ? createBlueprintEquipment : creators[item.slotCode]
    if (creator) {
      const equipment = creator(item)
      const transform = { scale: 1, offsetX: 0, offsetY: 0, offsetZ: 0, rotationX: 0, rotationY: 0, rotationZ: 0, ...(item.transform || {}) }
      equipment.scale.setScalar(transform.scale)
      equipment.position.set(transform.offsetX, transform.offsetY, transform.offsetZ)
      equipment.rotation.x += THREE.MathUtils.degToRad(transform.rotationX)
      equipment.rotation.y += THREE.MathUtils.degToRad(transform.rotationY)
      equipment.rotation.z += THREE.MathUtils.degToRad(transform.rotationZ)
      equipmentRoot.add(equipment)
    }
  })
  root.add(equipmentRoot)
}

const rebuildOrange = () => {
  if (!scene) return
  if (root) {
    scene.remove(root)
    disposeGroup(root)
  }
  root = createOrange()
  scene.add(root)
  rebuildEquipment()
}

const setFocus = code => {
  const focus = focusPositions[code] || focusPositions.overview
  const look = new THREE.Vector3(...focus.target)
  const baseCamera = new THREE.Vector3(...focus.camera)
  targetLook.copy(look)
  targetCamera.copy(look).add(baseCamera.sub(look).multiplyScalar(zoomScale.value))
}

const adjustZoom = delta => {
  zoomScale.value = THREE.MathUtils.clamp(zoomScale.value + delta, .68, 1.48)
  setFocus(props.activeFocus)
}
const resetZoom = () => { zoomScale.value = 1; setFocus(props.activeFocus) }
const showFullBody = () => { zoomScale.value = 1; setFocus('overview'); emit('reset-view') }

const setPointer = event => {
  if (!renderer) return
  const bounds = renderer.domElement.getBoundingClientRect()
  pointer.x = ((event.clientX - bounds.left) / bounds.width) * 2 - 1
  pointer.y = -((event.clientY - bounds.top) / bounds.height) * 2 + 1
}

const onPointerDown = event => { dragging = true; previousX = event.clientX; renderer?.domElement.setPointerCapture?.(event.pointerId) }
const onPointerMove = event => {
  setPointer(event)
  eyeTargetX = -THREE.MathUtils.clamp(pointer.y * .34, -.34, .34)
  eyeTargetY = THREE.MathUtils.clamp(pointer.x * .42, -.42, .42)
  if (dragging) {
    dragRotation += (event.clientX - previousX) * .008
    previousX = event.clientX
  }
}
const onPointerUp = event => { dragging = false; renderer?.domElement.releasePointerCapture?.(event.pointerId) }
const onPointerLeave = () => {
  if (!dragging) {
    eyeTargetX = 0
    eyeTargetY = 0
  }
}
const resize = () => {
  if (!host.value || !renderer || !camera) return
  const width = Math.max(1, host.value.clientWidth)
  const height = Math.max(1, host.value.clientHeight)
  renderer.setSize(width, height, false)
  camera.aspect = width / height
  camera.updateProjectionMatrix()
}

const animate = time => {
  const ease = reduceMotion ? 1 : .065
  camera.position.lerp(targetCamera, ease)
  currentLook.lerp(targetLook, ease)
  camera.lookAt(currentLook)
  if (root) {
    root.rotation.y += (dragRotation - root.rotation.y) * .08
    if (!reduceMotion && !dragging) root.position.y = Math.sin(time * .0012) * .035
  }
  if (!reduceMotion && interestOrbit) interestOrbit.rotation.y = time * .00032
  if (eyeLeft && eyeRight) {
    const eyeEase = reduceMotion ? 1 : .18
    eyeLeft.rotation.x += (eyeTargetX - eyeLeft.rotation.x) * eyeEase
    eyeLeft.rotation.y += (eyeTargetY - eyeLeft.rotation.y) * eyeEase
    eyeRight.rotation.x += (eyeTargetX - eyeRight.rotation.x) * eyeEase
    eyeRight.rotation.y += (eyeTargetY - eyeRight.rotation.y) * eyeEase
  }
  renderer.render(scene, camera)
}

onMounted(() => {
  try {
    reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    scene = new THREE.Scene()
    scene.background = new THREE.Color(0xfffbf5)
    camera = new THREE.PerspectiveCamera(34, 1, .1, 100)
    camera.position.copy(targetCamera)
    renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false, powerPreference: 'high-performance' })
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.75))
    renderer.shadowMap.enabled = true
    renderer.shadowMap.type = THREE.PCFSoftShadowMap
    renderer.outputColorSpace = THREE.SRGBColorSpace
    renderer.toneMapping = THREE.ACESFilmicToneMapping
    renderer.toneMappingExposure = 1.08
    renderer.domElement.setAttribute('aria-label', '可拖动旋转视角的原创 3D 橙子画像，所有外观编辑均在右侧面板完成')
    renderer.domElement.setAttribute('role', 'img')
    renderer.domElement.tabIndex = 0
    host.value.appendChild(renderer.domElement)

    scene.add(new THREE.HemisphereLight(0xfff4df, 0x4f6b5e, 2.2))
    const key = new THREE.DirectionalLight(0xffffff, 3.1)
    key.position.set(4, 6, 5)
    key.castShadow = true
    key.shadow.mapSize.set(1024, 1024)
    scene.add(key)
    const rim = new THREE.PointLight(0xff7a1a, 18, 9)
    rim.position.set(-3.5, 1.2, 2.8)
    scene.add(rim)

    const floor = mesh(new THREE.CylinderGeometry(2.7, 3.05, .18, 72), material(0xffead5, { roughness: .95 }), [0, -1.68, 0])
    floor.receiveShadow = true
    scene.add(floor)
    const floorRing = mesh(new THREE.TorusGeometry(2.16, .025, 10, 96), material(0xf37021, { emissive: 0xf37021, emissiveIntensity: .2 }), [0, -1.57, 0])
    floorRing.rotation.x = Math.PI / 2
    scene.add(floorRing)

    root = createOrange()
    scene.add(root)
    rebuildEquipment()
    setFocus(props.activeFocus)
    resizeObserver = new ResizeObserver(resize)
    resizeObserver.observe(host.value)
    resize()

    renderer.domElement.addEventListener('pointerdown', onPointerDown)
    renderer.domElement.addEventListener('pointermove', onPointerMove)
    renderer.domElement.addEventListener('pointerup', onPointerUp)
    renderer.domElement.addEventListener('pointercancel', onPointerUp)
    renderer.domElement.addEventListener('pointerleave', onPointerLeave)
    renderer.domElement.addEventListener('webglcontextlost', event => { event.preventDefault(); unavailable.value = true })
    renderer.setAnimationLoop(animate)
    ready.value = true
    emit('ready')
  } catch (error) {
    console.warn('Persona 3D scene unavailable', error)
    unavailable.value = true
  }
})

watch(() => props.activeFocus, setFocus)
watch(() => props.equipment, rebuildEquipment, { deep: true })
watch(() => props.appearance, rebuildOrange, { deep: true })

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  if (renderer) {
    renderer.setAnimationLoop(null)
    renderer.domElement.removeEventListener('pointerdown', onPointerDown)
    renderer.domElement.removeEventListener('pointermove', onPointerMove)
    renderer.domElement.removeEventListener('pointerup', onPointerUp)
    renderer.domElement.removeEventListener('pointercancel', onPointerUp)
    renderer.domElement.removeEventListener('pointerleave', onPointerLeave)
  }
  disposeGroup(root)
  scene?.traverse(object => {
    if (object !== root) {
      object.geometry?.dispose?.()
      object.material?.dispose?.()
    }
  })
  renderer?.dispose()
  renderer?.domElement.remove()
})
</script>

<style scoped>
.orange-scene{position:relative;width:100%;height:100%;min-height:560px;overflow:hidden;border-radius:26px;background:#fffbf5}.orange-scene :deep(canvas){display:block;width:100%;height:100%;cursor:grab;outline:none}.orange-scene :deep(canvas:active){cursor:grabbing}.orange-scene :deep(canvas:focus-visible){box-shadow:inset 0 0 0 3px rgba(243,112,33,.55)}.scene-zoom{position:absolute;z-index:4;top:20px;right:20px;display:flex;align-items:center;gap:4px;padding:5px;border:1px solid rgba(234,216,201,.95);border-radius:12px;background:rgba(255,255,255,.88);box-shadow:0 8px 26px rgba(87,45,20,.1);backdrop-filter:blur(12px)}.scene-zoom button{display:grid;width:34px;height:34px;place-items:center;border:0;border-radius:8px;background:#fff7ed;color:#9a3412;font:900 18px/1 var(--font-mono);cursor:pointer}.scene-zoom button:hover,.scene-zoom button:focus-visible{background:#ffedd5;outline:2px solid rgba(243,112,33,.3)}.scene-zoom span{min-width:44px;color:#7c2d12;font:800 10px/1 var(--font-mono);text-align:center}.scene-zoom .reset-zoom{width:auto;padding:0 9px;font-size:10px}.scene-status{position:absolute;inset:0;display:grid;place-items:center;color:#9a3412;font:800 12px/1.4 var(--font-mono);pointer-events:none}.scene-fallback{display:grid;height:100%;place-content:center;justify-items:center;gap:9px;color:var(--text-secondary);text-align:center}.scene-fallback strong{margin-top:8px;color:var(--text-primary)}.scene-fallback span{font-size:12px}
@media(max-width:1100px){.orange-scene{min-height:500px}}
</style>
