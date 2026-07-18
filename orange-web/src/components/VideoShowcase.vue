<template>
  <section v-if="safeUrl" class="video-showcase" :class="{ compact }" aria-label="作品视频展示">
    <div class="video-heading">
      <div>
        <span class="video-kicker">SHOWCASE VIDEO</span>
        <strong>{{ heading }}</strong>
      </div>
      <a :href="safeUrl" target="_blank" rel="noopener noreferrer">新窗口观看</a>
    </div>

    <div v-if="kind === 'direct'" class="video-stage">
      <video :src="safeUrl" :poster="safePoster || undefined" controls playsinline preload="metadata">
        当前浏览器不支持视频播放，请使用右上角链接打开。
      </video>
    </div>

    <div v-else-if="embedUrl" class="video-stage embed-stage">
      <iframe :src="embedUrl" :title="heading" loading="lazy" allowfullscreen
              referrerpolicy="strict-origin-when-cross-origin"
              allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture; fullscreen" />
    </div>

    <a v-else :href="safeUrl" target="_blank" rel="noopener noreferrer" class="external-video">
      <span class="play-mark" aria-hidden="true">▶</span>
      <span>
        <strong>视频页面已就绪</strong>
        <small>该平台暂不支持站内嵌入，点击前往原页面观看</small>
      </span>
      <span class="external-arrow" aria-hidden="true">↗</span>
    </a>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  url: { type: String, default: '' },
  poster: { type: String, default: '' },
  title: { type: String, default: '' },
  compact: { type: Boolean, default: false },
})

const localVideoPattern = /^\/(?:uploads\/video|api\/media\/video)\/[0-9a-f-]{36}\.(mp4|mov|m4v|webm)$/i
const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080'
const assetOrigin = (() => {
  try { return new URL(apiBase, window.location.origin).origin }
  catch { return window.location.origin }
})()

const parsedUrl = computed(() => {
  try {
    const value = props.url?.trim()
    if (!value) return null
    if (localVideoPattern.test(value)) return new URL(value, assetOrigin)
    const parsed = new URL(value)
    return parsed.protocol === 'https:' ? parsed : null
  } catch { return null }
})
const safeUrl = computed(() => parsedUrl.value?.href || '')
const safePoster = computed(() => {
  const value = props.poster?.trim()
  if (!value) return ''
  if (/^\/(?:uploads\/(?:cover|general)|api\/media\/(?:cover|general))\/[0-9a-f-]{8,36}\.(png|jpe?g|gif|webp)$/i.test(value)) {
    return new URL(value, assetOrigin).href
  }
  try {
    const parsed = new URL(value)
    return parsed.protocol === 'https:' ? parsed.href : ''
  } catch { return '' }
})
const heading = computed(() => props.title ? `${props.title} · 视频展示` : '作品视频展示')

const kind = computed(() => {
  const parsed = parsedUrl.value
  if (!parsed) return 'invalid'
  if (/\.(mp4|webm|ogg|m4v|mov)$/i.test(parsed.pathname)) return 'direct'
  if (/(^|\.)bilibili\.com$/i.test(parsed.hostname) && /^\/video\/(BV[0-9A-Za-z]+|av\d+)/i.test(parsed.pathname)) return 'bilibili'
  if (/(^|\.)youtube\.com$/i.test(parsed.hostname) || /(^|\.)youtu\.be$/i.test(parsed.hostname)) return 'youtube'
  return 'external'
})

const embedUrl = computed(() => {
  const parsed = parsedUrl.value
  if (!parsed) return ''
  if (kind.value === 'bilibili') {
    const id = parsed.pathname.match(/^\/video\/(BV[0-9A-Za-z]+|av\d+)/i)?.[1] || ''
    if (/^BV/i.test(id)) return `https://player.bilibili.com/player.html?bvid=${encodeURIComponent(id)}`
    if (/^av\d+$/i.test(id)) return `https://player.bilibili.com/player.html?aid=${id.slice(2)}`
  }
  if (kind.value === 'youtube') {
    const id = parsed.hostname.endsWith('youtu.be')
      ? parsed.pathname.split('/').filter(Boolean)[0]
      : parsed.searchParams.get('v') || parsed.pathname.match(/^\/shorts\/([^/]+)/)?.[1]
    if (id && /^[A-Za-z0-9_-]{6,20}$/.test(id)) return `https://www.youtube-nocookie.com/embed/${id}`
  }
  return ''
})
</script>

<style scoped>
.video-showcase {
  margin-top: 24px;
  overflow: hidden;
  border: 1px solid rgba(254, 215, 170, .9);
  border-radius: 24px;
  background: #211711;
  box-shadow: 0 18px 42px rgba(92, 38, 15, .16);
}
.video-heading {
  display: flex;
  min-height: 64px;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 12px 16px;
  color: #fff7ed;
  background: linear-gradient(90deg, #211711, #392116);
}
.video-heading > div { min-width: 0; }
.video-heading strong { display: block; margin-top: 2px; font: 800 14px/1.35 var(--font-body); text-wrap: pretty; }
.video-kicker { color: #f6a66d; font: 800 9px/1 var(--font-mono); letter-spacing: .14em; }
.video-heading a {
  flex-shrink: 0;
  color: #fed7aa;
  font: 700 11px/1 var(--font-body);
  text-decoration: none;
}
.video-heading a:hover { color: #fff; }
.video-stage { position: relative; width: 100%; aspect-ratio: 16 / 9; background: #0f0c0a; }
.video-stage video, .video-stage iframe { display: block; width: 100%; height: 100%; border: 0; object-fit: contain; }
.external-video {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  min-height: 170px;
  align-items: center;
  gap: 16px;
  padding: 24px;
  color: #fff7ed;
  text-decoration: none;
  background:
    radial-gradient(circle at 18% 30%, rgba(243,112,33,.30), transparent 28%),
    repeating-linear-gradient(0deg, rgba(255,255,255,.025) 0, rgba(255,255,255,.025) 1px, transparent 1px, transparent 16px),
    #18110d;
}
.external-video:hover .play-mark { transform: scale(1.06); background: #ff8a33; }
.external-video strong { display: block; font: 800 16px/1.35 var(--font-body); }
.external-video small { display: block; margin-top: 5px; color: #c9b7aa; font-size: 12px; line-height: 1.55; }
.play-mark {
  display: grid;
  width: 54px;
  height: 54px;
  place-items: center;
  border-radius: 18px;
  background: var(--brand);
  box-shadow: 4px 4px 0 rgba(255,255,255,.11);
  transition: transform .18s, background .18s;
}
.external-arrow { color: #f6a66d; font: 900 22px/1 var(--font-mono); }
.compact { margin-top: 10px; border-radius: 18px; box-shadow: 0 10px 26px rgba(92, 38, 15, .12); }
.compact .video-heading { min-height: 54px; }
.compact .external-video { min-height: 120px; padding: 18px; }
.compact .play-mark { width: 44px; height: 44px; border-radius: 14px; }

@media (max-width: 620px) {
  .video-heading { align-items: flex-start; }
  .video-heading a { padding-top: 4px; }
  .external-video { min-height: 140px; padding: 18px; }
  .external-video small { font-size: 11px; }
}
</style>
