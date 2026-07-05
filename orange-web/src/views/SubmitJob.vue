<template>
  <div style="max-width:700px;margin:0 auto">
    <h3 style="font-weight:700;margin:0 0 16px">投稿招聘信息</h3>

    <el-card shadow="never">
      <el-form :model="form" label-position="top">
        <el-form-item label="岗位名称">
          <el-input v-model="form.position" placeholder="如：AI Agent 算法工程师" maxlength="200" />
        </el-form-item>
        <el-form-item label="技术方向">
          <el-select v-model="form.direction" placeholder="选择方向" style="width:100%">
            <el-option v-for="d in directions" :key="d" :label="d" :value="d" />
          </el-select>
        </el-form-item>
        <el-form-item label="JD 内容">
          <el-input v-model="form.content" type="textarea" :rows="12" placeholder="请粘贴岗位描述、职责和要求" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="submit" :loading="submitting" :disabled="!agreed" style="background:#e65100;border-color:#e65100;width:100%">提交审核</el-button>
        </el-form-item>
      </el-form>

      <div style="background:#fff3e0;padding:12px;border-radius:8px;font-size:12px;line-height:1.8;color:#5d4037">
        <p style="font-weight:600;margin:0 0 4px">投稿须知：</p>
        <p>1. 禁止直接复制企业官网完整招聘文案、搬运他人原创 JD</p>
        <p>2. 上传内容需自己原创改写，或持有企业官方转载授权</p>
        <p>3. 因侵权引发的法律责任全部由上传用户自行承担</p>
        <p>4. 平台有权随时删除涉嫌侵权内容，多次侵权可封禁账号</p>
        <p>5. 平台会留存你的投稿账号、IP、时间用于侵权追溯</p>
        <el-checkbox v-model="agreed" style="margin-top:8px">我已阅读并同意以上条款</el-checkbox>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import request from '../api/request'
import { getDirections } from '../api/job'

const router = useRouter()
const form = ref({ position: '', direction: '', content: '' })
const directions = ref([])
const agreed = ref(false)
const submitting = ref(false)

const submit = async () => {
  if (!form.value.position) { ElMessage.warning('请输入岗位名称'); return }
  if (!form.value.content) { ElMessage.warning('请输入JD内容'); return }
  submitting.value = true
  try {
    await request.post('/app/job/submit', form.value)
    ElMessage.success('投稿成功，等待审核')
    router.push('/trend')
  } catch (e) { ElMessage.error(e?.response?.data?.msg || '投稿失败') }
  finally { submitting.value = false }
}

onMounted(async () => {
  try { const r = await getDirections(); directions.value = r.data || [] } catch {}
})
</script>
