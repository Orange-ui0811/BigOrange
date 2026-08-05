import * as THREE from 'three'
import { material, mesh } from './primitives'

export const createOrange = (appearance = {}) => {
  const group = new THREE.Group()
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
    const ripple = (Math.sin(vertex.y * 11 + vertex.x * 4) * .013
      + Math.sin(vertex.z * 17) * .008) * rippleScale
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
  const stem = mesh(new THREE.CylinderGeometry(.08, .12, .48, 14),
    material(0x5f3818, { roughness: .9 }), [.02, 1.64 * face.bodyHeight, -.02])
  stem.rotation.z = -.12
  group.add(stem)
  const leafStyle = appearance.leafStyle || 'CLASSIC'
  const leafSpecs = leafStyle === 'DOUBLE'
    ? [[[.34, 1.72, -.02], -.35, 1], [[-.27, 1.76, -.04], .5, .82]]
    : leafStyle === 'SPROUT'
      ? [[[.2, 1.83, -.04], -.12, .72], [[-.18, 1.84, -.04], .18, .72]]
      : [[[.34, 1.72, -.02], -.35, 1]]
  leafSpecs.forEach(([leafPosition, rotation, scale]) => {
    const leaf = mesh(new THREE.SphereGeometry(.42, 24, 14),
      material(0x65a30d, { roughness: .75 }),
      [leafPosition[0], leafPosition[1] * face.bodyHeight, leafPosition[2]])
    leaf.scale.set(scale, .18 * scale, .48 * scale)
    leaf.rotation.z = rotation
    group.add(leaf)
  })

  const eyeMaterial = material(0x3f1d0b, { roughness: .38 })
  const scleraMaterial = material(0xfff8eb, { roughness: .28 })
  const shineMaterial = new THREE.MeshBasicMaterial({ color: 0xffffff })
  const createEye = eyePosition => {
    const eye = new THREE.Group()
    eye.position.set(...eyePosition)
    eye.add(mesh(new THREE.SphereGeometry(.15 * face.eyeSize, 24, 18), scleraMaterial))
    const pupil = mesh(new THREE.SphereGeometry(.082 * face.eyeSize * face.pupilSize, 20, 16),
      eyeMaterial, [0, 0, .122 * face.eyeSize])
    pupil.add(mesh(new THREE.SphereGeometry(.022, 10, 8), shineMaterial, [-.024, .026, .067]))
    eye.add(pupil)
    return eye
  }
  const eyeX = .42 * face.eyeSpacing * face.bodyWidth
  const eyeY = .34 * face.bodyHeight + face.eyeHeight
  const eyes = [createEye([-eyeX, eyeY, 1.3]), createEye([eyeX, eyeY, 1.3])]
  group.add(...eyes)

  const faceStyle = appearance.faceStyle || 'FRIENDLY'
  const mouthY = -.12 * face.bodyHeight + face.mouthHeight
  if (face.mouthOpen > .08 || faceStyle === 'CURIOUS') {
    const openAmount = Math.max(face.mouthOpen, faceStyle === 'CURIOUS' ? .28 : 0)
    const mouth = mesh(new THREE.TorusGeometry(.12, .027 * face.mouthThickness, 10, 36),
      eyeMaterial, [0, mouthY - .08, 1.43])
    mouth.scale.set(face.mouthWidth, .55 + openAmount * 1.8, 1)
    group.add(mouth)
  } else {
    const curve = new THREE.QuadraticBezierCurve3(
      new THREE.Vector3(-.27 * face.mouthWidth, mouthY, 1.42),
      new THREE.Vector3(0, mouthY - .22 * face.mouthCurve, 1.48),
      new THREE.Vector3(.27 * face.mouthWidth, mouthY, 1.42))
    group.add(mesh(new THREE.TubeGeometry(curve, 28, .026 * face.mouthThickness, 8, false), eyeMaterial))
  }
  const browY = .62 * face.bodyHeight + face.browHeight
  const browAngle = faceStyle === 'COOL' ? -.16 : faceStyle === 'CURIOUS' ? .08 : 0
  const leftBrow = mesh(new THREE.BoxGeometry(.3 * face.browLength, .035 * face.browThickness, .035),
    eyeMaterial, [-eyeX, browY, 1.36])
  const rightBrow = mesh(new THREE.BoxGeometry(.3 * face.browLength, .035 * face.browThickness, .035),
    eyeMaterial, [eyeX, browY, 1.36])
  leftBrow.rotation.z = browAngle + face.browAngle
  rightBrow.rotation.z = -browAngle - face.browAngle
  group.add(leftBrow, rightBrow)
  return { group, eyes }
}
