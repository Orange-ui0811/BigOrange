<template>
  <div class="support-page">
    <header class="support-header">
      <router-link class="brand" :to="homePath">OrangeVC</router-link>
      <nav>
        <router-link to="/legal/terms">用户协议</router-link>
        <router-link to="/legal/privacy">隐私政策</router-link>
        <router-link to="/legal/third-parties">第三方服务</router-link>
      </nav>
    </header>

    <main id="main-content" class="support-shell" tabindex="-1">
      <section class="hero">
        <div class="eyebrow">TRUST & SAFETY</div>
        <h1>账号、数据、举报与申诉</h1>
        <p>这里集中提供账号注销、个人信息权利、内容举报、侵权下架和处置申诉的正式入口与材料清单。</p>
        <a class="primary" :href="generalMail">联系平台 · {{ LEGAL_CONTACT }}</a>
      </section>

      <section class="notice">
        <strong>处理承诺</strong>
        <span>通常在 7 个工作日内确认受理；个人信息权利申请一般在 15 个工作日内答复。紧急人身安全风险请同时联系公安、急救等有权机关。</span>
      </section>

      <section class="grid">
        <article class="process-card">
          <span class="number">01</span>
          <h2>账号注销与数据删除</h2>
          <ol>
            <li>使用注册邮箱发信，并注明用户名和申请事项。</li>
            <li>平台通过登录态或注册邮箱核验身份。</li>
            <li>处理未结订单、余额、投诉和法定留存事项。</li>
            <li>停止登录与公开展示，删除或匿名化非必要数据。</li>
          </ol>
          <p class="hint">请勿在普通邮件中发送身份证原件、银行卡号或密码。注销不等于删除依法必须保存的交易和争议记录。</p>
          <div class="actions">
            <a :href="cancelMail">申请注销</a>
            <a :href="deleteMail">申请删除数据</a>
          </div>
        </article>

        <article class="process-card">
          <span class="number">02</span>
          <h2>查询、复制、更正与撤回</h2>
          <ol>
            <li>说明请求类型、账号和涉及的数据范围。</li>
            <li>完成必要身份核验。</li>
            <li>平台核查处理依据与可操作范围。</li>
            <li>通过注册邮箱反馈结果或提供安全下载方式。</li>
          </ol>
          <p class="hint">公开资料可在“编辑资料”直接更正；AI 方案和趋势历史可在相应页面逐条删除或清空。</p>
          <div class="actions">
            <a :href="rightsMail">提交权利申请</a>
          </div>
        </article>
      </section>

      <section class="workflow">
        <div class="section-heading">
          <span>CONTENT GOVERNANCE</span>
          <h2>举报、下架与申诉流程</h2>
        </div>
        <div class="steps">
          <div><b>1</b><strong>提交</strong><span>提供内容链接、问题说明、联系方式和初步证据。</span></div>
          <div><b>2</b><strong>核验</strong><span>平台确认材料完整性；紧急违法风险优先处置。</span></div>
          <div><b>3</b><strong>措施</strong><span>转送相关用户，并视风险删除、屏蔽、断链、限流或保全记录。</span></div>
          <div><b>4</b><strong>反馈</strong><span>向双方反馈结果与理由；依法不能披露的内容除外。</span></div>
          <div><b>5</b><strong>申诉</strong><span>发布者可提交不存在侵权或处置不当的声明和证据。</span></div>
        </div>
      </section>

      <section class="grid governance-grid">
        <article class="process-card">
          <h2>一般内容举报</h2>
          <p>适用于违法有害、诈骗、色情低俗、暴力、恶意营销、泄露个人信息、虚假岗位或其他违反平台规则的内容。</p>
          <h3>请提供</h3>
          <ul><li>准确页面 URL、作品/需求/评论 ID</li><li>问题类型和具体位置</li><li>截图、时间和其他初步证据</li><li>可接收处理结果的邮箱</li></ul>
          <a class="card-action" :href="reportMail">提交举报</a>
        </article>

        <article class="process-card">
          <h2>侵权下架通知</h2>
          <p>适用于著作权、商标、肖像、名誉、隐私、个人信息或商业秘密等权益受到侵害。</p>
          <h3>请提供</h3>
          <ul><li>权利人真实身份与联系方式</li><li>权利归属证明或授权文件</li><li>可准确定位内容的链接/ID</li><li>构成侵权的初步证据和理由</li><li>材料真实、准确的声明</li></ul>
          <a class="card-action" :href="takedownMail">提交下架通知</a>
        </article>

        <article class="process-card">
          <h2>处置申诉 / 不侵权声明</h2>
          <p>内容被拒绝、屏蔽、下架或账号被处置时，可请求人工复核。</p>
          <h3>请提供</h3>
          <ul><li>账号、处置通知或内容 ID</li><li>申诉理由和不存在侵权的初步证据</li><li>必要的权属、授权或来源材料</li><li>真实身份和有效联系方式</li></ul>
          <a class="card-action" :href="appealMail">提交申诉</a>
        </article>
      </section>

      <section class="rules">
        <h2>处理规则与时限</h2>
        <div class="rules-grid">
          <p><strong>紧急违法与人身风险</strong>发现明显违法、有现实人身安全风险或大规模泄露个人信息时，平台可先采取临时措施并保全记录。</p>
          <p><strong>一般举报</strong>通常 7 个工作日内确认受理，材料完整后 15 个工作日内反馈；复杂案件会说明延长原因。</p>
          <p><strong>通知与转送</strong>有效侵权通知会依法转送相关用户；申诉/不侵权声明也会转送通知人，并告知进一步救济渠道。</p>
          <p><strong>错误或恶意提交</strong>提交虚假材料、恶意举报或错误通知造成损害的，提交人可能依法承担责任。</p>
        </div>
      </section>

      <section class="legal-basis">
        <h2>主要规则依据</h2>
        <p>页面流程主要按照《中华人民共和国个人信息保护法》关于告知、最短保存期限、查阅复制、更正删除和请求处理机制的规定，以及《中华人民共和国民法典》第一千一百九十五条、第一千一百九十六条的“通知—必要措施—声明”机制设计。交易信息保存期限同时参考《中华人民共和国电子商务法》第三十一条。</p>
        <div>
          <a href="https://www.miit.gov.cn/jgsj/zfs/fl/art/2022/art_515a4b20c12f430eab54bb4f56d89f56.html" target="_blank" rel="noopener noreferrer">个人信息保护法</a>
          <a href="https://tjca.miit.gov.cn/zwgk/zcwj/flfg/art/2020/art_20cf1a2e1b854924b5caa744c8045d1f.html" target="_blank" rel="noopener noreferrer">民法典</a>
          <a href="https://www.samr.gov.cn/zfjcj/tzgg/art/2023/art_d337c3291e8b40459ca03dea54395856.html" target="_blank" rel="noopener noreferrer">电子商务法</a>
        </div>
      </section>

      <footer>
        <span>法律与安全联系：<a :href="`mailto:${LEGAL_CONTACT}`">{{ LEGAL_CONTACT }}</a></span>
        <span>违法和不良信息也可向 <a href="https://www.12377.cn" target="_blank" rel="noopener noreferrer">中央网信办举报中心</a> 举报。</span>
      </footer>
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { LEGAL_CONTACT } from '../content/legal'

const homePath = computed(() => sessionStorage.getItem('appToken') ? '/market' : '/login')
const mail = (subject, body) => `mailto:${LEGAL_CONTACT}?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`
const generalMail = mail('【OrangeVC】咨询与联系', '账号/用户名：\n问题描述：\n相关页面链接：\n期望处理方式：')
const cancelMail = mail('【账号注销申请】OrangeVC', '用户名：\n注册邮箱：\n是否存在未结订单：\n注销原因（选填）：\n\n本人确认申请注销账号，并了解依法需要保存的交易、审计和争议记录不会立即删除。')
const deleteMail = mail('【个人信息删除申请】OrangeVC', '用户名：\n注册邮箱：\n希望删除的信息范围：\n相关页面/记录：\n申请理由（选填）：')
const rightsMail = mail('【个人信息权利申请】OrangeVC', '请求类型（查询/复制/更正/撤回同意/限制处理/解释）：\n用户名：\n注册邮箱：\n信息范围与具体请求：')
const reportMail = mail('【内容举报】OrangeVC', '举报类型：\n内容 URL/ID：\n问题位置与说明：\n证据说明（附件请注意脱敏）：\n联系邮箱：')
const takedownMail = mail('【侵权下架通知】OrangeVC', '权利人姓名/名称：\n联系方式：\n权利类型：\n侵权内容 URL/ID：\n权利证明与初步侵权证据：\n请求采取的措施：\n\n本人声明以上材料真实、准确，并知悉错误通知可能承担法律责任。')
const appealMail = mail('【处置申诉/不侵权声明】OrangeVC', '账号/用户名：\n处置通知或内容 ID：\n申诉理由：\n不存在侵权或处置不当的初步证据：\n联系方式：\n\n本人声明以上材料真实、准确。')
</script>

<style scoped>
.support-page { min-height: 100vh; background: #fffaf4; color: #30251e; }
.support-header { display: flex; justify-content: space-between; align-items: center; gap: 20px; padding: 16px clamp(20px, 5vw, 72px); border-bottom: 1px solid #fed7aa; background: #fff; }
.brand { color: #c2410c; font: 900 20px/1 monospace; text-decoration: none; }
nav { display: flex; flex-wrap: wrap; gap: 8px 18px; } nav a { color: #6b4c3b; text-decoration: none; }
.support-shell { width: min(1120px, calc(100% - 32px)); margin: 0 auto; padding: 52px 0 72px; outline: none; }
.hero { padding: clamp(28px, 6vw, 72px); border: 1px solid #fdba74; border-radius: 30px; background: linear-gradient(135deg, #fff 15%, #ffedd5); box-shadow: 0 22px 60px rgba(124,45,18,.1); }
.eyebrow, .section-heading span { color: #ea580c; font: 800 12px/1 monospace; letter-spacing: .14em; }
h1 { max-width: 800px; margin: 12px 0; font-size: clamp(38px, 7vw, 72px); line-height: 1.05; letter-spacing: -.055em; }
.hero p { max-width: 720px; margin: 0 0 28px; color: #6b5a50; font-size: 18px; line-height: 1.75; }
.primary { display: inline-flex; padding: 13px 18px; border-radius: 12px; background: #c2410c; color: #fff; font-weight: 800; text-decoration: none; }
.notice { display: grid; grid-template-columns: 140px 1fr; gap: 18px; margin: 22px 0 42px; padding: 18px 22px; border: 1px solid #fde68a; border-radius: 16px; background: #fffbeb; line-height: 1.7; }
.notice strong { color: #92400e; }
.grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 22px; }
.process-card { position: relative; padding: 28px; border: 1px solid #fed7aa; border-radius: 22px; background: #fff; }
.number { display: inline-block; margin-bottom: 16px; color: #ea580c; font: 900 32px/1 monospace; }
h2 { margin: 0 0 16px; font-size: 25px; letter-spacing: -.02em; }
h3 { margin: 20px 0 8px; font-size: 15px; color: #9a3412; }
ol, ul { margin: 0; padding-left: 22px; color: #5f4b40; line-height: 1.9; }
.hint { margin-top: 16px; padding: 14px; border-radius: 12px; background: #fff7ed; color: #7c2d12; font-size: 13px; line-height: 1.7; }
.actions { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 18px; }
.actions a, .card-action { display: inline-flex; padding: 10px 14px; border: 1px solid #ea580c; border-radius: 10px; color: #c2410c; font-weight: 800; text-decoration: none; }
.workflow, .rules { margin-top: 52px; }
.section-heading { margin-bottom: 20px; }
.section-heading h2 { margin-top: 8px; font-size: 34px; }
.steps { display: grid; grid-template-columns: repeat(5, 1fr); overflow: hidden; border: 1px solid #fed7aa; border-radius: 20px; background: #fff; }
.steps div { display: grid; align-content: start; gap: 10px; min-height: 210px; padding: 22px; border-right: 1px solid #ffedd5; }
.steps div:last-child { border: 0; }
.steps b { color: #fb923c; font: 900 30px/1 monospace; }.steps span { color: #6b5a50; font-size: 14px; line-height: 1.6; }
.governance-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); margin-top: 24px; }
.governance-grid .process-card { display: flex; flex-direction: column; }
.governance-grid .process-card > p { color: #6b5a50; line-height: 1.7; }
.card-action { align-self: flex-start; margin-top: auto; }
.rules { padding: 30px; border-radius: 24px; background: #431d0c; color: #fff7ed; }
.rules-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px 30px; }.rules p { line-height: 1.75; }.rules strong { display: block; color: #fdba74; }
.legal-basis { margin-top: 24px; padding: 24px; border: 1px solid #fed7aa; border-radius: 20px; background: #fff; }.legal-basis p { color: #6b5a50; line-height: 1.8; }.legal-basis div { display: flex; flex-wrap: wrap; gap: 10px 18px; }.legal-basis a { color: #c2410c; font-weight: 800; }
footer { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 12px; margin-top: 36px; padding-top: 22px; border-top: 1px solid #fed7aa; color: #6b5a50; } footer a { color: #c2410c; }
@media (max-width: 900px) { .steps { grid-template-columns: 1fr; }.steps div { min-height: 0; border-right: 0; border-bottom: 1px solid #ffedd5; }.governance-grid { grid-template-columns: 1fr; } }
@media (max-width: 680px) { .support-header { align-items: flex-start; flex-direction: column; }.grid, .rules-grid { grid-template-columns: 1fr; }.notice { grid-template-columns: 1fr; }.process-card { padding: 22px; } }
</style>
