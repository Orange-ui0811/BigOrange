# 客服修复前后真实调用复测

相同八个合成画像；基线每个两轮，修复版各一轮。模型仍为 DeepSeek Flash，B 索引未更换。v4.5–4.8全阶段开启思考；v4.9聊天及条件理解保留思考，证据摘取与核验使用普通模式。此为开发集回归，不是独立上线验收。

|指标|修复前（16次）|最终v4.11（8次）|
|---|---:|---:|
|成功终态|13|8|
|要求澄清|2|0|
|有卡片的请求|11|6|
|卡片总数|33|17|
|有核验引用的卡片|0|13|
|核验引用条数|0|33|
|允许导入的卡片|0|4|
|平均秒数|16.608862|13.834366|
|最慢秒数|26.116937|17.133179|
|模型调用数（含重试）|32|20|
|失败的模型调用|19|0|
|输入 Token|56330|61498|
|输出 Token（包含思考）|41730|12288|
|模型费用估算（元）|0.184740|0.073771|
|含未知费用的请求|0|0|

估算沿用本次对比核实的周末 Flash 单价：缓存命中输入 0.02、未命中输入 1、输出 4 元/百万 Token（[官方定价](https://api-docs.deepseek.com/zh-cn/quick_start/pricing/)）。思考已包含在输出，不重复计费。不含未返回用量的 Embedding、基础设施成本；不是供应商账单。

## 最终版逐画像结果

|画像|终态|结果|卡片|带引用卡片|耗时秒|估算元|
|---|---|---|---:|---:|---:|---:|
|P01_spring_beginner|SUCCEEDED|PROJECTS_WITH_LIMITATIONS|2|2|14.47|0.01203784|
|P02_rag_career|SUCCEEDED|PROJECTS_WITH_LIMITATIONS|3|2|17.13|0.0093444|
|P03_rag_python|SUCCEEDED|PROJECTS_WITH_LIMITATIONS|3|2|15.52|0.0106734|
|P04_agent_multi|SUCCEEDED|VERIFIED_PROJECTS|3|3|12.90|0.0107574|
|P05_vue_no_docker|SUCCEEDED|PROJECTS_WITH_LIMITATIONS|3|2|11.38|0.010315399999999999|
|P06_data_career|SUCCEEDED|PROJECTS_WITH_LIMITATIONS|3|2|16.53|0.00931552|
|P07_custom_inventory|SUCCEEDED|NO_MATCH|0|0|8.79|0.00464684|
|P08_unavailable|SUCCEEDED|NO_MATCH|0|0|13.95|0.00667984|

## 最终版分阶段调用

|阶段|次数|失败|平均秒数|输入 Token|输出 Token|
|---|---:|---:|---:|---:|---:|
|ACTION|8|0|5.74|42306|9983|
|EVIDENCE|6|0|1.68|12478|2215|
|VERIFY|6|0|0.62|6714|90|

## 引用核对材料

### P01_spring_beginner

**JarGin-J/Earthquake-Warning** — NEEDS_VERIFICATION；可导入：False

- description：实时对接 Wolfx CENC 中国地震台网数据，多城市影响计算与 Bark 推送通知系统 [E3]
  原文：> 实时对接 Wolfx CENC 中国地震台网数据，多城市影响计算与 Bark 推送通知系统

[![Version](https://img.shields.io/badge/version-5.7.0-blue.svg)](https://github.com)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/lice
  定位：文档 538 / 版本 757 / 片段 17825

- prerequisites：技术栈：Java 17、Spring Boot 3.3.5、Java-WebSocket 1.5.7、OkHttp 4.12.0、Jackson 2.x、SpringDoc OpenAPI 2.6.0、Lombok 1.x，前端为原生 HTML/CSS/JS [E1]
  原文：| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 运行时环境 |
| Spring Boot | 3.3.5 | 应用框架 |
| Java-WebSocket | 1.5.7 | WebSocket 客户端 |
| OkHttp | 4.12.0 | HTTP 代理客户端 (SOCKS5) |
| Jackson | 2.x | JSON 处理 |
| SpringDoc OpenAPI | 2.6.0 | Swagger UI |
| Lombok | 1.x | 代码简化 |
| HTML/CSS/JS | - | 原生前端 (无框架依赖) |

---


  定位：文档 538 / 版本 757 / 片段 17829

- vibeCodingReason：快速开始：克隆项目、修改 application.yml 配置代理/Bark设备/监测城市，然后 ./mvnw spring-boot:run，访问 localhost:5050 控制台与 Swagger [E5]
  原文：```bash
# 1. 克隆项目
git clone <repo-url>
cd earthquake-warning

# 2. 修改配置文件
vim src/main/resources/application.yml
# 按需配置代理、Bark设备、监测城市等

# 3. 编译运行
./mvnw spring-boot:run

# 4. 访问
# 控制台: http://localhost:5050
# Swagger: http://localhost:5050/swagger-ui.html
```


  定位：文档 538 / 版本 757 / 片段 17837

**gematik/ref-idp-server** — NEEDS_VERIFICATION；可导入：False

- description：IDP-Global 由多个子项目组成：IDP-Server 中央IDP参考实现、IDP-Client 请求ACCESS_TOKEN的客户端、IDP-Testsuite 审批测试套件 [E6]
  原文：**IDP-Global** 项目由多个子项目组成。这些子项目包括：

* **IDP-Server:** 中央IDP的参考实现
* **IDP-Client:** 客户端，用于在中央IDP上使用SMC-B-Aut或HBA-Aut密钥请求ACCESS_TOKEN
* **IDP-Testsuite:** 针对中央IDP的审批测试套件，还包括针对Fast Track和联邦IDP的测试


  定位：文档 284 / 版本 503 / 片段 16245

- description：idp-server 的 URL 对服务器 discovery 文档中许多字段是必需的，按 JVM 参数、环境变量、Spring Boot 配置、预编译值的优先级确定 [E2]
  原文：idp-server的URL对于服务器discovery文档中的许多字段是必需的。例如，授权端点：

```
{
"authorization_endpoint": "https://server42/sign_response",
...
```

idp-server按以下优先级顺序确定URL（如果存在）：

1. jvm参数：--idp.serverUrl=https://myServerUrlAsJvmArgument.de
2. 环境变量：IDP_SERVER_URL=myServerUrlFromEnv:8080
3. spring boot配置（application.yml）：

```
idp:
   serverUrl: "https://urlPreConfiguredUrl"
```

在开发过程中，建议不要在application.yml中设置"serverUrl
  定位：文档 284 / 版本 503 / 片段 16254

### P02_rag_career

**pathwaycom/llm-app** — NEEDS_VERIFICATION；可导入：False

- description：提供可扩展至数百万页文档的AI应用模板，注重简洁或准确性，可直接使用或修改流水线步骤，如添加数据源或改混合索引只需改一行代码。 [E1]
  原文：本仓库提供的应用模板可扩展至 **数百万页文档**。有些模板注重简洁性，有些则注重惊人的准确性。请选择最适合您的一款。您可以直接使用，也可以修改流水线的某些步骤——例如，如果您想添加新数据源，或将向量索引改为混合索引，只需修改一行代码即可。

| 应用（模板）                                                                                   | 描述                                                                                                                                                     
  定位：文档 243 / 版本 462 / 片段 15991

- description：AI流水线支持高精度RAG和大规模AI企业搜索，提供开箱即用的LLM应用模板，可连接文件系统、Google Drive、S3、Kafka、PostgreSQL等数据源，内置向量、混合和全文搜索。 [E4]
  原文：Pathway 实时数据框架的 **AI 流水线** 让您能够快速将提供 **高精度 RAG 和大规模 AI 企业搜索** 的 AI 应用投入生产，并利用数据源中 **最新的知识**。它为您提供开箱即用的 **LLM（大语言模型）应用模板**。您可以在自己的机器上测试，并在云端（GCP、AWS、Azure、Render...）或本地部署。

这些应用会连接并同步（所有新增、删除、更新）来自 **文件系统、Google Drive、Sharepoint、S3、Kafka、PostgreSQL、实时数据 API** 的数据源。它们无需单独设置基础设施依赖。它们包含 **内置数据索引**，支持向量搜索、混合搜索和全文搜索——全部在内存中完成，并带有缓存。


  定位：文档 243 / 版本 462 / 片段 15990

**LearningCircuit/local-deep-research** — NEEDS_VERIFICATION；可导入：False

**HKUDS/DeepTutor** — NEEDS_VERIFICATION；可导入：False

- prerequisites：内置LightRAG引擎需通过pip install 'deeptutor[rag-lightrag]'安装，该extra包含支持的LightRAG SDK但不安装MinerU，MinerU需在文档解析中独立选择。 [E2]
  原文：The built-in LightRAG engine is installed with `pip install 'deeptutor[rag-lightrag]'`. That extra contains the supported LightRAG SDK but does not install MinerU. Choose MinerU independently in Document Parsing and either configure its cloud m
  定位：文档 171 / 版本 390 / 片段 14206

- description：每章编译为可编辑的类型化块，包括文本、标注、测验、闪卡、时间线、代码、图表、交互HTML、动画、概念图、深入探讨和用户笔记，并带有页面聊天功能。 [E5]
  原文：Each chapter compiles into editable typed blocks — text, callouts, quizzes, flash cards, timelines, code, figures, interactive HTML, animations, concept graphs, deep dives, and user notes — with its own Page Chat. Insert, move, regenerate, rewr
  定位：文档 171 / 版本 390 / 片段 14204

- description：用户可切换工具包括brainstorm、web_search、paper_search、reason和geogebra_analysis，配置匹配生成模型后还可使用imagegen和videogen；上下文工具如rag、kb_files等。 [E7]
  原文：User-toggleable tools are `brainstorm`, `web_search`, `paper_search`, `reason`, and `geogebra_analysis` — plus `imagegen` and `videogen` once you configure the matching generation model. Contextual tools such as `rag`, `kb_files`, `knowledge_fr
  定位：文档 171 / 版本 390 / 片段 14200

### P03_rag_python

**headroomlabs-ai/headroom** — NEEDS_VERIFICATION；可导入：False

**QuivrHQ/quivr** — NEEDS_VERIFICATION；可导入：False

- description：Quivr 提供自有 RAG，可与任何 LLM 配合，支持所有文件类型如 PDF、TXT、Markdown，并允许自定义 RAG、添加互联网搜索和集成工具。 [E4]
  原文：- **自有的 RAG**：我们创建了一个高效、快速且具有明确设计理念的 RAG，让你可以专注于产品开发。
- **大语言模型**：Quivr 可与任何 LLM 配合使用，包括 OpenAI、Anthropic、Mistral、Gemma 等。
- **任意文件**：Quivr 支持所有文件类型，如 PDF、TXT、Markdown 等，你甚至可以添加自己的解析器。
- **自定义 RAG**：Quivr 允许你自定义 RAG，添加互联网搜索、集成工具等。
- **与 Megaparse 集成**：Quivr 可与 [Megaparse](https://github.
  定位：文档 250 / 版本 469 / 片段 16031

- vibeCodingReason：Quivr 基础 RAG 工作流：将 API 密钥加入环境变量，支持 Anthropic、OpenAI、Mistral 的 API，也支持通过 Ollama 使用本地模型，然后创建 YAML 工作流文件。 [E1]
  原文：创建如上所示的基础 RAG 工作流很简单，步骤如下：

1. 将 API 密钥添加到环境变量中
```python
import os
os.environ["OPENAI_API_KEY"] = "myopenai_apikey"
```
Quivr 支持 Anthropic、OpenAI 和 Mistral 的 API，也支持通过 Ollama 使用本地模型。

2. 创建 YAML 文件 ``basic_rag_workflow.yaml`` 并将以下内容复制进去
```yaml
workflow_config:
  name: "standard RAG"
  
  定位：文档 250 / 版本 469 / 片段 16037

- vibeCodingReason：Quivr 快速开始：pip install quivr-core 安装包，然后用 5 行代码创建一个 RAG。 [E6]
  原文：- **步骤1**：安装软件包

  ```bash
  pip install quivr-core # Check that the installation worked
  ```

- **步骤2**：用5行代码创建一个 RAG

  ```python
  import tempfile

  from quivr_core import Brain

  if __name__ == "__main__":
      with tempfile.NamedTemporaryFile(mode="w", suffix=".txt") as temp_fil
  定位：文档 250 / 版本 469 / 片段 16034

**infiniflow/ragflow** — NEEDS_VERIFICATION；可导入：False

- description：RAGFlow 提供面向个人和大型企业的精简 RAG 编排，可配置 LLM 与嵌入模型，支持多路召回与融合重排，并提供直观 API 便于业务集成。 [E3]
  原文：- Streamlined RAG orchestration catered to both personal and large businesses.
- Configurable LLMs as well as embedding models.
- Multiple recall paired with fused re-ranking.
- Intuitive APIs for seamless integration with business.


  定位：文档 169 / 版本 388 / 片段 14113

- description：RAGFlow 是领先的开源 RAG 引擎，将前沿 RAG 与 Agent 能力融合，为 LLM 构建更优的上下文层，提供精简的 RAG 工作流。 [E5]
  原文：[RAGFlow](https://ragflow.io/) is a leading open-source Retrieval-Augmented Generation ([RAG](https://ragflow.io/basics/what-is-rag)) engine that fuses cutting-edge RAG with Agent capabilities to create a superior context layer for LLMs. It offers a streamlined RAG workflow adaptable to 
  定位：文档 169 / 版本 388 / 片段 14104

### P04_agent_multi

**olo-dot-io/Uni-CLI** — CONFIRMED；可导入：True

- description：TypeScript项目，功能模块化；涵盖多种接口的统一调用，适合学习命令行工具开发、进程管理和协议适配。 [E5]
  原文：TypeScript 项目，功能模块化；涵盖多种接口的统一调用，适合学习命令行工具开发、进程管理和协议适配。


  定位：文档 97 / 版本 316 / 片段 12072

- description：主题标签包括agent-infrastructure、ai-agents、automation、browser-automation、cdp、cli、mcp、mcp-server、self-repair、typescript等。 [E2]
  原文：["agent-infrastructure", "ai-agents", "ai-tools", "automation", "browser-automation", "cdp", "chrome", "claude-code", "cli", "codex", "cursor", "developer-tools", "mcp", "mcp-server", "nodejs", "openai", "self-repair", "typescript", "web-scraping", "yaml"]


  定位：文档 97 / 版本 316 / 片段 12075

**deer-flow/llm-space** — CONFIRMED；可导入：True

- description：涉及Agent原型设计模式、本地优先与应用状态管理、React+TypeScript桌面应用架构、Agent性能评估等讨论点。 [E1]
  原文：Agent 原型的设计模式；如何实现本地优先与应用状态管理；React + TypeScript 桌面应用的架构；如何评估 Agent 性能。


  定位：文档 113 / 版本 332 / 片段 12545

- prerequisites：技术栈：TypeScript，用Bun构建管理；桌面外壳Electrobun；UI用React+Tailwind CSS+shadcn/ui；Agent框架为Pi Agent Core。 [E4]
  原文：- **Language & tooling** — TypeScript, built and managed with [Bun](https://bun.com).
- **Desktop shell** — [Electrobun](https://electrobun.dev), a lightweight way to ship a native app.
- **UI** — React with Tailwind CSS and shadcn/ui.
- **Agent framework** — [Pi Agent Core](https://github.com/earendil-works/pi), a lightweight agent framework for building agents.


  定位：文档 113 / 版本 332 / 片段 12560

- vibeCodingReason：体现Agent开发与原型设计能力，适合AI应用与Agent或全栈后端岗位。 [E7]
  原文：体现 Agent 开发与原型设计能力，适合 AI 应用与 Agent 或全栈后端岗位。


  定位：文档 113 / 版本 332 / 片段 12543

**timmyy123/LLM-Hub** — CONFIRMED；可导入：True

- vibeCodingReason：通过调用不同LLM的接口构建统一工具，适合锻炼TypeScript开发与API集成能力。 [E3]
  原文：通过调用不同LLM的接口构建统一工具，适合锻炼TypeScript开发与API集成能力。


  定位：文档 88 / 版本 307 / 片段 11955

### P05_vue_no_docker

**JustArchiNET/ASF-WebConfigGenerator** — CONFIRMED；可导入：True

- description：一款基于Vue.js的ArchiSteamFarm配置文件在线生成器。 [E8]
  原文：一款基于Vue.js的ArchiSteamFarm配置文件在线生成器。


  定位：文档 61 / 版本 280 / 片段 10756

- vibeCodingReason：以表单驱动的配置生成工具，可练习Vue组件开发和数据配置逻辑，门槛较低。 [E6]
  原文：以表单驱动的配置生成工具，可练习Vue组件开发和数据配置逻辑，门槛较低。


  定位：文档 61 / 版本 280 / 片段 10757

- supportedExplanation：项目主题标签包含javascript、nodejs、vue、vuejs、webpack，属于JavaScript/Vue技术栈。 [E3]
  原文：["archisteamfarm", "css", "hacktoberfest", "html", "javascript", "nodejs", "vue", "vuejs", "webpack"]


  定位：文档 61 / 版本 280 / 片段 10760

**aidizhi/455555.xyz** — NEEDS_VERIFICATION；可导入：False

- description：技术栈：TypeScript核心开发、Vite开发服务器与生产构建、Jest单元测试、Canvas 2D API图表渲染、Material Design 3主题、npm/pnpm包管理。 [E1]
  原文：| 技术 | 用途 |
|:----:|:-----|
| TypeScript | 核心代码开发，类型安全 |
| Vite | 开发服务器与生产构建 |
| Jest | 单元测试 & 覆盖率 |
| Canvas 2D API | 图表渲染引擎 |
| Material Design 3 | 主题与视觉规范 |
| npm / pnpm | 包管理与发布 |

---


  定位：文档 537 / 版本 756 / 片段 17822

- description：路线图：v1.0核心图表类型（柱状图、折线图、饼图、散点图）、v1.1 Material Design主题系统、v1.2插件架构与Tooltip/Legend插件、v1.3性能优化；v2.0地图渲染器与WebGL加速层等尚未完成。 [E4]
  原文：- [x] v1.0 — 核心图表类型 (柱状图、折线图、饼图、散点图)
- [x] v1.1 — Material Design 主题系统
- [x] v1.2 — 插件架构 & Tooltip / Legend 插件
- [x] v1.3 — 性能优化 (虚拟化渲染、离屏 Canvas 缓存)
- [ ] v2.0 — 地图渲染器 (GeoJSON)
- [ ] v2.0 — WebGL 加速层 (大数据量场景)
- [ ] v2.1 — 服务端渲染 (SSR) 静态导出
- [ ] v2.2 — React / Vue 框架绑定层

---


  定位：文档 537 / 版本 756 / 片段 17823

- supportedExplanation：项目结构包含core图表核心、renderers渲染器、themes主题、plugins插件、utils工具、benchmarks性能基准、tests测试、docs文档等目录。 [E7]
  原文：```
455555.xyz/
├── src/
│   ├── core/            # 图表核心（Chart、Scale、Animation）
│   ├── renderers/       # 各图表类型渲染器
│   ├── themes/          # Material Design 主题定义
│   ├── plugins/         # 内置插件（Tooltip、Legend、Zoom）
│   ├── utils/           # 工具函数
│   └── index.ts         # 入口导出
├── benchmarks/          # 性能基准测试
├── tests/               # Jest 单元测试 & 集成测试
├── docs/                # API 文档与使用指南
├─
  定位：文档 537 / 版本 756 / 片段 17819

**apache/echarts** — NEEDS_VERIFICATION；可导入：False

### P06_data_career

**aidizhi/455555.xyz** — NEEDS_VERIFICATION；可导入：False

- description：该项目的技术栈包括 TypeScript 核心开发、Vite 构建、Jest 测试、Canvas 2D API 图表渲染引擎和 Material Design 3 主题规范。 [E1]
  原文：| 技术 | 用途 |
|:----:|:-----|
| TypeScript | 核心代码开发，类型安全 |
| Vite | 开发服务器与生产构建 |
| Jest | 单元测试 & 覆盖率 |
| Canvas 2D API | 图表渲染引擎 |
| Material Design 3 | 主题与视觉规范 |
| npm / pnpm | 包管理与发布 |

---


  定位：文档 537 / 版本 756 / 片段 17822

- prerequisites：安装方式支持 npm、pnpm、yarn；最低要求 Node.js >= 16 和现代浏览器（Chrome 90+、Firefox 88+、Safari 15+）。 [E4]
  原文：```bash
# npm
npm install dataviz-canvas

# pnpm（推荐）
pnpm add dataviz-canvas

# yarn
yarn add dataviz-canvas
```

> 最低要求：Node.js >= 16 · 现代浏览器（Chrome 90+、Firefox 88+、Safari 15+）

---


  定位：文档 537 / 版本 756 / 片段 17806

- vibeCodingReason：开发流程包括 pnpm install、pnpm dev 启动本地开发服务器、pnpm test 运行单元测试、pnpm build 构建生产产物等命令。 [E6]
  原文：```bash
# 克隆仓库
git clone https://github.com/aidizhi/455555.xyz.git
cd 455555.xyz

# 安装依赖
pnpm install

# 启动本地开发服务器（Vite + HMR）
pnpm dev

# 运行单元测试（Jest）
pnpm test

# 运行测试并生成覆盖率报告
pnpm test:coverage

# 构建生产产物
pnpm build

# 运行性能基准测试
pnpm bench

# 代码检查与格式化
pnpm lint && pnpm format
```

---


  定位：文档 537 / 版本 756 / 片段 17820

**bencbartlett/3D-printed-mirror-array** — NEEDS_VERIFICATION；可导入：False

**bytedance/deer-flow** — NEEDS_VERIFICATION；可导入：False

- description：DeerFlow 通过技能扩展能力，内置技能包括 Web 搜索、文件操作、代码执行和数据分析，可处理和分析结构化数据，并支持自定义技能。 [E2]
  原文：DeerFlow 通过**技能（Skills）**扩展其能力。技能是模块化的代理逻辑，可以动态加载和组合。内置技能包括：

- **Web 搜索**：使用多个搜索引擎和爬虫进行深度网络搜索。
- **文件操作**：读写文件、生成报告等。
- **代码执行**：在沙箱中执行 Python、JavaScript 等代码。
- **数据分析**：处理和分析结构化数据。

你可以轻松创建自定义技能并添加到代理中。


  定位：文档 233 / 版本 452 / 片段 15920

- vibeCodingReason：提供嵌入式 Python 客户端，可在 Python 应用中编程启动和管理代理，示例为分析 2025 年 AI 趋势并撰写报告。 [E5]
  原文：DeerFlow 提供了一个嵌入式 Python 客户端，允许你以编程方式在 Python 应用中启动和管理代理。

```python
from deerflow import Agent

agent = Agent(
    model="deepseek/deepseek-chat",
    config="config.yaml",
    sandbox=False
)

result = agent.run("分析 2025 年 AI 趋势并撰写报告")
print(result)
```


  定位：文档 233 / 版本 452 / 片段 15927

- supportedExplanation：DeerFlow 与多种模型兼容，推荐模型包括 DeepSeek v3.2、Kimi 2.5、GPT-4.1 等，可在 config.yaml 中指定模型。 [E7]
  原文：DeerFlow 与多种模型兼容，但以下模型在测试中表现最佳：

- **Doubao-Seed-2.0-Code**：字节跳动研发的代码生成模型，适合开发任务。
- **DeepSeek v3.2**：强大的通用模型，性价比高。
- **Kimi 2.5**：长上下文处理能力强，适合研究任务。
- **GPT-4.1**：OpenAI 的旗舰模型，综合能力出色。
- **Claude 4 Sonnet**：Anthropic 的最新模型，安全和 instruction-following 能力强。

你可以在 `config.yaml` 中指定模型，例如：

```yaml
model: deepseek/deepseek-chat
```


  定位：文档 233 / 版本 452 / 片段 15926

### P07_custom_inventory

### P08_unavailable
