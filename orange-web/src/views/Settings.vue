<template>
  <div class="settings-page">
    <header class="settings-hero">
      <span>ACCOUNT & PRIVACY</span>
      <h2>设置与账号权利</h2>
      <p>管理登录安全，了解数据使用方式，并通过正式渠道申请注销、删除或举报申诉。</p>
    </header>

    <div class="settings-grid">
      <el-card shadow="never" class="settings-card">
        <h3>修改密码</h3>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
          <el-form-item label="旧密码" prop="old"><el-input v-model="form.old" type="password" show-password autocomplete="current-password"/></el-form-item>
          <el-form-item label="新密码" prop="new"><el-input v-model="form.new" type="password" show-password autocomplete="new-password" placeholder="6-128 位，包含字母和数字"/></el-form-item>
          <el-button type="primary" @click="submit" :loading="loading" class="btn-primary">确认修改</el-button>
        </el-form>
      </el-card>

      <el-card shadow="never" class="settings-card rights-card">
        <h3>账号与个人信息</h3>
        <p>您可以申请查询、复制、更正、删除个人信息，撤回同意或注销账号。为保护账号安全，平台会通过登录态或注册邮箱核验身份。</p>
        <ul>
          <li>账号注销后将停止登录与公开展示。</li>
          <li>非必要数据会删除或匿名化。</li>
          <li>交易、支付和争议记录按法定期限隔离保存。</li>
        </ul>
        <div class="button-row">
          <el-button @click="$router.push('/support')">查看流程</el-button>
          <a class="mail-button" :href="cancelMail">发起注销申请</a>
        </div>
      </el-card>
    </div>

    <section class="legal-panel">
      <div>
        <span>LEGAL & SAFETY</span>
        <h3>法律、隐私与平台治理</h3>
      </div>
      <nav>
        <router-link to="/legal/terms"><b>用户协议</b><small>账号、内容、交易与 AI 使用规则</small></router-link>
        <router-link to="/legal/privacy"><b>隐私政策</b><small>处理目的、保存期限与用户权利</small></router-link>
        <router-link to="/legal/third-parties"><b>第三方服务清单</b><small>云服务、AI、支付和通知服务</small></router-link>
        <router-link to="/support"><b>举报、下架与申诉</b><small>材料清单、处理流程与联系方式</small></router-link>
      </nav>
    </section>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../api/request'
import { LEGAL_CONTACT } from '../content/legal'

const loading = ref(false)
const form = reactive({ old: '', new: '' })
const rules = {
  old: [{ required: true, message: '请输入旧密码' }],
  new: [
    { required: true, message: '请输入新密码' },
    { pattern: /^(?=.*[A-Za-z])(?=.*\d).{6,128}$/, message: '密码需为 6-128 位且包含字母和数字' },
  ],
}
const formRef = ref(null)
const cancelBody = '用户名：\n注册邮箱：\n是否存在未结订单：\n注销原因（选填）：\n\n本人确认申请注销账号，并了解依法需要保存的交易、审计和争议记录不会立即删除。'
const cancelMail = `mailto:${LEGAL_CONTACT}?subject=${encodeURIComponent('【账号注销申请】OrangeVC')}&body=${encodeURIComponent(cancelBody)}`

const submit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const response = await request.put('/app/auth/password', { oldPassword: form.old, newPassword: form.new })
    if (response.code !== 1) return
    ElMessage.success('密码修改成功')
    form.old = ''
    form.new = ''
  } finally { loading.value = false }
}
</script>

<style scoped>
.settings-page { max-width: 980px; margin: 0 auto; }
.settings-hero { margin-bottom: 24px; padding: 28px; border: 1px solid var(--border-orange); border-radius: 24px; background: linear-gradient(135deg, #fff, #fff7ed); }
.settings-hero > span, .legal-panel > div > span { color: var(--brand-strong); font: 800 11px/1 var(--font-mono); letter-spacing: .14em; }
.settings-hero h2 { margin: 8px 0; font-size: 30px; }.settings-hero p { margin: 0; color: var(--text-secondary); line-height: 1.7; }
.settings-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20px; }
.settings-card { border: 1px solid var(--border-orange); border-radius: 20px; }.settings-card h3 { margin: 0 0 18px; font-size: 20px; }
.btn-primary { background: var(--brand); border-color: var(--brand); }
.rights-card p, .rights-card li { color: var(--text-secondary); line-height: 1.75; }.rights-card ul { padding-left: 20px; }
.button-row { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 18px; }
.mail-button { display: inline-flex; align-items: center; padding: 8px 15px; border-radius: 7px; background: #fff7ed; color: var(--brand-strong); font-size: 14px; font-weight: 700; text-decoration: none; }
.legal-panel { margin-top: 20px; padding: 28px; border-radius: 22px; background: #431d0c; color: #fff7ed; }.legal-panel h3 { margin: 8px 0 20px; font-size: 22px; }
.legal-panel nav { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }.legal-panel a { display: grid; gap: 5px; padding: 16px; border: 1px solid rgba(253,186,116,.28); border-radius: 14px; color: #fff7ed; text-decoration: none; }.legal-panel a:hover { border-color: #fdba74; background: rgba(255,255,255,.04); }.legal-panel small { color: #fed7aa; line-height: 1.5; }
@media (max-width: 760px) { .settings-grid, .legal-panel nav { grid-template-columns: 1fr; } }
</style>
