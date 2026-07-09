<template>
  <svg class="mascot-orange" :class="expression" viewBox="0 0 40 40" aria-hidden="true" v-bind="$attrs">
    <!-- 茎干 + 叶子 -->
    <rect x="14" y="1" width="3" height="8" fill="#4d230d" />
    <rect x="17" y="2" width="1" height="7" fill="#4d230d" />
    <rect x="15" y="2" width="2" height="6" fill="#79bd28" />
    <rect x="19" y="2" width="9" height="2" fill="#4d230d" />
    <rect x="18" y="4" width="12" height="2" fill="#4d230d" />
    <rect x="18" y="6" width="11" height="2" fill="#4d230d" />
    <rect x="20" y="4" width="8" height="2" fill="#8bd33a" />
    <rect x="19" y="6" width="8" height="2" fill="#78c42f" />
    <!-- 外轮廓 -->
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
    <!-- 身体渐变 -->
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
    <!-- 高光 -->
    <rect x="10" y="13" width="3" height="3" fill="#fff4cb" class="hi" />
    <rect x="9" y="16" width="3" height="5" fill="#ffe29d" class="hi" />
    <rect x="10" y="21" width="2" height="4" fill="#ffd17b" class="hi" />
    <rect x="13" y="12" width="4" height="1" fill="#fff8dc" class="hi" />

    <!-- ═══ 腮红（部分表情） ═══ -->
    <template v-if="blush">
      <rect x="11" y="23" width="2" height="1" fill="#ff9999" opacity="0.5" />
      <rect x="27" y="23" width="2" height="1" fill="#ff9999" opacity="0.5" />
    </template>

    <!-- ═══ 眼睛 ═══ -->
    <template v-for="r in eyes" :key="'e'+r.x+r.y">
      <rect :x="r.x" :y="r.y" :width="r.w" :height="r.h" :fill="r.fill" />
    </template>

    <!-- ═══ 嘴巴 ═══ -->
    <template v-for="r in mouth" :key="'m'+r.x+r.y">
      <rect :x="r.x" :y="r.y" :width="r.w" :height="r.h" :fill="r.fill" />
    </template>
  </svg>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  expression: { type: String, default: 'smile' }
})

const F = '#4d230d' // 眼睛嘴巴色
const W = '#fffef5' // 高光白眼

const eyes = computed(() => {
  switch (props.expression) {
    // 🤩 哇塞（原闪闪位置）— 双眼星星
    case 'sparkle':
      return [
        { x:13,y:20,w:1,h:1,fill:F },{ x:15,y:20,w:1,h:1,fill:F },
        { x:14,y:21,w:1,h:1,fill:F },{ x:12,y:21,w:1,h:1,fill:F },{ x:16,y:21,w:1,h:1,fill:F },
        { x:13,y:22,w:1,h:1,fill:F },{ x:15,y:22,w:1,h:1,fill:F },
        { x:14,y:23,w:1,h:1,fill:F },
        { x:24,y:20,w:1,h:1,fill:F },{ x:26,y:20,w:1,h:1,fill:F },
        { x:25,y:21,w:1,h:1,fill:F },{ x:23,y:21,w:1,h:1,fill:F },{ x:27,y:21,w:1,h:1,fill:F },
        { x:24,y:22,w:1,h:1,fill:F },{ x:26,y:22,w:1,h:1,fill:F },
        { x:25,y:23,w:1,h:1,fill:F },
      ]
    // 🤩 哇塞 — 双眼星星
    case 'amazed':
      return [
        { x:13,y:20,w:1,h:1,fill:F },{ x:15,y:20,w:1,h:1,fill:F },
        { x:14,y:21,w:1,h:1,fill:F },{ x:12,y:21,w:1,h:1,fill:F },{ x:16,y:21,w:1,h:1,fill:F },
        { x:13,y:22,w:1,h:1,fill:F },{ x:15,y:22,w:1,h:1,fill:F },
        { x:14,y:23,w:1,h:1,fill:F },
        { x:24,y:20,w:1,h:1,fill:F },{ x:26,y:20,w:1,h:1,fill:F },
        { x:25,y:21,w:1,h:1,fill:F },{ x:23,y:21,w:1,h:1,fill:F },{ x:27,y:21,w:1,h:1,fill:F },
        { x:24,y:22,w:1,h:1,fill:F },{ x:26,y:22,w:1,h:1,fill:F },
        { x:25,y:23,w:1,h:1,fill:F },
      ]
    // 😉 眨眼 — 左大眼 + 右横线
    case 'wink':
      return [
        { x:12,y:20,w:3,h:3,fill:F },
        { x:13,y:20,w:1,h:1,fill:W },
        { x:24,y:21,w:3,h:1,fill:F },
      ]
    // 👀 好奇 / 🤩 哇塞 — 双眼星星（共用）
    case 'curious':
      return [
        { x:13,y:20,w:1,h:1,fill:F },{ x:15,y:20,w:1,h:1,fill:F },
        { x:14,y:21,w:1,h:1,fill:F },{ x:12,y:21,w:1,h:1,fill:F },{ x:16,y:21,w:1,h:1,fill:F },
        { x:13,y:22,w:1,h:1,fill:F },{ x:15,y:22,w:1,h:1,fill:F },
        { x:14,y:23,w:1,h:1,fill:F },
        { x:24,y:20,w:1,h:1,fill:F },{ x:26,y:20,w:1,h:1,fill:F },
        { x:25,y:21,w:1,h:1,fill:F },{ x:23,y:21,w:1,h:1,fill:F },{ x:27,y:21,w:1,h:1,fill:F },
        { x:24,y:22,w:1,h:1,fill:F },{ x:26,y:22,w:1,h:1,fill:F },
        { x:25,y:23,w:1,h:1,fill:F },
      ]
    // 🥰 喜爱 / 😊 微笑 — 大圆眼 + 眼光
    default:
      return [
        { x:12,y:20,w:3,h:3,fill:F },
        { x:13,y:20,w:1,h:1,fill:W },
        { x:25,y:20,w:3,h:3,fill:F },
        { x:26,y:20,w:1,h:1,fill:W },
      ]
  }
})

const mouth = computed(() => {
  switch (props.expression) {
    // 😊 微笑 — 宽弧形
    case 'smile':
      return [
        { x:17,y:28,w:1,h:1,fill:F }, { x:21,y:28,w:2,h:1,fill:F },
        { x:15,y:29,w:1,h:1,fill:F }, { x:24,y:29,w:1,h:1,fill:F },
        { x:16,y:30,w:1,h:1,fill:F }, { x:23,y:30,w:1,h:1,fill:F },
        { x:17,y:31,w:6,h:1,fill:F },
      ]
    // 🤩 哇塞（原闪闪位置）— 大圆嘴
    case 'sparkle':
      return [
        { x:16,y:28,w:8,h:3,fill:F },
        { x:18,y:31,w:4,h:1,fill:F },
      ]
    // 😉 眨眼 — 俏皮歪笑
    case 'wink':
      return [
        { x:18,y:28,w:1,h:1,fill:F }, { x:22,y:28,w:3,h:1,fill:F },
        { x:19,y:29,w:6,h:1,fill:F },
        { x:18,y:30,w:4,h:1,fill:F },
      ]
    // 👀 好奇（原哇塞位置）— 歪歪小圆嘴
    case 'amazed':
      return [
        { x:17,y:28,w:4,h:3,fill:F },
        { x:18,y:31,w:2,h:1,fill:F },
      ]
    // 🤩 哇塞 — 大圆嘴
    case 'curious':
      return [
        { x:16,y:28,w:8,h:3,fill:F },
        { x:18,y:31,w:4,h:1,fill:F },
      ]
    // 🥰 喜爱 — 登录页微笑弧线
    case 'love':
      return [
        { x:18,y:28,w:1,h:1,fill:F }, { x:22,y:28,w:1,h:1,fill:F },
        { x:19,y:29,w:1,h:1,fill:F }, { x:21,y:29,w:1,h:1,fill:F },
        { x:20,y:30,w:1,h:1,fill:F },
      ]
    default: return []
  }
})

const blush = computed(() =>
  ['smile', 'sparkle', 'love', 'wink'].includes(props.expression)
)
</script>

<style scoped>
.mascot-orange {
  image-rendering: pixelated;
  image-rendering: crisp-edges;
  display: block;
  filter: drop-shadow(0 4px 0 rgba(92, 38, 15, .10));
}
.hi { animation: mascot-shimmer 2.5s ease-in-out infinite; }

/* 每种表情的小动作 */
.mascot-orange.smile   { animation: float-gentle 4s ease-in-out infinite; }
.mascot-orange.sparkle { animation: float-pop 2.5s ease-in-out infinite; }
.mascot-orange.wink    { animation: tilt-wink 3s ease-in-out infinite; }
.mascot-orange.curious { animation: lean-in 3.5s ease-in-out infinite; }
.mascot-orange.amazed  { animation: bounce-excite 2s ease-in-out infinite; }
.mascot-orange.love    { animation: float-gentle 4.5s ease-in-out infinite; }

@keyframes mascot-shimmer {
  0%,100%{opacity:1} 50%{opacity:.55}
}
@keyframes float-gentle {
  0%,100%{transform:translateY(0)} 50%{transform:translateY(-2px)}
}
@keyframes float-pop {
  0%,100%{transform:translateY(0) scale(1)} 30%{transform:translateY(-3px) scale(1.05)} 60%{transform:translateY(-1px) scale(1)}
}
@keyframes tilt-wink {
  0%,100%{transform:rotate(0)} 25%{transform:rotate(-4deg)} 75%{transform:rotate(3deg)}
}
@keyframes lean-in {
  0%,100%{transform:translateY(0) scale(1)} 40%{transform:translateY(-1px) scale(1.04)} 80%{transform:translateY(0) scale(1)}
}
@keyframes bounce-excite {
  0%,100%{transform:translateY(0)} 20%{transform:translateY(-5px)} 40%{transform:translateY(0)} 60%{transform:translateY(-3px)} 80%{transform:translateY(0)}
}
</style>
