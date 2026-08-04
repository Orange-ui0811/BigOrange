<template>
  <div class="persona-orange-badge" :class="[appearanceClass, { compact }]" :style="{ '--badge-accent': accent, '--badge-body': bodyColor }" role="img" :aria-label="label">
    <span class="badge-leaf"></span><span v-if="appearance.leafStyle === 'DOUBLE' || appearance.leafStyle === 'SPROUT'" class="badge-leaf badge-leaf-secondary"></span>
    <span class="badge-face"><i></i><i></i><b></b></span>
    <span v-if="equippedSlots.includes('GOAL_CROWN')" class="badge-crown"></span>
    <span v-if="equippedSlots.includes('SKILL_VISOR')" class="badge-visor"></span>
    <span v-if="equippedSlots.includes('INTEREST_ORBIT')" class="badge-orbit"></span>
    <span v-if="equippedSlots.includes('GROWTH_TRAIL')" class="badge-trail"></span>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  equipment: { type: Array, default: () => [] },
  compact: { type: Boolean, default: false },
  label: { type: String, default: '橙子画像头像' },
  appearance: { type: Object, default: () => ({}) },
})

const equippedSlots = computed(() => props.equipment.filter(item => item.equipped).map(item => item.slotCode))
const accent = computed(() => props.equipment.find(item => item.equipped)?.colorHex || '#f37021')
const bodyColor = computed(() => props.appearance.bodyColor || '#f37021')
const appearanceClass = computed(() => [
  `face-${(props.appearance.faceStyle || 'FRIENDLY').toLowerCase()}`,
  `leaf-${(props.appearance.leafStyle || 'CLASSIC').toLowerCase()}`,
  `surface-${(props.appearance.surfaceStyle || 'NATURAL').toLowerCase()}`,
])
</script>

<style scoped>
.persona-orange-badge{--size:92px;position:relative;display:inline-grid;width:var(--size);height:var(--size);place-items:center;border:1px solid rgba(243,112,33,.26);border-radius:28%;background:radial-gradient(circle at 34% 27%,color-mix(in srgb,var(--badge-body),white 42%) 0 7%,var(--badge-body) 48%,color-mix(in srgb,var(--badge-body),black 18%) 100%);box-shadow:inset -10px -12px 20px rgba(72,35,12,.18),0 12px 28px rgba(154,52,18,.14)}.persona-orange-badge.surface-glossy{box-shadow:inset 12px 14px 18px rgba(255,255,255,.34),inset -10px -12px 20px rgba(72,35,12,.14),0 12px 28px rgba(154,52,18,.14)}.persona-orange-badge.surface-matte{filter:saturate(.88);box-shadow:inset -7px -8px 14px rgba(72,35,12,.13),0 10px 22px rgba(90,45,18,.1)}.persona-orange-badge.compact{--size:52px;border-radius:17px}.badge-leaf{position:absolute;top:-7%;left:49%;width:34%;height:17%;border-radius:100% 0 100% 0;background:#65a30d;transform:rotate(-18deg);transform-origin:left bottom}.badge-leaf-secondary{left:28%;transform:scale(.82) rotate(34deg)}.leaf-sprout .badge-leaf{top:-9%;width:27%;transform:scale(.78) rotate(-7deg)}.leaf-sprout .badge-leaf-secondary{left:34%;transform:scale(.72) rotate(20deg)}.badge-face{position:absolute;inset:0}.badge-face i{position:absolute;top:40%;width:9%;height:13%;border-radius:999px;background:#4a220e}.badge-face i:first-child{left:30%}.badge-face i:nth-child(2){right:30%}.badge-face b{position:absolute;left:43%;top:60%;width:16%;height:7%;border-bottom:3px solid #4a220e;border-radius:0 0 999px 999px}.face-curious .badge-face b{left:46%;width:10%;height:10%;border:3px solid #4a220e;border-radius:50%}.face-cool .badge-face b{left:40%;width:22%;height:0;border-radius:0}.face-cool .badge-face::before,.face-cool .badge-face::after{position:absolute;top:34%;width:17%;height:3px;background:#4a220e;content:''}.face-cool .badge-face::before{left:25%;transform:rotate(-10deg)}.face-cool .badge-face::after{right:25%;transform:rotate(10deg)}.badge-crown{position:absolute;top:-15%;left:24%;width:54%;height:24%;border:4px solid var(--badge-accent);border-bottom:0;border-radius:50% 50% 0 0;transform:perspective(40px) rotateX(55deg)}.badge-visor{position:absolute;top:34%;left:20%;width:62%;height:25%;border:3px solid var(--badge-accent);border-radius:999px;background:rgba(255,255,255,.13)}.badge-orbit{position:absolute;inset:5%;border:2px solid var(--badge-accent);border-radius:50%;transform:rotate(-24deg);box-shadow:18px -9px 0 -7px var(--badge-accent)}.badge-trail{position:absolute;left:24%;bottom:-12%;width:54%;height:18%;border:3px solid var(--badge-accent);border-radius:50%;opacity:.75}
@media(prefers-reduced-motion:no-preference){.badge-orbit{animation:badge-orbit 7s linear infinite}@keyframes badge-orbit{to{transform:rotate(336deg)}}}
</style>
