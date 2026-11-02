<template>
  <div v-if="playableUrl" class="admin-video-preview">
    <video
      :src="playableUrl"
      :poster="posterUrl || undefined"
      controls
      playsinline
      preload="metadata"
    >
      当前浏览器不支持视频播放。
    </video>
    <span>可直接播放、暂停、拖动进度并全屏检查作品视频</span>
  </div>
  <el-alert
    v-else
    title="该作品保存的是旧版外部视频链接，请通知用户重新上传视频文件"
    type="warning"
    :closable="false"
    show-icon
  />
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  url: { type: String, default: '' },
  poster: { type: String, default: '' },
})

const API = (import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080').replace(/\/$/, '')
const uploadedVideoPattern = /^\/(?:uploads\/video|api\/media\/video)\/[0-9a-f-]{36}\.(mp4|mov|m4v|webm)$/i
const uploadedImagePattern = /^\/(?:uploads\/(?:cover|general)|api\/media\/(?:cover|general))\/[0-9a-f-]{8,36}\.(png|jpe?g|gif|webp)$/i
const assetOrigin = /^https?:\/\//i.test(API) ? new URL(API).origin : window.location.origin
const resolveAsset = (value) => new URL(value, assetOrigin).href

const playableUrl = computed(() => {
  const value = props.url?.trim()
  return uploadedVideoPattern.test(value) ? resolveAsset(value) : ''
})
const posterUrl = computed(() => {
  const value = props.poster?.trim()
  return uploadedImagePattern.test(value) ? resolveAsset(value) : ''
})
</script>

<style scoped>
.admin-video-preview {
  width: min(100%, 760px);
  overflow: hidden;
  border: 1px solid #e4e7ed;
  border-radius: 12px;
  background: #111;
}
.admin-video-preview video {
  display: block;
  width: 100%;
  max-height: 520px;
  aspect-ratio: 16 / 9;
  object-fit: contain;
  background: #0b0b0b;
}
.admin-video-preview span {
  display: block;
  padding: 8px 12px;
  color: #c0c4cc;
  background: #1d1e1f;
  font-size: 12px;
  line-height: 1.5;
}
</style>
