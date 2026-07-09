<template>
  <div class="half-orange-outer" :style="outerStyle">
    <div class="half-orange-wrap" :style="scaleStyle">
      <div class="shadow"></div>
      <div class="orange-model">
        <div class="orange-circle">
          <div class="pith pith0"></div>
          <div class="pith pith1"></div>
          <div class="pith pith2"></div>
          <div class="pith pith3"></div>
          <div class="pith pith4"></div>
          <div class="pith pith5"></div>
          <div class="pith pith6"></div>
          <div class="pith pith7"></div>
          <div class="core"></div>
        </div>
        <div class="cut-edge"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  size: { type: Number, default: 38 }
})

const outerStyle = computed(() => ({
  width: props.size + 'px',
  height: props.size + 'px',
}))

const scaleStyle = computed(() => ({
  transform: `scale(${props.size / 360})`,
}))
</script>

<style scoped>
.half-orange-outer {
  display: inline-block;
  flex-shrink: 0;
  overflow: visible;
}
.half-orange-wrap {
  width: 360px;
  height: 360px;
  position: relative;
  transform-origin: top left;
}
.orange-model {
  width: 100%;
  height: 100%;
  position: relative;
  transform: rotate(-90deg);
}
.orange-circle {
  width: 360px;
  height: 360px;
  border-radius: 50%;
  box-shadow: inset 0 0 0 14px #e67018;
  background: linear-gradient(120deg, #ffb747, #ff8020);
  position: absolute;
  clip-path: polygon(0 0, 50% 0, 50% 100%, 0 100%);
}
.pith {
  position: absolute;
  left: 50%;
  top: 0;
  width: 4px;
  height: 50%;
  background: #fff8dd;
  transform-origin: bottom center;
}
.pith0 { transform: translateX(-50%) rotate(0deg); }
.pith1 { transform: translateX(-50%) rotate(45deg); }
.pith2 { transform: translateX(-50%) rotate(90deg); }
.pith3 { transform: translateX(-50%) rotate(135deg); }
.pith4 { transform: translateX(-50%) rotate(180deg); }
.pith5 { transform: translateX(-50%) rotate(225deg); }
.pith6 { transform: translateX(-50%) rotate(270deg); }
.pith7 { transform: translateX(-50%) rotate(315deg); }
.core {
  width: 60px;
  height: 60px;
  background: #fff8dd;
  border-radius: 50%;
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  z-index: 2;
}
.cut-edge {
  width: 8px;
  height: 100%;
  background: #c05610;
  position: absolute;
  left: 50%;
  top: 0;
  transform: translateX(-50%);
  z-index: 1;
}
.shadow {
  width: 300px;
  height: 28px;
  background: rgba(90, 45, 20, 0.28);
  border-radius: 50%;
  filter: blur(6px);
  position: absolute;
  bottom: -12px;
  left: 30px;
}
</style>
