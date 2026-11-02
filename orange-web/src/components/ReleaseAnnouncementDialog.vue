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
      <p>OrangeVC 新版本现已正式上线！本次更新围绕橙子画像、智能分析和项目知识库进行了系统升级。</p>
      <h4>橙子画像工坊玩法升级</h4>
      <p>新增左右手、武器和饰品 DIY。用户可以调整手部大小与位置，并逐步解锁、编辑和穿戴剑、刀、盾牌、枪等造型。</p>
      <h4>智能分析接入橙子画像</h4>
      <p>趋势分析支持结合已确认的目标、兴趣、技能、资源、成长经历和不希望参与的项目类型进行分析，并可搭配知识库、新手项目和职业项目。</p>
      <h4>榜单新增画像项目分析</h4>
      <p>本网站、GitHub、新手推荐和职业成长榜单均可结合橙子画像分析当前项目，帮助用户找到更适合自己的下一步。</p>
      <h4>项目知识库统一升级</h4>
      <p>新手推荐项目和职业成长项目已纳入统一知识库索引，管理端可以查看项目来源和索引状态。</p>
      <h4>稳定性改进</h4>
      <p>完善了画像分析参数校验、趋势分析跳转、榜单分析流程、历史记录保存和页面测试机制。</p>
      <p class="release-tip">如果仍显示旧页面，请强制刷新浏览器后重新进入。</p>
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
.release-announcement { max-height: 58vh; overflow:auto; padding-right:8px; color:#4b3525; line-height:1.75; }
.release-announcement p { margin:0 0 12px; font-size:13px; }
.release-announcement>p:first-child { font-weight:700; }
.release-announcement h4 { margin:18px 0 5px; color:#9a3412; font-size:14px; }
.release-announcement .release-tip { margin-top:18px; padding:10px 12px; border-radius:8px; background:#fff7ed; color:#9a3412; }
</style>
