import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as THREE from 'three'
import { createOrange } from './orangeFactory'
import { createEquipmentRoot } from './equipmentFactory'
import { disposeGroup, material, mesh } from './primitives'

const focusPositions = {
  overview: { camera: [0, -.5, 11.5], target: [0, -.7, 0] },
  goal: { camera: [0, 1.35, 7.5], target: [0, .55, 0] },
  interest: { camera: [4.55, 1.2, 4.55], target: [0, .55, 0] },
  skill: { camera: [0, .65, 4.6], target: [0, .3, .05] },
  resource: { camera: [-4.55, .55, 4.35], target: [-.15, .05, -.1] },
  growth: { camera: [0, -1.05, 5.65], target: [0, -.8, 0] },
  left_hand: { camera: [-3.7, .1, 5.1], target: [-1.25, -.1, .1] },
  right_hand: { camera: [3.7, .1, 5.1], target: [1.25, -.1, .1] },
  recommendation: { camera: [4.25, -.3, 5.1], target: [0, -.25, 0] },
}

export const usePersonaScene = (props, emit) => {
  const host = ref(null)
  const ready = ref(false)
  const unavailable = ref(false)
  const zoomScale = ref(1)
  const zoomPercent = computed(() => Math.round(100 / zoomScale.value))
  let scene, camera, renderer, orangeRoot, equipmentRoot, interestOrbit, eyes = []
  let resizeObserver, reduceMotion = false, dragging = false, previousX = 0, dragRotation = 0
  let eyeTargetX = 0, eyeTargetY = 0
  const pointer = new THREE.Vector2()
  const targetCamera = new THREE.Vector3(...focusPositions.overview.camera)
  const targetLook = new THREE.Vector3(...focusPositions.overview.target)
  const currentLook = targetLook.clone()

  const setFocus = code => {
    const focus = focusPositions[code] || focusPositions.overview
    const look = new THREE.Vector3(...focus.target)
    const position = new THREE.Vector3(...focus.camera)
    targetLook.copy(look)
    targetCamera.copy(look).add(position.sub(look).multiplyScalar(zoomScale.value))
  }
  const adjustZoom = delta => {
    zoomScale.value = THREE.MathUtils.clamp(zoomScale.value + delta, .68, 1.48)
    setFocus(props.activeFocus)
  }
  const resetZoom = () => { zoomScale.value = 1; setFocus(props.activeFocus) }
  const showFullBody = () => { zoomScale.value = 1; setFocus('overview'); emit('reset-view') }

  const rebuildEquipment = () => {
    if (!orangeRoot) return
    if (equipmentRoot) { orangeRoot.remove(equipmentRoot); disposeGroup(equipmentRoot) }
    const result = createEquipmentRoot(props.equipment, props.appearance)
    equipmentRoot = result.root
    interestOrbit = result.interestOrbit
    orangeRoot.add(equipmentRoot)
  }
  const rebuildOrange = () => {
    if (!scene) return
    if (orangeRoot) { scene.remove(orangeRoot); disposeGroup(orangeRoot) }
    const result = createOrange(props.appearance || {})
    orangeRoot = result.group
    eyes = result.eyes
    scene.add(orangeRoot)
    equipmentRoot = null
    rebuildEquipment()
  }

  const setPointer = event => {
    if (!renderer) return
    const bounds = renderer.domElement.getBoundingClientRect()
    pointer.x = ((event.clientX - bounds.left) / bounds.width) * 2 - 1
    pointer.y = -((event.clientY - bounds.top) / bounds.height) * 2 + 1
  }
  const onPointerDown = event => {
    dragging = true
    previousX = event.clientX
    renderer?.domElement.setPointerCapture?.(event.pointerId)
  }
  const onPointerMove = event => {
    setPointer(event)
    eyeTargetX = -THREE.MathUtils.clamp(pointer.y * .34, -.34, .34)
    eyeTargetY = THREE.MathUtils.clamp(pointer.x * .42, -.42, .42)
    if (dragging) { dragRotation += (event.clientX - previousX) * .008; previousX = event.clientX }
  }
  const onPointerUp = event => {
    dragging = false
    renderer?.domElement.releasePointerCapture?.(event.pointerId)
  }
  const onPointerLeave = () => {
    if (!dragging) { eyeTargetX = 0; eyeTargetY = 0 }
  }
  const onContextLost = event => { event.preventDefault(); unavailable.value = true }
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
    if (orangeRoot) {
      orangeRoot.rotation.y += (dragRotation - orangeRoot.rotation.y) * .08
      if (!reduceMotion && !dragging) orangeRoot.position.y = Math.sin(time * .0012) * .035
    }
    if (!reduceMotion && interestOrbit) interestOrbit.rotation.y = time * .00032
    eyes.forEach(eye => {
      const easeValue = reduceMotion ? 1 : .18
      eye.rotation.x += (eyeTargetX - eye.rotation.x) * easeValue
      eye.rotation.y += (eyeTargetY - eye.rotation.y) * easeValue
    })
    renderer.render(scene, camera)
  }

  const addLightsAndFloor = () => {
    scene.add(new THREE.HemisphereLight(0xfff4df, 0x4f6b5e, 2.2))
    const key = new THREE.DirectionalLight(0xffffff, 3.1)
    key.position.set(4, 6, 5)
    key.castShadow = true
    key.shadow.mapSize.set(1024, 1024)
    scene.add(key)
    const rim = new THREE.PointLight(0xff7a1a, 18, 9)
    rim.position.set(-3.5, 1.2, 2.8)
    scene.add(rim)
    scene.add(mesh(new THREE.CylinderGeometry(2.7, 3.05, .18, 72),
      material(0xffead5, { roughness: .95 }), [0, -1.68, 0]))
    const floorRing = mesh(new THREE.TorusGeometry(2.16, .025, 10, 96),
      material(0xf37021, { emissive: 0xf37021, emissiveIntensity: .2 }), [0, -1.57, 0])
    floorRing.rotation.x = Math.PI / 2
    scene.add(floorRing)
  }

  const mountScene = () => {
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
    addLightsAndFloor()
    rebuildOrange()
    setFocus(props.activeFocus)
    resizeObserver = new ResizeObserver(resize)
    resizeObserver.observe(host.value)
    resize()
    const canvas = renderer.domElement
    canvas.addEventListener('pointerdown', onPointerDown)
    canvas.addEventListener('pointermove', onPointerMove)
    canvas.addEventListener('pointerup', onPointerUp)
    canvas.addEventListener('pointercancel', onPointerUp)
    canvas.addEventListener('pointerleave', onPointerLeave)
    canvas.addEventListener('webglcontextlost', onContextLost)
    renderer.setAnimationLoop(animate)
    ready.value = true
    emit('ready')
  }

  onMounted(() => {
    try { mountScene() }
    catch (error) { console.warn('Persona 3D scene unavailable', error); unavailable.value = true }
  })
  watch(() => props.activeFocus, setFocus)
  watch(() => props.equipment, rebuildEquipment, { deep: true })
  watch(() => props.appearance, rebuildOrange, { deep: true })
  onBeforeUnmount(() => {
    resizeObserver?.disconnect()
    if (renderer) {
      renderer.setAnimationLoop(null)
      const canvas = renderer.domElement
      canvas.removeEventListener('pointerdown', onPointerDown)
      canvas.removeEventListener('pointermove', onPointerMove)
      canvas.removeEventListener('pointerup', onPointerUp)
      canvas.removeEventListener('pointercancel', onPointerUp)
      canvas.removeEventListener('pointerleave', onPointerLeave)
      canvas.removeEventListener('webglcontextlost', onContextLost)
    }
    disposeGroup(orangeRoot)
    scene?.traverse(object => {
      if (object !== orangeRoot) { object.geometry?.dispose?.(); object.material?.dispose?.() }
    })
    renderer?.dispose()
    renderer?.domElement.remove()
  })

  return { host, ready, unavailable, zoomPercent, adjustZoom, resetZoom, showFullBody }
}
