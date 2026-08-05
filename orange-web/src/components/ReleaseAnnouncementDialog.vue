<template>
  <el-dialog
    v-model="visible"
    title="OrangeVC 功能更新"
    width="min(460px, calc(100vw - 32px))"
    append-to-body
    class="release-announcement-dialog"
    @closed="rememberDismissal"
  >
    <div class="release-announcement">
      <p>本次更新让项目推荐更懂你的目标：</p>
      <ul>
        <li>橙子画像工坊新增手部、武器与饰品 DIY。</li>
        <li>趋势分析和榜单支持使用橙子画像智能匹配。</li>
        <li>新手与职业项目已纳入统一知识库。</li>
      </ul>
    </div>
    <template #footer>
      <el-button type="primary" @click="dismiss">知道了</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import {
  dismissReleaseAnnouncement,
  hasDismissedReleaseAnnouncement,
} from '../utils/releaseAnnouncement'

const props = defineProps({ accountId: { type: [String, Number], required: true } })
const visible = ref(false)

const rememberDismissal = () => dismissReleaseAnnouncement(props.accountId)
const dismiss = () => {
  rememberDismissal()
  visible.value = false
}

onMounted(() => {
  visible.value = !hasDismissedReleaseAnnouncement(props.accountId)
})
</script>

<style scoped>
.release-announcement { color: #4b3525; line-height: 1.75; }
.release-announcement p { margin: 0 0 10px; font-weight: 700; }
.release-announcement ul { margin: 0; padding-left: 20px; }
.release-announcement li + li { margin-top: 6px; }
</style>
