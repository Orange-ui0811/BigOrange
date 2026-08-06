<template>
  <div class="persona-workshop" v-loading="store.loading">
    <header class="workshop-header">
      <div>
        <span class="eyebrow">ORANGE PERSONA ATELIER · 画像由你确认</span>
        <h2>橙子画像工坊</h2>
        <p>把零散经历整理成一只会成长的原创橙子。系统只把行为放进素材箱；是否成为画像，始终由你决定。</p>
      </div>
      <div class="completion-card" aria-live="polite">
        <span>画像完整度</span>
        <strong>{{ snapshot.completion?.percent || 0 }}<small>%</small></strong>
        <div><i :style="{ width: `${snapshot.completion?.percent || 0}%` }"></i></div>
        <small>{{ snapshot.completion?.completedDimensions || 0 }} / {{ snapshot.completion?.totalDimensions || 7 }} 个维度</small>
      </div>
    </header>

    <div class="mobile-preview">
      <PersonaOrangeBadge :equipment="snapshot.equipment" :appearance="appearance" />
      <div><strong>你的橙子正在形成</strong><span>{{ unlockedCount }} 件装备已解锁 · {{ snapshot.materialCount || 0 }} 条素材待确认</span></div>
    </div>

    <div class="workshop-layout">
      <aside v-if="!compactMode" class="stage-column" aria-label="3D 橙子画像预览">
        <div class="stage-shell">
          <div class="stage-topline"><span>ORIGINAL 3D ORANGE · LIVE</span><span class="live-dot">实时预览</span></div>
          <OrangePersonaScene :equipment="snapshot.equipment" :appearance="appearance" :active-focus="activeFocus" @reset-view="activeFocus='overview'" />
          <div class="stage-caption">
            <div><strong>{{ focusMeta.title }}</strong><span>{{ focusMeta.caption }}</span></div>
            <span>拖动仅旋转视角 · 编辑均在右侧</span>
          </div>
          <nav class="focus-nav" aria-label="画像维度快捷导航">
            <button v-for="item in focusNavigation" :key="item.code" type="button" :class="{ active: activeFocus === item.code }" @click="scrollToFocus(item.code)">
              <span>{{ item.index }}</span>{{ item.short }}
            </button>
          </nav>
        </div>
      </aside>

      <main ref="storyRail" class="story-rail" tabindex="0" aria-label="画像内容与 DIY 设置，可独立滚动">
        <section id="persona-appearance" class="persona-node appearance-node" data-focus="overview">
          <div class="node-marker"><span>00</span><i></i></div>
          <div class="node-card appearance-card">
            <header class="node-heading">
              <div><span>ORANGE DIY · 实时预览</span><h3>先设计你的橙子</h3></div>
              <div class="appearance-status"><span aria-live="polite">{{ appearanceSaveState }}</span><el-tag type="warning" effect="plain">本体造型</el-tag></div>
            </header>
            <p class="node-intro">外观只是你的创作表达，不改变画像数据。修改颜色、表情、叶子和质感，左侧会实时更新。</p>
            <div class="appearance-grid">
              <fieldset class="diy-field diy-color-field">
                <legend>果皮颜色</legend>
                <div class="color-presets">
                  <button v-for="color in bodyColorPresets" :key="color" type="button" :class="{ active: appearance.bodyColor === color }" :style="{ '--swatch': color }" :aria-label="`选择果皮颜色 ${color}`" :aria-pressed="appearance.bodyColor === color" @click="setAppearance('bodyColor', color)"></button>
                  <label class="custom-color">自选<input v-model="snapshot.settings.bodyColor" type="color" aria-label="自定义果皮颜色" @change="scheduleAppearanceSave" /></label>
                </div>
              </fieldset>
              <fieldset v-for="control in appearanceControls" :key="control.key" class="diy-field">
                <legend>{{ control.label }}</legend>
                <div class="option-cards">
                  <button v-for="option in control.options" :key="option.value" type="button" :class="{ active: appearance[control.key] === option.value }" :aria-pressed="appearance[control.key] === option.value" @click="setAppearance(control.key, option.value)">
                    <span>{{ option.icon }}</span><strong>{{ option.label }}</strong>
                  </button>
                </div>
              </fieldset>
              <PersonaFaceEditor :model-value="faceConfig" @update:model-value="updateFaceConfig" @change="scheduleAppearanceSave" />
              <PersonaHandEditor :model-value="handConfig" @update:model-value="updateHandConfig" @change="scheduleAppearanceSave" />
            </div>
          </div>
        </section>
        <section class="persona-node share-node" data-focus="overview">
          <div class="node-marker"><span>SH</span><i></i></div>
          <PersonaAppearanceShareCard :completion="snapshot.completion" :visual-fingerprint="visualFingerprint" @imported="load" />
        </section>
        <section v-for="(dimension, index) in snapshot.dimensions" :id="`persona-${dimension.code.toLowerCase()}`" :key="dimension.code" class="persona-node" :data-focus="dimension.code.toLowerCase()">
          <div class="node-marker"><span>0{{ index + 1 }}</span><i></i></div>
          <div class="node-card">
            <header class="node-heading">
              <div><span>{{ dimensionMeta[dimension.code].kicker }}</span><h3>{{ dimension.label }}</h3></div>
              <el-tag :type="dimension.completed ? 'success' : 'info'" effect="plain">{{ dimension.completed ? '已形成' : '待完善' }}</el-tag>
            </header>
            <p class="node-intro">{{ dimensionMeta[dimension.code].description }}</p>
            <div class="entry-list" :aria-label="`${dimension.label}画像内容`">
              <article v-for="entry in dimension.entries" :key="entry.id" class="entry-chip">
                <span>{{ entry.content }}</span>
                <div>
                  <button type="button" :aria-label="`编辑${entry.content}`" @click="openEdit(entry)"><EditPen /></button>
                  <button type="button" :aria-label="`删除${entry.content}`" @click="removeEntry(entry)"><Delete /></button>
                </div>
              </article>
              <div v-if="!dimension.entries.length" class="entry-empty">{{ dimensionMeta[dimension.code].example }}</div>
            </div>
            <div class="entry-compose">
              <el-input v-model="drafts[dimension.code]" maxlength="500" show-word-limit :placeholder="dimensionMeta[dimension.code].placeholder" @keyup.enter="addEntry(dimension.code)" />
              <el-button type="primary" :disabled="!drafts[dimension.code]?.trim()" @click="addEntry(dimension.code)"><Plus />加入画像</el-button>
            </div>

            <div v-if="equipmentFor(dimension.code)" class="equipment-panel" :class="{ locked: !equipmentFor(dimension.code).unlocked }">
              <div class="equipment-copy">
                <span class="equipment-icon"><Box /></span>
                <div><strong>{{ equipmentFor(dimension.code).name }}</strong><p>{{ equipmentFor(dimension.code).visualMeaning }}</p><small v-if="equipmentFor(dimension.code).unlocked">DIY 饰品 · 选择款式、颜色和穿戴状态</small></div>
              </div>
              <div v-if="equipmentFor(dimension.code).unlocked" class="equipment-controls">
                <div class="equipment-variants" aria-label="DIY 饰品款式">
                  <button v-for="variant in equipmentFor(dimension.code).variants" :key="variant" type="button" :class="{ active: equipmentFor(dimension.code).variantCode === variant }" :aria-pressed="equipmentFor(dimension.code).variantCode === variant" @click="setEquipmentVariant(equipmentFor(dimension.code), variant)">{{ variantLabels[variant] || variant }}</button>
                </div>
                <label class="color-control">饰品颜色<input v-model="equipmentFor(dimension.code).colorHex" type="color" aria-label="装备颜色" @change="saveEquipment(equipmentFor(dimension.code))" /></label>
                <el-switch v-model="equipmentFor(dimension.code).equipped" inline-prompt active-text="穿戴" inactive-text="收起" @change="saveEquipment(equipmentFor(dimension.code))" />
                <PersonaEquipmentTransformEditor :model-value="equipmentFor(dimension.code).transform" @update:model-value="updateEquipmentTransform(equipmentFor(dimension.code), $event)" @change="saveEquipment(equipmentFor(dimension.code))" />
                <PersonaAccessoryBlueprintEditor :model-value="equipmentFor(dimension.code).blueprint" :slot-code="equipmentFor(dimension.code).slotCode" :variant-code="equipmentFor(dimension.code).variantCode" :title="equipmentFor(dimension.code).name" :default-color="equipmentFor(dimension.code).colorHex" @update:model-value="updateAccessoryBlueprint(equipmentFor(dimension.code), $event)" @change="saveAccessoryBlueprint(equipmentFor(dimension.code), $event)" />
              </div>
              <span v-else class="unlock-hint">先写下一条真实内容，即可解锁</span>
            </div>
          </div>
        </section>

        <section id="persona-materials" class="persona-node material-node" data-focus="growth">
          <div class="node-marker"><span>{{ String(snapshot.dimensions.length + 1).padStart(2, '0') }}</span><i></i></div>
          <div class="node-card">
            <header class="node-heading">
              <div><span>MATERIAL INBOX · 只收集，不擅自采用</span><h3>素材箱</h3></div>
              <el-badge :value="snapshot.materialCount || 0" :hidden="!snapshot.materialCount"><el-tag type="warning" effect="plain">待你确认</el-tag></el-badge>
            </header>
            <p class="node-intro">AI 工具、作品和后续平台行为会形成候选素材。接受前可以改写；忽略后不会进入画像，也不会解锁装备。</p>
            <div v-if="snapshot.materials?.length" class="material-list">
              <article v-for="material in snapshot.materials" :key="material.id" class="material-card">
                <div class="material-evidence"><span>{{ eventLabels[material.eventType] || '平台行为' }}</span><small>{{ material.evidenceSummary }}</small></div>
                <el-input v-model="materialDrafts[material.id]" maxlength="500" show-word-limit type="textarea" :rows="2" aria-label="确认前编辑画像素材" />
                <div class="material-actions"><el-button @click="rejectMaterial(material)"><Close />不用这条</el-button><el-button type="primary" @click="acceptMaterial(material)"><Check />确认加入画像</el-button></div>
              </article>
            </div>
            <el-empty v-else :image-size="72" description="素材箱是空的；继续使用平台，新的候选素材会出现在这里" />
          </div>
        </section>

        <section id="persona-recommendation" class="persona-node recommendation-node" data-focus="recommendation">
          <div class="node-marker"><span>{{ String(snapshot.dimensions.length + 2).padStart(2, '0') }}</span><i></i></div>
          <div class="node-card">
            <header class="node-heading"><div><span>NEXT BEST ACTION · 不消耗 AI 配额</span><h3>下一步推荐</h3></div><el-tag type="success" effect="plain">基于已确认画像</el-tag></header>
            <p class="node-intro">先用可解释规则连接现有平台内容，不为推荐额外调用模型。画像越完整，入口越贴近你的当前目标。</p>
            <div class="recommendation-grid">
              <button v-for="item in snapshot.recommendations" :key="item.title" type="button" @click="router.push(item.route)">
                <span>{{ item.type }}</span><strong>{{ item.title }}</strong><p>{{ item.description }}</p><ArrowRight />
              </button>
            </div>
          </div>
        </section>

        <section class="persona-node settings-node" data-focus="overview">
          <div class="node-marker"><span>{{ String(snapshot.dimensions.length + 3).padStart(2, '0') }}</span></div>
          <div class="node-card">
            <header class="node-heading"><div><span>ACCOUNT DISPLAY · 整体设置</span><h3>展示方式</h3></div></header>
            <div class="setting-row"><div><strong>在个人主页展示3D橙子与画像</strong><span>展示你的3D橙子、已确认画像和完成度，不展示素材箱内容</span></div><el-switch v-model="snapshot.settings.showcaseEnabled" @change="saveSettings" /></div>
          </div>
        </section>
      </main>
    </div>

    <el-dialog v-model="editVisible" title="编辑画像内容" width="min(520px, 92vw)" destroy-on-close>
      <el-form label-position="top"><el-form-item label="画像维度"><el-select v-model="editForm.dimension" style="width:100%"><el-option v-for="item in snapshot.dimensions" :key="item.code" :label="item.label" :value="item.code" /></el-select></el-form-item><el-form-item label="内容"><el-input v-model="editForm.content" type="textarea" :rows="4" maxlength="500" show-word-limit /></el-form-item></el-form>
      <template #footer><el-button @click="editVisible=false">取消</el-button><el-button type="primary" :disabled="!editForm.content.trim()" @click="saveEdit">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, defineAsyncComponent, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Box, Check, Close, Delete, EditPen, Plus } from '@element-plus/icons-vue'
import PersonaAccessoryBlueprintEditor from '../components/PersonaAccessoryBlueprintEditor.vue'
import PersonaEquipmentTransformEditor from '../components/PersonaEquipmentTransformEditor.vue'
import PersonaFaceEditor from '../components/PersonaFaceEditor.vue'
import PersonaHandEditor from '../components/PersonaHandEditor.vue'
import PersonaOrangeBadge from '../components/PersonaOrangeBadge.vue'
import PersonaAppearanceShareCard from '../components/PersonaAppearanceShareCard.vue'
import { usePersonaAppearance } from '../composables/usePersonaAppearance'
import { usePersonaEquipment } from '../composables/usePersonaEquipment'
import { usePersonaStoryRail } from '../composables/usePersonaStoryRail'
import { usePersonaWorkshop } from '../composables/usePersonaWorkshop'

const router = useRouter()
const OrangePersonaScene = defineAsyncComponent(() => import('../components/OrangePersonaScene.vue'))
const { storyRail, activeFocus, compactMode, focusMeta, observeNodes, scrollToFocus } = usePersonaStoryRail()
const workshop = usePersonaWorkshop({ afterLoad: observeNodes })
const { store, snapshot, editVisible, editForm, drafts, materialDrafts, load, addEntry,
  openEdit, saveEdit, removeEntry, acceptMaterial, rejectMaterial } = workshop
const { appearance, faceConfig, handConfig, appearanceSaveState, setAppearance, updateFaceConfig, updateHandConfig,
  scheduleAppearanceSave, saveSettings } = usePersonaAppearance(snapshot)
const { equipmentFor, saveEquipment, setEquipmentVariant, updateEquipmentTransform,
  updateAccessoryBlueprint, saveAccessoryBlueprint } = usePersonaEquipment({ snapshot, reload: load })

const dimensionMeta = {
  GOAL: { kicker: 'CURRENT DIRECTION', description: '你此刻最想推进什么。目标可以变化，记录的是当前选择，不是永久标签。', example: '例如：三个月内做出一个可投递前端岗位的完整作品', placeholder: '学习、求职、参赛、开源、接单……也可以写得更具体' },
  INTEREST: { kicker: 'ATTENTION ORBIT', description: '哪些主题会持续吸引你。它们形成兴趣轨道，不等于你已经掌握。', example: '例如：Agent、RAG、网站、游戏、硬件与人机交互', placeholder: '最近反复关注、愿意继续探索的方向' },
  SKILL: { kicker: 'PROVEN CRAFT', description: '你已经使用过的语言、工具和完成过的作品。尽量写可验证的事实。', example: '例如：用 Vue 做过两个网站，能独立部署到 Linux 服务器', placeholder: '语言、框架、工具、作品或真实经验' },
  RESOURCE: { kicker: 'AVAILABLE ENERGY', description: '能投入的时间、预算、设备和协作条件，用来避免生成不切实际的方案。', example: '例如：每周 6 小时、个人电脑、暂时没有团队、预算 200 元', placeholder: '时间、预算、设备、团队情况' },
  GROWTH: { kicker: 'CONFIRMED TRAIL', description: '你确认过的行动与里程碑。系统行为只能成为候选素材，不能自动写入这里。', example: '例如：完成第一次趋势分析，并按方案上线了一个项目', placeholder: '最近完成的行动、作品、比赛或开源贡献' },
  LEFT_HAND: { kicker: 'HARD EXCLUSIONS · LEFT HAND', description: '明确告诉系统哪些项目不要推荐。这里是硬性排除条件，不会被兴趣关键词覆盖。', example: '例如：不要纯算法项目、不接受付费 API、不做超过两周或必须组队的项目', placeholder: '不想要的技术、难度、成本、周期或协作方式' },
  RIGHT_HAND: { kicker: 'DESIRED OUTCOME · RIGHT HAND', description: '说明做完项目后希望得到什么，用于判断项目对你是否真正有价值。', example: '例如：得到一个可在线演示、能写进前端简历的完整作品', placeholder: '希望得到的作品、能力证明、比赛、开源或职业成果' },
}
const variantLabels = { CLASSIC: '经典', ORBIT: '环航', BEACON: '信标', SPARK: '火花', CONSTELLATION: '星座', ROUND: '圆框', WIDE: '宽屏', COMPACT: '轻量', TEAM: '协作', RINGS: '年轮', STEPS: '阶梯', SWORD: '剑', KNIFE: '刀', SHIELD: '盾牌', BLASTER: '能量枪' }
const eventLabels = { PROFILE_SKILLS: '账号资料', WORK_CREATED: '作品草稿', WORK_SUBMITTED: '作品投稿', WORK_APPROVED: '作品通过审核', AI_RESULT_COMPLETED: 'AI 工具记录' }
const focusNavigation = [
  { code: 'goal', index: '01', short: '目标' }, { code: 'interest', index: '02', short: '兴趣' },
  { code: 'skill', index: '03', short: '技能' }, { code: 'resource', index: '04', short: '资源' },
  { code: 'growth', index: '05', short: '成长' }, { code: 'left_hand', index: '06', short: '左手' },
  { code: 'right_hand', index: '07', short: '右手' }, { code: 'recommendation', index: '09', short: '下一步' },
]
const unlockedCount = computed(() => snapshot.equipment.filter(item => item.unlocked).length)
const visualFingerprint = computed(() => JSON.stringify({ appearance: appearance.value, equipment: snapshot.equipment }))
const bodyColorPresets = ['#F37021', '#FF9F1C', '#FF6B35', '#F43F5E', '#8B5CF6', '#0EA5E9']
const appearanceControls = [
  { key: 'faceStyle', label: '表情', options: [{ value: 'FRIENDLY', label: '友好', icon: '◡' }, { value: 'CURIOUS', label: '好奇', icon: '○' }, { value: 'COOL', label: '酷感', icon: '⌐' }] },
  { key: 'leafStyle', label: '叶子', options: [{ value: 'CLASSIC', label: '经典', icon: '◒' }, { value: 'DOUBLE', label: '双叶', icon: '∞' }, { value: 'SPROUT', label: '新芽', icon: '♧' }] },
  { key: 'surfaceStyle', label: '质感', options: [{ value: 'NATURAL', label: '自然', icon: '••' }, { value: 'GLOSSY', label: '亮面', icon: '✦' }, { value: 'MATTE', label: '磨砂', icon: '▪' }] },
]

onMounted(load)
</script>

<style scoped>
.persona-workshop{--atelier-ink:#24160f;--atelier-muted:#725f53;--atelier-line:#ead8c9;--atelier-paper:#fffdf9;max-width:1480px;margin:0 auto;color:var(--atelier-ink)}.workshop-header{display:grid;grid-template-columns:minmax(0,1fr) 210px;gap:32px;align-items:end;padding:22px 0 28px;border-bottom:1px solid var(--atelier-line)}.eyebrow{display:inline-flex;padding:7px 10px;border:1px solid #fdba74;border-radius:999px;background:#fff7ed;color:#9a3412;font:850 10px/1 var(--font-mono);letter-spacing:.08em}.workshop-header h2{margin:13px 0 8px;font:950 clamp(32px,5vw,54px)/1.02 var(--font-mono);letter-spacing:-.04em}.workshop-header p{max-width:760px;margin:0;color:var(--atelier-muted);font-size:14px;line-height:1.8}.completion-card{padding:17px 18px;border:1px solid var(--atelier-line);border-radius:14px;background:#fff;box-shadow:var(--shadow-card)}.completion-card>span,.completion-card>small{display:block;color:var(--atelier-muted);font-size:11px}.completion-card strong{display:block;margin:5px 0;color:#c2410c;font:900 34px/1 var(--font-mono)}.completion-card strong small{font-size:14px}.completion-card>div{height:6px;margin:10px 0 7px;overflow:hidden;border-radius:999px;background:#ffedd5}.completion-card i{display:block;height:100%;border-radius:inherit;background:linear-gradient(90deg,#fb923c,#ea580c);transition:width .35s ease}.workshop-layout{display:grid;grid-template-columns:minmax(460px,1.05fr) minmax(480px,.95fr);gap:30px;margin-top:28px}.stage-column{position:relative}.stage-shell{position:sticky;top:82px;height:calc(100vh - 106px);min-height:680px;padding:12px;border:1px solid #f3d8c2;border-radius:30px;background:#fff;box-shadow:0 26px 70px rgba(124,45,18,.12)}.stage-topline{position:absolute;z-index:2;top:29px;left:30px;right:30px;display:flex;align-items:center;justify-content:space-between;color:#9a3412;font:800 10px/1 var(--font-mono);letter-spacing:.08em;pointer-events:none}.live-dot{display:flex;align-items:center;gap:6px;color:#166534}.live-dot::before{width:7px;height:7px;border-radius:50%;background:#22c55e;box-shadow:0 0 0 5px rgba(34,197,94,.12);content:''}.stage-caption{position:absolute;z-index:2;left:32px;right:32px;bottom:83px;display:flex;align-items:end;justify-content:space-between;gap:18px;padding:12px 14px;border:1px solid rgba(255,255,255,.85);border-radius:12px;background:rgba(255,255,255,.7);backdrop-filter:blur(12px);pointer-events:none}.stage-caption div strong,.stage-caption div span{display:block}.stage-caption div strong{font:850 14px/1.3 var(--font-mono)}.stage-caption div span,.stage-caption>span{margin-top:4px;color:var(--atelier-muted);font-size:10px}.focus-nav{position:absolute;z-index:3;left:25px;right:25px;bottom:21px;display:grid;grid-template-columns:repeat(6,1fr);gap:5px}.focus-nav button{display:flex;min-width:0;height:38px;align-items:center;justify-content:center;gap:5px;border:1px solid #f0dfd1;border-radius:8px;background:rgba(255,255,255,.88);color:var(--atelier-muted);font-size:10px;font-weight:800;cursor:pointer}.focus-nav button span{color:#d97706;font:800 9px/1 var(--font-mono)}.focus-nav button.active,.focus-nav button:hover,.focus-nav button:focus-visible{border-color:#f37021;background:#fff7ed;color:#9a3412;outline:2px solid rgba(243,112,33,.16)}.story-rail{position:relative;padding:0 0 30vh}.story-rail::before{position:absolute;top:0;bottom:0;left:25px;width:1px;background:linear-gradient(#fb923c,#e9d5ff 24%,#bae6fd 42%,#a7f3d0 62%,#fed7aa 82%,transparent);content:''}.persona-node{position:relative;display:grid;grid-template-columns:52px minmax(0,1fr);min-height:72vh;align-content:center;padding:46px 0}.node-marker{position:relative;z-index:2;display:flex;align-items:center;flex-direction:column}.node-marker span{display:grid;width:40px;height:40px;place-items:center;border:1px solid #fdba74;border-radius:50%;background:#fff;color:#c2410c;font:850 10px/1 var(--font-mono);box-shadow:0 0 0 7px #fffdf9}.node-marker i{width:1px;flex:1;background:transparent}.node-card{padding:24px;border:1px solid var(--atelier-line);border-radius:18px;background:rgba(255,255,255,.96);box-shadow:0 18px 50px rgba(72,40,20,.06)}.node-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:20px}.node-heading>div>span{color:#b45309;font:800 9px/1.2 var(--font-mono);letter-spacing:.09em}.node-heading h3{margin:8px 0 0;font:900 25px/1.12 var(--font-mono);letter-spacing:-.025em}.node-intro{margin:14px 0 18px;color:var(--atelier-muted);font-size:13px;line-height:1.75}.entry-list{display:grid;gap:8px}.entry-chip{display:flex;min-height:48px;align-items:center;justify-content:space-between;gap:12px;padding:10px 11px 10px 14px;border:1px solid #f0dfd1;border-radius:10px;background:#fffaf5}.entry-chip>span{font-size:13px;line-height:1.55}.entry-chip>div{display:flex;flex-shrink:0;gap:3px}.entry-chip button{display:grid;width:32px;height:32px;place-items:center;border:0;border-radius:7px;background:transparent;color:#9a8070;cursor:pointer}.entry-chip button:hover,.entry-chip button:focus-visible{background:#ffedd5;color:#c2410c;outline:2px solid rgba(243,112,33,.18)}.entry-chip svg{width:14px}.entry-empty{padding:14px;border:1px dashed #dfc9b8;border-radius:10px;color:#927c6c;background:#fffdf9;font-size:12px;line-height:1.6}.entry-compose{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:8px;margin-top:12px}.entry-compose :deep(.el-button svg){width:15px;margin-right:5px}.equipment-panel{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:16px;align-items:center;margin-top:18px;padding:15px;border:1px solid #fed7aa;border-radius:12px;background:linear-gradient(110deg,#fff7ed,#fff)}.equipment-panel.locked{border-style:dashed;filter:saturate(.7)}.equipment-copy{display:flex;align-items:center;gap:12px}.equipment-icon{display:grid;width:38px;height:38px;flex-shrink:0;place-items:center;border-radius:10px;background:#ffedd5;color:#c2410c}.equipment-icon svg{width:18px}.equipment-copy strong,.equipment-copy p{display:block;margin:0}.equipment-copy strong{font:850 13px/1.3 var(--font-mono)}.equipment-copy p{margin-top:4px;color:var(--atelier-muted);font-size:10px;line-height:1.45}.equipment-controls{display:flex;align-items:center;gap:7px}.equipment-controls :deep(.el-select){width:92px}.color-control{display:flex;height:32px;align-items:center;gap:5px;padding:0 7px;border:1px solid #dcdfe6;border-radius:5px;color:#766357;font-size:10px}.color-control input{width:22px;height:22px;padding:0;border:0;background:transparent;cursor:pointer}.unlock-hint{color:#a16207;font-size:11px;font-weight:750}.material-node{min-height:82vh}.material-list{display:grid;gap:11px}.material-card{padding:15px;border:1px solid #fde68a;border-radius:12px;background:#fffdf4}.material-evidence{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:10px}.material-evidence span{padding:4px 7px;border-radius:5px;background:#fef3c7;color:#92400e;font-size:10px;font-weight:800}.material-evidence small{overflow:hidden;color:#8a7668;text-overflow:ellipsis;white-space:nowrap}.material-actions{display:flex;justify-content:flex-end;gap:7px;margin-top:10px}.material-actions :deep(svg){width:14px;margin-right:4px}.recommendation-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:9px}.recommendation-grid button{position:relative;display:flex;min-height:190px;align-items:flex-start;flex-direction:column;padding:15px;border:1px solid #d1fae5;border-radius:12px;background:#fbfffd;color:var(--atelier-ink);text-align:left;cursor:pointer}.recommendation-grid button:hover,.recommendation-grid button:focus-visible{border-color:#10b981;box-shadow:0 12px 30px rgba(5,150,105,.1);outline:2px solid rgba(16,185,129,.15);transform:translateY(-2px)}.recommendation-grid button>span{color:#047857;font:800 9px/1 var(--font-mono)}.recommendation-grid button strong{margin-top:20px;font:850 15px/1.35 var(--font-mono)}.recommendation-grid button p{margin:8px 0;color:var(--atelier-muted);font-size:11px;line-height:1.6}.recommendation-grid button svg{width:17px;margin-top:auto;color:#047857}.settings-node{min-height:55vh}.setting-row{display:flex;align-items:center;justify-content:space-between;gap:20px;padding:14px 0;border-bottom:1px solid #f1e6dd}.setting-row:last-child{border-bottom:0}.setting-row strong,.setting-row span{display:block}.setting-row strong{font-size:13px}.setting-row span{margin-top:4px;color:var(--atelier-muted);font-size:11px}.mobile-preview{display:none}
.stage-shell :deep(.scene-zoom){top:58px}
@media(max-width:1180px){.workshop-layout{grid-template-columns:minmax(400px,.9fr) minmax(440px,1.1fr)}.stage-shell{min-height:610px}.equipment-panel{grid-template-columns:1fr}.equipment-controls{justify-content:flex-start}.recommendation-grid{grid-template-columns:1fr}.recommendation-grid button{min-height:135px}}
.focus-nav{grid-template-columns:repeat(8,1fr)}
@media(max-width:900px){.workshop-header{grid-template-columns:1fr;padding-top:10px}.completion-card{display:none}.mobile-preview{display:flex;align-items:center;gap:15px;margin-top:18px;padding:14px;border:1px solid var(--atelier-line);border-radius:16px;background:#fff}.mobile-preview>div strong,.mobile-preview>div span{display:block}.mobile-preview>div strong{font:850 14px/1.3 var(--font-mono)}.mobile-preview>div span{margin-top:5px;color:var(--atelier-muted);font-size:11px}.workshop-layout{display:block;margin-top:0}.stage-column{display:none}.story-rail{padding-bottom:80px}.story-rail::before{left:19px}.persona-node{grid-template-columns:40px minmax(0,1fr);min-height:0;padding:24px 0}.node-marker span{width:34px;height:34px}.node-card{padding:18px}.entry-compose{grid-template-columns:1fr}.entry-compose :deep(.el-button){width:100%;margin:0}.equipment-controls{flex-wrap:wrap}.material-evidence{align-items:flex-start;flex-direction:column}.material-evidence small{white-space:normal}.setting-row{align-items:flex-start}}
@media(max-width:560px){.workshop-header h2{font-size:34px}.node-heading{align-items:flex-start;flex-direction:column;gap:9px}.node-heading h3{font-size:21px}.equipment-controls{display:grid;grid-template-columns:1fr auto}.equipment-controls :deep(.el-select){width:100%}.equipment-controls :deep(.el-switch){grid-column:1/-1}.material-actions{display:grid;grid-template-columns:1fr 1fr}.material-actions :deep(.el-button){margin:0}.recommendation-grid button{min-height:140px}}
@media(prefers-reduced-motion:reduce){.completion-card i,.recommendation-grid button{transition:none}.recommendation-grid button:hover{transform:none}}
.appearance-node{min-height:76vh}.appearance-card{border-color:#fdba74;background:linear-gradient(145deg,#fffdf9,#fff7ed)}.appearance-status{display:flex;align-items:center;gap:8px}.appearance-status>span{color:#15803d;font-size:10px;font-weight:800}.appearance-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}.diy-field{min-width:0;margin:0;padding:13px;border:1px solid #f1dfd1;border-radius:12px;background:rgba(255,255,255,.82)}.diy-field legend{padding:0 5px;color:#7c2d12;font:850 10px/1 var(--font-mono);letter-spacing:.04em}.diy-color-field{grid-column:1/-1}.color-presets{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.color-presets>button{width:31px;height:31px;border:3px solid #fff;border-radius:9px;background:var(--swatch);box-shadow:0 0 0 1px #e7d2c2;cursor:pointer}.color-presets>button.active,.color-presets>button:focus-visible{box-shadow:0 0 0 2px #9a3412;outline:none;transform:scale(1.08)}.custom-color{display:flex;height:32px;align-items:center;gap:7px;margin-left:auto;padding:0 7px;border:1px solid #e7d2c2;border-radius:8px;color:#725f53;background:#fff;font-size:10px}.custom-color input{width:23px;height:23px;padding:0;border:0;background:transparent;cursor:pointer}.option-cards{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:5px}.option-cards button{display:grid;min-width:0;min-height:58px;place-content:center;gap:4px;border:1px solid #ead8c9;border-radius:9px;background:#fff;color:#725f53;cursor:pointer}.option-cards button span{color:#c2410c;font:900 17px/1 var(--font-mono)}.option-cards button strong{font-size:10px}.option-cards button.active,.option-cards button:hover,.option-cards button:focus-visible{border-color:#f37021;background:#fff7ed;color:#9a3412;outline:2px solid rgba(243,112,33,.16)}.equipment-panel{grid-template-columns:1fr}.equipment-copy,.equipment-copy>div{min-width:0}.equipment-copy small{display:block;margin-top:6px;color:#c2410c;font-size:9px;font-weight:800}.equipment-variants{display:flex;justify-content:flex-start;gap:4px;flex-wrap:wrap}.equipment-variants button{min-height:30px;padding:0 9px;border:1px solid #ead8c9;border-radius:7px;background:#fff;color:#725f53;font-size:10px;font-weight:800;cursor:pointer}.equipment-variants button.active,.equipment-variants button:hover,.equipment-variants button:focus-visible{border-color:#f37021;background:#ffedd5;color:#9a3412;outline:none}.equipment-controls{max-width:none;align-items:center;flex-wrap:wrap;justify-content:flex-start}.equipment-controls .color-control{white-space:nowrap}
@media(min-width:901px){.persona-workshop{display:flex;height:calc(100dvh - 124px);max-height:calc(100dvh - 124px);flex-direction:column;overflow:hidden}.workshop-header{flex:0 0 auto;padding-top:4px;padding-bottom:18px}.workshop-layout{min-height:0;flex:1;overflow:hidden;margin-top:18px}.stage-column{height:100%;min-height:0}.stage-shell{position:relative;top:auto;height:100%;min-height:0}.story-rail{height:100%;min-height:0;overflow-y:auto;overscroll-behavior:contain;scrollbar-gutter:stable;padding:0 10px 24vh 0;scroll-behavior:smooth}.story-rail:focus-visible{outline:2px solid rgba(243,112,33,.28);outline-offset:3px}.persona-node{min-height:70vh}.story-rail::-webkit-scrollbar{width:10px}.story-rail::-webkit-scrollbar-track{background:#fff7ed;border-radius:999px}.story-rail::-webkit-scrollbar-thumb{border:3px solid #fff7ed;border-radius:999px;background:#fdba74}}
@media(max-width:900px){.appearance-node{min-height:0}.appearance-grid{grid-template-columns:1fr}.diy-color-field{grid-column:auto}.equipment-controls{max-width:none;align-items:flex-start}.equipment-variants{justify-content:flex-start}}
@media(max-width:560px){.option-cards{grid-template-columns:repeat(3,minmax(0,1fr))}.custom-color{margin-left:0}.equipment-controls{display:flex}.equipment-variants{width:100%}}
</style>
