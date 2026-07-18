# AI 趋势分析增强 — 可执行任务计划

> 四维画像表单 + 平台模型 + 结构化输出 + 柱状图 | 与旧面板共存

---

## 0. 架构

```
用户填四维表单
    ↓
POST /app/trend/analyze { profile: {...}, question: "..." }
    ↓
TrendAnalysisService
    ├─ 读图谱数据 buildCompareGraph()
    ├─ 组装 prompt（System: 角色+输出格式+用户画像 / User: 14 Topic数据）
    ├─ 调 ai-trend 配置模型（不设超时）
    ├─ 解析 JSON → 兜底正则
    └─ 返回 { analysis, recommendations[], chartData, learning }
    ↓
前端渲染：领域卡片 + 项目推荐×3 + 柱状图 + 学习路径
```

---

## 1. 任务拆解

```
Step 1: 后端配置 ai-trend               ██      约 15 分钟
Step 2: 重写 TrendAnalysisService        █████   约 1 小时
Step 3: 前端四维表单 + 结果展示           ████    约 1.5 小时
Step 4: 柱状图                           ██      约 30 分钟
Step 5: 联调测试                          ██      约 30 分钟
─────────────────────────────────────────────────
总计                                约 4 小时
```

---

## Step 1：后端配置 `ai-trend`

### Step 1.1 — 新增配置块

**目标**：趋势分析有独立模型配置，和 `ai-audit` 互不干扰

**输入**：`application-dev.yml`、`application-prod.yml`

**新增**：
```yaml
orange:
  ai-trend:
    endpoint: ${AI_TREND_ENDPOINT:https://ai.gitee.com/v1}
    api-key: ${AI_TREND_KEY:}
    model: ${AI_TREND_MODEL:DeepSeek-V4-Pro}
```

**输出**：2 个 YAML 文件追加配置块

**验证**：`grep ai-trend application-dev.yml` 有输出

---

## Step 2：重写 TrendAnalysisService

### Step 2.1 — 新 API 端点

**目标**：新增 `/app/trend/analyze-v2`，接收用户画像

**输入**：无

**新增方法**：`AppTrendController.analyzeV2(@RequestBody TrendProfileDTO dto)`

**DTO 结构**：
```java
class TrendProfileDTO {
    List<String> topics;       // 技术方向（多选 + 自定义）
    String purpose;            // 目的动机（单选 + 自定义）
    String experience;         // 编程经验（单选 + 自定义）
    List<String> languages;    // 已掌握语言（多选 + 自定义）
    String timeCommitment;     // 时间投入（单选 + 自定义）
}
```

**输出**：1 个新 Controller 方法 + 1 个 DTO

---

### Step 2.2 — 重写 System Prompt

**目标**：AI 角色明确 + 输出格式严格 + 用户画像嵌入

**输入**：无

**新 System Prompt**：
```
你是开源项目推荐专家。基于用户画像和 GitHub 真实数据，给出个人化分析。

输出严格 JSON（不要 markdown 代码块，不要额外文字）：
{
  "analysis": "领域当前情况和市场分析（200字以内）",
  "recommendations": [
    {
      "name": "项目名",
      "description": "一句话介绍做什么",
      "difficulty": "入门/中等/进阶",
      "difficultyReason": "为什么是这个难度",
      "recommendationReason": "结合用户画像说明推荐原因",
      "prerequisites": "需要学习的前置知识",
      "references": ["参考开源项目1", "参考开源项目2"],
      "score": 85
    }
  ],
  "learning": "学习路径和预计出成果时间",
  "chartData": [
    { "label": "方向名", "score": 85 },
    { "label": "方向名", "score": 62 }
  ]
}

recommendations 1-3 个，score 0-100。分析越详细越好。
```

**输出**：System Prompt 常量

---

### Step 2.3 — 重写 analyze 逻辑

**目标**：合并用户画像 + 图谱数据 → 调 AI → 解析 → 返回

**改动**：`TrendAnalysisService.java`

**核心逻辑**：
```
1. 接收 TrendProfileDTO
2. 格式化用户画像为文本
3. 调用 buildCompareGraph() 获取 14 Topic 数据
4. 格式化数据摘要（复用现有 formatDataSummary）
5. 组装 User Prompt: 用户画像 + 数据摘要
6. POST ai-trend 模型（不设超时）
7. 解析 JSON → 正则兜底 → 返回
```

**Model 调用**：
- 读取 `orange.ai-trend.endpoint/api-key/model`
- RestTemplate 不设超时
- 不限制 max_tokens

**JSON 解析兜底**：
```
1. ObjectMapper 解析 JSON 
2. 正则提取 analysis/recommendations/learning
3. 失败 → 返回 { analysis: rawText, recommendations: [], learning: "" }
```

**旧方法保留**：`analyze(Map)` 不变，标记 `@Deprecated`

**输出**：修改 1 个 Service 类

---

## Step 3：前端

### Step 3.1 — 新增「智能分析」Tab

**目标**：趋势分析页新增 Tab，和洞察/图谱并列

**改动**：`TrendAnalysis.vue` → 新增 `<el-tab-pane label="🤖 智能分析" name="smart">`

**表单布局**（四卡片式）：

```
┌──────────────┐  ┌──────────────┐
│ 🎯 技术方向    │  │ 💡 目的动机    │
│ [多选下拉]    │  │ [单选下拉]    │
│ 自定义: ___   │  │ 自定义: ___   │
└──────────────┘  └──────────────┘
┌──────────────┐  ┌──────────────┐
│ 📚 技术基础    │  │ ⏰ 时间投入    │
│ 经验: [单选]  │  │ [单选下拉]    │
│ 语言: [多选]  │  │ 自定义: ___   │
│ 自定义: ___   │  │               │
└──────────────┘  └──────────────┘
        [开始分析] (el-button)
```

**状态管理**：
```js
const profile = reactive({
  topics: [], purpose: '', experience: '',
  languages: [], timeCommitment: '',
  customTopics: '', customPurpose: '', customExp: '', customLang: '', customTime: ''
})
const analysisResult = ref(null)
const chartData = ref([])
const analyzing = ref(false)
```

**输出**：修改 TrendAnalysis.vue，新增表单区域

---

### Step 3.2 — 结构化结果展示

**目标**：AI 返回后渲染三区卡片 + 柱状图

**布局**：

```
┌─────────────────────────────────────────────┐
│ 📊 领域分析                                  │
│ {{ analysisResult.analysis }}               │
├─────────────────────────────────────────────┤
│ 🏆 推荐项目                                  │
│ ┌──────────┐ ┌──────────┐ ┌──────────┐     │
│ │ 项目1     │ │ 项目2     │ │ 项目3     │     │
│ │ 难度:中等 │ │ 难度:入门 │ │ 难度:进阶 │     │
│ │ 评分:85  │ │ 评分:72  │ │ 评分:60  │     │
│ │ 理由:... │ │ 理由:... │ │ 理由:... │     │
│ │ 前置知识 │ │ 前置知识 │ │ 前置知识 │     │
│ │ 参考项目 │ │ 参考项目 │ │ 参考项目 │     │
│ └──────────┘ └──────────┘ └──────────┘     │
├─────────────────────────────────────────────┤
│ 📈 综合评分对比（柱状图）                     │
│ [ECharts bar chart]                         │
├─────────────────────────────────────────────┤
│ 📖 学习建议                                  │
│ {{ analysisResult.learning }}               │
└─────────────────────────────────────────────┘
```

**输出**：修改 TrendAnalysis.vue，新增结果区域

---

## Step 4：柱状图

### Step 4.1 — 图表渲染

**目标**：AI 返回 `chartData` 后渲染 ECharts 柱状图

**输入**：`chartData: [{label, score}]`

**图表配置**：
```js
{
  xAxis: { type: 'category', data: chartData.map(d => d.label) },
  yAxis: { type: 'value', max: 100, name: '综合评分' },
  series: [{ type: 'bar', data: chartData.map(d => d.score),
    itemStyle: { color: new echarts.graphic.LinearGradient(...) } }]
}
```

**空数据降级**：chartData 为空 → 不渲染图表区

**输出**：TrendAnalysis.vue 内图表区

---

## Step 5：联调测试

| # | 验证项 | 方式 |
|---|--------|------|
| 1 | 四维表单填写 + 校验 | 浏览器操作 |
| 2 | 点分析 → AI 返回结构化结果 | F12 Network 检查 |
| 3 | 领域分析卡片正常 | 肉眼 |
| 4 | 推荐项目卡片 1-3 个，6 字段齐全 | 肉眼 |
| 5 | 柱状图渲染 | 肉眼 |
| 6 | 学习建议显示 | 肉眼 |
| 7 | 模型调用不走用户 Key | 请求体无 apiKey |
| 8 | 图谱 Tab 正常 | 切换 Tab 查看 |
| 9 | 洞察 Tab 正常 | 切换 Tab 查看 |
| 10 | 旧 AI 面板仍可用 | 切换到旧 Tab 验证 |

---

## 2. 文件清单

| 层 | 新增 | 修改 |
|------|------|------|
| orange-server/config | — | `application-dev.yml`、`application-prod.yml` |
| orange-server/controller | — | `AppTrendController.java` |
| orange-server/service | — | `TrendAnalysisService.java` |
| orange-pojo/dto | `TrendProfileDTO.java` | — |
| orange-web | — | `TrendAnalysis.vue` |

**总计：新增 1 文件，修改 4 文件。**

---

## 3. 回滚

| 操作 | 回滚 |
|------|------|
| 新面板有问题 | 切回旧 Tab，新代码用 `v-if` 不加载 |
| AI 配置错 | 改 yml 重启 |
| 全部回滚 | `git revert` + 重新部署 |
