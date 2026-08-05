<template>
  <div class="publish-page">
    <PageTour storageKey="publish" :steps="tourSteps" />
    <!-- ═══════ 选择页：发布作品 or 发布需求 ═══════ -->
    <template v-if="!showForm">
      <div class="publish-studio">
        <div>
          <div class="compact-head">
            <div>
              <span class="eyebrow">发布工作台</span>
              <h2>先选你要挂出的内容，再进入对应表单。</h2>
              <p>发布页只做分流和准备提示。真正的字段在下一步表单里根据作品/需求类型动态展示，避免用户一进来就被复杂表单压住。</p>
            </div>
          </div>
          <div class="publish-choices">
            <!-- 发布作品 -->
            <article class="publish-choice work">
              <div>
                <div class="tag-row">
                  <span class="publish-tag orange">作品</span>
                  <span class="publish-tag green">展示与售卖</span>
                </div>
                <h3>发布 AI 产品作品</h3>
                <p>适合已经做出 demo、原型或可演示产品的创作者。重点是让别人快速看懂价值、完成度和适用场景。</p>
                <div class="work-reward">
                  <strong>审核通过奖励</strong>
                  <span>{{ workRewardText }}</span>
                </div>
                <div class="choice-preview">
                  <strong>下一步会填写</strong>
                  <span>作品封面</span><span>演示地址</span><span>视频展示</span><span>功能亮点</span>
                </div>
                <ul class="prepare-list">
                  <li><span class="check-dot">✓</span>产品标题、简介和封面</li>
                  <li><span class="check-dot">✓</span>演示链接、截图或视频</li>
                  <li><span class="check-dot">✓</span>功能亮点和适用场景</li>
                  <li><span class="check-dot">✓</span>当前完成度和注意事项</li>
                </ul>
              </div>
              <el-button type="primary" class="choice-btn" @click="startPublish('work')">开始发布作品</el-button>
            </article>
            <!-- 发布需求 -->
            <article class="publish-choice requirement">
              <div>
                <div class="tag-row">
                  <span class="publish-tag blue">需求</span>
                  <span class="publish-tag yellow">预算与定制</span>
                </div>
                <h3>发布定制需求</h3>
                <p>适合商家或用户提出目标，先给预算范围，再和创作者通过聊天确认需求范围和实现思路。</p>
                <div class="choice-preview">
                  <strong>下一步会填写</strong>
                  <span>需求目标</span><span>预算范围</span><span>参考样例</span><span>完成标准</span>
                </div>
                <ul class="prepare-list">
                  <li><span class="check-dot blue-dot">✓</span>想解决的问题</li>
                  <li><span class="check-dot blue-dot">✓</span>预算范围和期望周期</li>
                  <li><span class="check-dot blue-dot">✓</span>参考产品或截图</li>
                  <li><span class="check-dot blue-dot">✓</span>必须包含和暂不需要的内容</li>
                </ul>
              </div>
              <el-button class="choice-btn ghost-btn" @click="startPublish('requirement')">开始发布需求</el-button>
            </article>
          </div>
        </div>
        <aside class="publish-side">
          <div class="publish-note strong">
            <h4>审核流程</h4>
            <div class="publish-flow">
              <div class="flow-step">填写内容并保存草稿</div>
              <div class="flow-step">提交平台审核</div>
              <div class="flow-step">审核通过后公开展示</div>
              <div class="flow-step">被感兴趣后进入聊天沟通</div>
            </div>
          </div>
          <div class="publish-note">
            <h4>发布前检查</h4>
            <p>标题是否清楚、演示链接是否可打开、说明范围是否明确、是否包含敏感或侵权内容。</p>
          </div>
          <div class="publish-note">
            <h4>MVP 边界</h4>
            <p>当前只做作品展示、需求展示、审核、收藏、评论和聊天沟通。</p>
            <ul class="safe-list">
              <li>作品先说明功能、演示方式和当前完成度。</li>
              <li>需求先说明目标、预算范围和完成标准。</li>
              <li>审核状态、聊天状态和收藏状态先独立流转。</li>
            </ul>
          </div>
        </aside>
      </div>
    </template>

    <!-- ═══════ 表单页 ═══════ -->
    <template v-else>
      <button class="detail-back" @click="backToChoice">← 返回发布工作台</button>
      <div class="form-section">
        <div class="compact-head form-head">
          <div>
            <span class="eyebrow">{{ isEdit ? '编辑' : '发布' }}{{ tab==='work'?'作品':'需求' }}</span>
            <h2 v-if="!isEdit">{{ tab==='work' ? '发布你的 AI 产品作品' : '发布你的定制需求' }}</h2>
            <h2 v-else>编辑{{ tab==='work'?'作品':'需求' }}</h2>
          </div>
        </div>
        <div class="panel form-panel">
          <el-tabs v-model="tab" class="pub-tabs" @tab-change="onTabChange">
            <el-tab-pane label="作品" name="work" />
            <el-tab-pane label="需求" name="requirement" />
          </el-tabs>
          <el-form label-position="top">
            <el-form-item label="封面图"><el-upload :action="API+'/app/common/upload?dir=cover'" :headers="uploadHeaders" :on-success="onCoverSuccess" :show-file-list="false" accept="image/*"><el-button size="small">上传封面</el-button></el-upload><el-image v-if="form.coverUrl" :src="form.coverUrl" class="cover-preview" fit="cover"/></el-form-item>
            <el-form-item label="演示链接">
              <template v-if="tab==='work'">
                <el-collapse class="guide-collapse">
                  <el-collapse-item title="📘 如何生成演示链接？（点击展开）" name="guide">
                    <div class="guide-body">
                      <p class="guide-intro">把你的作品部署到免费云端，粘贴链接即可。源码始终在你手里，买家只能体验产品。</p>
                      <div class="guide-block guide-block-blue"><b>🔧 前端项目（HTML/Vue/React）</b><p class="guide-text">→ vercel.com → Import Git Repository → Deploy → 1-2分钟上线</p></div>
                      <div class="guide-block guide-block-orange"><b>🔧 Python / Streamlit</b><p class="guide-text">→ share.streamlit.io → New app → 填仓库路径 → Deploy</p></div>
                      <div class="guide-block guide-block-green"><b>🔧 后端 API / 全栈</b><p class="guide-text">→ railway.app 或 render.com → Deploy from GitHub</p></div>
                    </div>
                  </el-collapse-item>
                </el-collapse>
              </template>
              <el-input v-model="form.demoUrl" placeholder="https://你的作品演示地址（可选）" />
              <span class="field-hint">粘贴演示链接后，买家可在线体验你的作品，源码不可见</span>
            </el-form-item>
            <el-form-item v-if="tab==='work'" label="作品视频展示">
              <div class="video-upload-box">
                <el-upload
                  :action="API+'/app/common/upload/video'"
                  :headers="uploadHeaders"
                  :show-file-list="false"
                  :before-upload="beforeVideoUpload"
                  :on-progress="onVideoProgress"
                  :on-success="onVideoSuccess"
                  :on-error="onVideoError"
                  accept=".mp4,.mov,.m4v,.webm,video/mp4,video/quicktime,video/x-m4v,video/webm"
                >
                  <el-button type="primary" plain :loading="videoUploading">
                    {{ form.videoUrl ? '重新上传视频' : '选择视频文件' }}
                  </el-button>
                </el-upload>
                <el-button v-if="form.videoUrl" type="danger" text @click="removeVideo">移除视频</el-button>
              </div>
              <el-progress v-if="videoUploading" :percentage="videoProgress" :stroke-width="8" class="video-progress" />
              <span class="field-hint">支持 MP4、MOV、M4V、WebM，单个文件不超过 200MB；上传完成后可在下方预览</span>
              <div v-if="legacyVideoUrl && !form.videoUrl" class="legacy-video-tip">
                该作品原有的是外部视频链接。现在已改为文件上传，请重新选择视频；保存后旧链接会被移除。
              </div>
              <VideoShowcase v-if="form.videoUrl" :url="form.videoUrl" :poster="form.coverUrl" title="发布预览" compact class="publish-video-preview" />
            </el-form-item>
            <el-form-item label="标题" required><el-input v-model="form.title" placeholder="一句话描述你的作品/需求" /></el-form-item>
            <el-form-item label="简介"><el-input v-model="form.summary" type="textarea" :rows="2" placeholder="简短介绍" /></el-form-item>
            <el-form-item label="详情">
              <div class="detail-upload-row">
                <el-upload :action="API+'/app/common/upload?dir=detail'" :headers="uploadHeaders" :on-success="onDetailImage" :show-file-list="false" accept="image/*" multiple>
                  <el-button size="small">📷 插入图片</el-button>
                </el-upload>
                <span class="field-hint inline-hint">支持 HTML：&lt;img src=&quot;...&quot;&gt; 插入图片</span>
              </div>
              <el-input v-model="form.detail" type="textarea" :rows="6" placeholder="详细描述（支持HTML富文本）" />
            </el-form-item>
            <el-form-item label="分类"><el-select v-model="form.categoryId" placeholder="选择分类" clearable><el-option v-for="c in cats" :key="c.id" :label="c.name" :value="c.id" /></el-select></el-form-item>
            <template v-if="tab==='work'">
              <el-form-item label="参考价格">
                <el-input v-model="form.price" type="number" min="0" placeholder="¥ 留空则面议" />
                <div class="field-note">价格仅用于展示；在线支付功能即将开放。</div>
              </el-form-item>
            </template>
            <template v-else>
              <el-form-item label="预算范围">
                <el-input v-model="form.budgetMin" type="number" placeholder="最低" class="budget-input" /> —
                <el-input v-model="form.budgetMax" type="number" placeholder="最高" class="budget-input" />
              </el-form-item>
            </template>
            <div class="form-actions">
              <el-button
                @click="submit(false)"
                :loading="loading && saveAction === 'draft'"
                :disabled="loading && saveAction !== 'draft'"
              >保存草稿</el-button>
              <el-button
                type="primary"
                @click="submit(true)"
                :loading="loading && saveAction === 'review'"
                :disabled="loading && saveAction !== 'review'"
              >保存并提交审核</el-button>
            </div>
          </el-form>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { usePublish } from '../composables/usePublish'

const { PageTour, VideoShowcase, API, workRewardText, tourSteps, isEdit, showForm, tab, cats,
  loading, saveAction, form, videoUploading, videoProgress, legacyVideoUrl, uploadHeaders,
  onCoverSuccess, onDetailImage, beforeVideoUpload, onVideoProgress, onVideoSuccess, onVideoError,
  removeVideo, startPublish, backToChoice, onTabChange, submit } = usePublish()
</script>

<style scoped src="../styles/views/publish.css"></style>
