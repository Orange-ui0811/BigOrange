<template>
  <svg class="pixel-orange-logo" :class="{ bounce: bounce }" viewBox="0 0 40 40" aria-hidden="true" v-bind="$attrs">
    <rect x="14" y="1" width="3" height="8" fill="#4d230d" />
    <rect x="17" y="2" width="1" height="7" fill="#4d230d" />
    <rect x="15" y="2" width="2" height="6" fill="#79bd28" />
    <rect x="19" y="2" width="9" height="2" fill="#4d230d" />
    <rect x="18" y="4" width="12" height="2" fill="#4d230d" />
    <rect x="18" y="6" width="11" height="2" fill="#4d230d" />
    <rect x="20" y="4" width="8" height="2" fill="#8bd33a" />
    <rect x="19" y="6" width="8" height="2" fill="#78c42f" />
    <rect x="12" y="6" width="10" height="2" fill="#4d230d" />
    <rect x="9" y="8" width="4" height="2" fill="#4d230d" />
    <rect x="7" y="10" width="3" height="2" fill="#4d230d" />
    <rect x="5" y="12" width="3" height="4" fill="#4d230d" />
    <rect x="4" y="16" width="2" height="5" fill="#4d230d" />
    <rect x="3" y="21" width="2" height="7" fill="#4d230d" />
    <rect x="4" y="28" width="2" height="4" fill="#4d230d" />
    <rect x="6" y="32" width="3" height="3" fill="#4d230d" />
    <rect x="9" y="35" width="5" height="2" fill="#4d230d" />
    <rect x="14" y="37" width="13" height="1" fill="#4d230d" />
    <rect x="27" y="35" width="5" height="2" fill="#4d230d" />
    <rect x="32" y="32" width="3" height="3" fill="#4d230d" />
    <rect x="35" y="27" width="2" height="5" fill="#4d230d" />
    <rect x="36" y="18" width="1" height="9" fill="#4d230d" />
    <rect x="35" y="14" width="2" height="4" fill="#4d230d" />
    <rect x="33" y="11" width="3" height="3" fill="#4d230d" />
    <rect x="30" y="9" width="3" height="2" fill="#4d230d" />
    <rect x="27" y="7" width="3" height="2" fill="#4d230d" />
    <rect x="13" y="8" width="14" height="2" fill="#ffad3f" />
    <rect x="27" y="9" width="3" height="1" fill="#ffad3f" />
    <rect x="10" y="10" width="23" height="2" fill="#ffa739" />
    <rect x="8" y="12" width="27" height="4" fill="#ffa033" />
    <rect x="6" y="16" width="29" height="5" fill="#ff9a2e" />
    <rect x="5" y="21" width="31" height="7" fill="#ff9329" />
    <rect x="6" y="28" width="29" height="4" fill="#ff8823" />
    <rect x="9" y="32" width="23" height="3" fill="#ff7e1f" />
    <rect x="14" y="35" width="13" height="2" fill="#ef6f18" />
    <rect x="33" y="18" width="3" height="8" fill="#ff7623" />
    <rect x="10" y="13" width="3" height="3" fill="#fff4cb" class="highlight" />
    <rect x="9" y="16" width="3" height="5" fill="#ffe29d" class="highlight" />
    <rect x="10" y="21" width="2" height="4" fill="#ffd17b" class="highlight" />
    <rect x="13" y="12" width="4" height="1" fill="#fff8dc" class="highlight" />
    <rect x="14" y="21" width="2" height="3" fill="#5c260f" />
    <rect x="24" y="21" width="2" height="3" fill="#5c260f" />
    <rect x="18" y="28" width="1" height="1" fill="#5c260f" />
    <rect x="22" y="28" width="1" height="1" fill="#5c260f" />
    <rect x="19" y="29" width="1" height="1" fill="#5c260f" />
    <rect x="21" y="29" width="1" height="1" fill="#5c260f" />
    <rect x="20" y="30" width="1" height="1" fill="#5c260f" />
  </svg>
</template>

<script setup>
defineProps({
  bounce: { type: Boolean, default: false }
})
</script>

<style scoped>
.pixel-orange-logo {
  image-rendering: pixelated;
  image-rendering: crisp-edges;
  display: block;
  filter: drop-shadow(0 7px 0 rgba(92, 38, 15, .14));
}
.highlight {
  animation: highlight-shimmer 2.5s ease-in-out infinite;
}
.pixel-orange-logo.bounce {
  animation: logo-bounce 3s ease-in-out infinite;
}
@keyframes logo-bounce {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-3px); }
}
@keyframes highlight-shimmer {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.55; }
}
</style>
