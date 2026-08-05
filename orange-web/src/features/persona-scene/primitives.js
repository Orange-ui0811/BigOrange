import * as THREE from 'three'

export const material = (color, options = {}) => new THREE.MeshStandardMaterial({
  color, roughness: options.roughness ?? .55, metalness: options.metalness ?? .05,
  transparent: options.transparent ?? false, opacity: options.opacity ?? 1,
  emissive: options.emissive || 0x000000, emissiveIntensity: options.emissiveIntensity || 0,
})

export const mesh = (geometry, meshMaterial, position = [0, 0, 0]) => {
  const value = new THREE.Mesh(geometry, meshMaterial)
  value.position.set(...position)
  value.castShadow = true
  value.receiveShadow = true
  return value
}

export const focusGroup = code => {
  const group = new THREE.Group()
  group.userData.focusCode = code
  return group
}

export const disposeGroup = group => {
  group?.traverse(object => {
    object.geometry?.dispose?.()
    if (Array.isArray(object.material)) object.material.forEach(value => value.dispose?.())
    else object.material?.dispose?.()
  })
}
