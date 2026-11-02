# 29 条未恢复知识：原文与阻断点归因

所有结果来自本地保存原文、R5 旧响应及当前 V5 的真实 Java 规划；本轮未调用模型。
MODEL_TARGET 才能获得模型选择；仅出现在 context 不算被分类。扩大为 8 块仅是已有参数的离线对照，未修改默认配置。

| 分类 | 条数 |
|---|---:|
| BODY_CLASSIFIED_AS_POINTER | 2 |
| OLD_MODEL_REVIEW | 10 |
| DEPENDENCY_BLOCKED | 16 |
| INSTRUCTION_LIKE_SOURCE | 1 |

| 新版请求覆盖 | max4 | max8 |
|---|---:|---:|
| HARD_DEPENDENCY_COMPONENT | 5 | 5 |
| HARD_SOURCE_REVIEW | 1 | 1 |
| MODEL_TARGET | 14 | 20 |
| RULE_OR_PROJECTION_ONLY | 3 | 3 |
| UNIT_TARGET_LIMIT | 6 | 0 |

根因路径同时包含结构依赖和程序认定适用的 guard；这些边表示当前实现为何阻断，不是对许可或业务适用性的外部确认。

## Q2-240-36 · Mintplex-Labs/anything-llm · L36–36

主因：BODY_CLASSIFIED_AS_POINTER。旧模型：KEEP / SOURCE_POINTER；当前：KEEP / POINTER_METADATA_ONLY / MODEL_CLASSIFIED_SOURCE。
新版覆盖：max4=MODEL_TARGET；max8=MODEL_TARGET。

~~~~~~~text
👉 适用于桌面端的 AnythingLLM（Mac、Windows 和 Linux）！<a href="https://anythingllm.com/download" target="_blank">立即下载</a>
~~~~~~~

预期依据：桌面端 Mac Windows Linux 支持不可外推到服务器资源下限

处理方向：复核正文事实与导航的混合分类；SOURCE_POINTER 继续禁止直接回落为正文。

## Q2-244-18 · FlowiseAI/Flowise · L18–18

主因：OLD_MODEL_REVIEW。旧模型：REVIEW / PURPOSE；当前：REVIEW / REVIEW_QUEUE / SEMANTIC_REVIEW_NEEDED。
新版覆盖：max4=MODEL_TARGET；max8=MODEL_TARGET。

~~~~~~~text
<h3>可视化构建 AI 智能体</h3>
~~~~~~~

预期依据：可视化构建 AI 智能体是产品用途

处理方向：先确认新请求是否覆盖目标；实际 V5 判断只能通过新调用核验。

## Q2-244-64 · FlowiseAI/Flowise · L64–64

主因：OLD_MODEL_REVIEW。旧模型：REVIEW / USAGE；当前：REVIEW / REVIEW_QUEUE / SEMANTIC_REVIEW_NEEDED。
新版覆盖：max4=UNIT_TARGET_LIMIT；max8=MODEL_TARGET。

~~~~~~~text
1. 本地构建镜像：
~~~~~~~

预期依据：构建引导句与命令成组

处理方向：先确认新请求是否覆盖目标；实际 V5 判断只能通过新调用核验。

## Q2-244-66 · FlowiseAI/Flowise · L66–68

主因：DEPENDENCY_BLOCKED。旧模型：KEEP / USAGE；当前：REVIEW / REVIEW_QUEUE / DEPENDENCY_NOT_APPROVED。
新版覆盖：max4=UNIT_TARGET_LIMIT；max8=MODEL_TARGET。

~~~~~~~text
    ```bash
    docker build --no-cache -t flowise .
    ```
~~~~~~~

预期依据：镜像构建原样保留参数

根因节点：
- Q2-244-64 · L64 · SEMANTIC_REVIEW_NEEDED；max8=MODEL_TARGET。

## Q2-244-70 · FlowiseAI/Flowise · L70–70

主因：DEPENDENCY_BLOCKED。旧模型：KEEP / USAGE；当前：REVIEW / REVIEW_QUEUE / DEPENDENCY_NOT_APPROVED。
新版覆盖：max4=UNIT_TARGET_LIMIT；max8=MODEL_TARGET。

~~~~~~~text
2. 运行镜像：
~~~~~~~

预期依据：运行引导句与命令成组

根因节点：
- Q2-244-64 · L64 · SEMANTIC_REVIEW_NEEDED；max8=MODEL_TARGET。

## Q2-244-72 · FlowiseAI/Flowise · L72–74

主因：DEPENDENCY_BLOCKED。旧模型：KEEP / USAGE；当前：REVIEW / REVIEW_QUEUE / DEPENDENCY_NOT_APPROVED。
新版覆盖：max4=UNIT_TARGET_LIMIT；max8=MODEL_TARGET。

~~~~~~~text
    ```bash
    docker run -d --name flowise -p 3000:3000 flowise
    ```
~~~~~~~

预期依据：保留端口与镜像名字

根因节点：
- Q2-244-64 · L64 · SEMANTIC_REVIEW_NEEDED；max8=MODEL_TARGET。

## Q2-244-76 · FlowiseAI/Flowise · L76–76

主因：DEPENDENCY_BLOCKED。旧模型：KEEP / USAGE；当前：REVIEW / REVIEW_QUEUE / DEPENDENCY_NOT_APPROVED。
新版覆盖：max4=UNIT_TARGET_LIMIT；max8=MODEL_TARGET。

~~~~~~~text
3. 停止镜像：
~~~~~~~

预期依据：停止引导句与命令成组

根因节点：
- Q2-244-64 · L64 · SEMANTIC_REVIEW_NEEDED；max8=MODEL_TARGET。

## Q2-244-78 · FlowiseAI/Flowise · L78–80

主因：DEPENDENCY_BLOCKED。旧模型：KEEP / USAGE；当前：REVIEW / REVIEW_QUEUE / DEPENDENCY_NOT_APPROVED。
新版覆盖：max4=UNIT_TARGET_LIMIT；max8=MODEL_TARGET。

~~~~~~~text
    ```bash
    docker stop flowise
    ```
~~~~~~~

预期依据：停止命令不是服务启动命令

根因节点：
- Q2-244-64 · L64 · SEMANTIC_REVIEW_NEEDED；max8=MODEL_TARGET。

## Q2-244-139 · FlowiseAI/Flowise · L139–139

主因：OLD_MODEL_REVIEW。旧模型：REVIEW / USAGE；当前：REVIEW / REVIEW_QUEUE / STRUCTURE_REQUIRES_SEMANTIC_REVIEW。
新版覆盖：max4=HARD_DEPENDENCY_COMPONENT；max8=HARD_DEPENDENCY_COMPONENT。

~~~~~~~text
    然后运行：
~~~~~~~

预期依据：重建引导与下一命令一起

根因节点：
- Q2-244-141 · L141 · STRUCTURE_REQUIRES_SEMANTIC_REVIEW；max8=HARD_DEPENDENCY_COMPONENT。

## Q2-244-141 · FlowiseAI/Flowise · L141–143

主因：OLD_MODEL_REVIEW。旧模型：REVIEW / USAGE；当前：REVIEW / REVIEW_QUEUE / STRUCTURE_REQUIRES_SEMANTIC_REVIEW。
新版覆盖：max4=HARD_DEPENDENCY_COMPONENT；max8=HARD_DEPENDENCY_COMPONENT。

~~~~~~~text
    ```bash
    pnpm build
    ```
~~~~~~~

预期依据：重建命令需携带条件性内存处理上下文

根因节点：
- Q2-244-139 · L139 · STRUCTURE_REQUIRES_SEMANTIC_REVIEW；max8=HARD_DEPENDENCY_COMPONENT。

## Q2-244-147 · FlowiseAI/Flowise · L147–147

主因：OLD_MODEL_REVIEW。旧模型：REVIEW / USAGE；当前：REVIEW / REVIEW_QUEUE / SEMANTIC_REVIEW_NEEDED。
新版覆盖：max4=HARD_DEPENDENCY_COMPONENT；max8=HARD_DEPENDENCY_COMPONENT。

~~~~~~~text
5.  启动应用：
~~~~~~~

预期依据：启动引导与命令成组

根因节点：
- Q2-244-149 · L149 · STRUCTURE_REQUIRES_SEMANTIC_REVIEW；max8=HARD_DEPENDENCY_COMPONENT。

## Q2-244-149 · FlowiseAI/Flowise · L149–151

主因：OLD_MODEL_REVIEW。旧模型：REVIEW / USAGE；当前：REVIEW / REVIEW_QUEUE / STRUCTURE_REQUIRES_SEMANTIC_REVIEW。
新版覆盖：max4=HARD_DEPENDENCY_COMPONENT；max8=HARD_DEPENDENCY_COMPONENT。

~~~~~~~text
    ```bash
    pnpm start
    ```
~~~~~~~

预期依据：源码启动需保留安装和构建依赖

根因节点：
- af541e373f1f22c099c2909f0544576e7523841c786edf54e3062a5df0012220:3063-3078 · L145 · HTML_CONTEXT_REQUIRES_PARSE；max8=HARD_SOURCE_REVIEW。
- Q2-244-147 · L147 · SEMANTIC_REVIEW_NEEDED；max8=HARD_DEPENDENCY_COMPONENT。

## Q2-244-153 · FlowiseAI/Flowise · L153–153

主因：DEPENDENCY_BLOCKED。旧模型：KEEP / USAGE；当前：REVIEW / REVIEW_QUEUE / DEPENDENCY_NOT_APPROVED。
新版覆盖：max4=HARD_DEPENDENCY_COMPONENT；max8=HARD_DEPENDENCY_COMPONENT。

~~~~~~~text
    现在可以通过 [http://localhost:3000](http://localhost:3000) 访问应用
~~~~~~~

预期依据：访问地址是开发启动步骤的结果

根因节点：
- Q2-244-149 · L149 · STRUCTURE_REQUIRES_SEMANTIC_REVIEW；max8=HARD_DEPENDENCY_COMPONENT。

## Q2-248-20 · milvus-io/milvus · L20–20

主因：DEPENDENCY_BLOCKED。旧模型：无有效选择 / 无；当前：REVIEW / REVIEW_QUEUE / DEPENDENCY_NOT_ADMITTED。
新版覆盖：max4=RULE_OR_PROJECTION_ONLY；max8=RULE_OR_PROJECTION_ONLY。

~~~~~~~text
🐦 [Milvus](https://milvus.io/) 是一个为大规模扩展而构建的高性能向量数据库。它通过高效地组织和搜索海量非结构化数据（如文本、图像和多模态信息）来赋能 AI 应用。
~~~~~~~

预期依据：向量数据库定位不受同文另一个混合产品段落替代

根因节点：
- Q2-248-22 · L22 · MIXED_PRODUCT_REQUIRES_SPLIT；max8=HARD_SEMANTIC_REVIEW。

## Q2-248-24 · milvus-io/milvus · L24–24

主因：OLD_MODEL_REVIEW。旧模型：REVIEW / OTHER；当前：REVIEW / REVIEW_QUEUE / SEMANTIC_REVIEW_NEEDED。
新版覆盖：max4=MODEL_TARGET；max8=MODEL_TARGET。

~~~~~~~text
想零配置使用 Milvus？免费试用 [Zilliz Cloud ☁️](https://cloud.zilliz.com/signup?utm_source=partner&utm_medium=referral&utm_campaign=2024-11-04_web_github-readme_global)。Milvus 作为 Zilliz Cloud 上的全托管服务提供，可选择[无服务器](https://zilliz.com/serverless?utm_source=partner&utm_medium=referral&utm_campaign=2024-11-04_web_github-readme_global)、[专属](https://zilliz.com/cloud?utm_source=partner&utm_medium=referral&utm_campaign=2024-11-04_web_github-readme_global)和[自带云](https://zilliz.com/bring-your-own-cloud?utm_source=partner&utm_medium=referral&utm_campaign=2024-11-04_web_github-readme_global)选项。
~~~~~~~

预期依据：Zilliz Cloud 为托管产品，不属于开源单机必需条件；免费属于原文声明

根因节点：
- Q2-248-22 · L22 · MIXED_PRODUCT_REQUIRES_SPLIT；max8=HARD_SEMANTIC_REVIEW。

## Q2-248-28 · milvus-io/milvus · L28–28

主因：DEPENDENCY_BLOCKED。旧模型：无有效选择 / 无；当前：REVIEW / REVIEW_QUEUE / DEPENDENCY_NOT_ADMITTED。
新版覆盖：max4=RULE_OR_PROJECTION_ONLY；max8=RULE_OR_PROJECTION_ONLY。

~~~~~~~text
Milvus 开源项目隶属于 [LF AI & Data 基金会](https://lfaidata.foundation/projects/milvus/)，依据 [Apache 2.0](https://github.com/milvus-io/milvus/blob/master/LICENSE) 许可证分发，Zilliz 为其主要贡献者。
~~~~~~~

预期依据：Apache2.0 是正文声明，仅原样保留

根因节点：
- Q2-248-22 · L22 · MIXED_PRODUCT_REQUIRES_SPLIT；max8=HARD_SEMANTIC_REVIEW。

## Q2-248-32 · milvus-io/milvus · L32–34

主因：DEPENDENCY_BLOCKED。旧模型：无有效选择 / 无；当前：REVIEW / REVIEW_QUEUE / DEPENDENCY_NOT_ADMITTED。
新版覆盖：max4=RULE_OR_PROJECTION_ONLY；max8=RULE_OR_PROJECTION_ONLY。

~~~~~~~text
```python
$ pip install -U pymilvus
```
~~~~~~~

预期依据：pip 命令安装 Python SDK，不是 Milvus 服务本体

根因节点：
- Q2-248-22 · L22 · MIXED_PRODUCT_REQUIRES_SPLIT；max8=HARD_SEMANTIC_REVIEW。

## Q2-368-7 · lettier/3d-game-shaders-for-beginners · L7–23

主因：OLD_MODEL_REVIEW。旧模型：REVIEW / PURPOSE；当前：REVIEW / REVIEW_QUEUE / SEMANTIC_REVIEW_NEEDED。
新版覆盖：max4=MODEL_TARGET；max8=MODEL_TARGET。

~~~~~~~text
是否想在你的 3D 游戏中添加
纹理、
光照、
阴影、
法线贴图、
发光物体、
环境光遮蔽、
反射、
折射
等等特效？
太好了！
下面是一系列着色技术，将让你的游戏视觉效果更上一层楼。
我以这样的一种方式解释了每种技术，让你可以将所学内容应用/移植到
你所使用的任何技术栈中——无论是 Godot、Unity、Unreal 还是其他。
对于着色器之间的粘合部分，
我选择了出色的 Panda3D 游戏引擎和 OpenGL 着色语言（GLSL）。
因此，如果你恰好使用这些技术栈，你还能学到如何专门在 Panda3D 和 OpenGL 中使用这些着色技术。
~~~~~~~

预期依据：教学仓库含 Panda3D/GLSL 软件部分，可作源码学习，不能声称支持所有引擎直接部署

处理方向：先确认新请求是否覆盖目标；实际 V5 判断只能通过新调用核验。

## Q2-368-73 · lettier/3d-game-shaders-for-beginners · L73–73

主因：OLD_MODEL_REVIEW。旧模型：REVIEW / OTHER；当前：REVIEW / REVIEW_QUEUE / SEMANTIC_REVIEW_NEEDED。
新版覆盖：max4=MODEL_TARGET；max8=MODEL_TARGET。

~~~~~~~text
(C) 2019 David Lettier
~~~~~~~

预期依据：作者版权及软件许可适用范围共同保留

处理方向：先确认新请求是否覆盖目标；实际 V5 判断只能通过新调用核验。

## Q2-407-21 · open-webui/open-webui · L21–24

主因：BODY_CLASSIFIED_AS_POINTER。旧模型：KEEP / SOURCE_POINTER；当前：KEEP / POINTER_METADATA_ONLY / MODEL_CLASSIFIED_SOURCE。
新版覆盖：max4=MODEL_TARGET；max8=MODEL_TARGET。

~~~~~~~text
> [!TIP]  
> **正在寻找[企业版方案](https://docs.openwebui.com/enterprise)？** – **[立即与我们的销售团队洽谈！](https://docs.openwebui.com/enterprise)**
>
> 获得**增强功能**，包括**自定义主题和品牌标识**、**服务水平协议 (SLA) 支持**、**长期支持 (LTS) 版本**以及**更多！**
~~~~~~~

预期依据：主题品牌 SLA LTS 明确企业方案，不能作为社区默认能力

处理方向：复核正文事实与导航的混合分类；SOURCE_POINTER 继续禁止直接回落为正文。

## Q2-427-5 · n8n-io/n8n · L5–5

主因：DEPENDENCY_BLOCKED。旧模型：KEEP / PURPOSE；当前：REVIEW / REVIEW_QUEUE / DEPENDENCY_NOT_ADMITTED。
新版覆盖：max4=MODEL_TARGET；max8=MODEL_TARGET。

~~~~~~~text
这是一个公平代码（fair-code）平台，用于构建和部署 AI 智能体与工作流。通过可视化画布与自定义代码相结合，支持自托管或[云端](https://app.n8n.cloud/login)运行，并连接超过 1500 个集成。从原型到生产，为你提供可信赖的 AI 自动化，处理真实工作。
~~~~~~~

预期依据：AI 工作流平台用途，托管入口不能变成部署必需条件

根因节点：
- Q2-427-59 · L59 · MODEL_CLASSIFIED_SOURCE；max8=MODEL_TARGET。

## Q2-427-20 · n8n-io/n8n · L20–20

主因：DEPENDENCY_BLOCKED。旧模型：KEEP / USAGE；当前：REVIEW / REVIEW_QUEUE / DEPENDENCY_NOT_ADMITTED。
新版覆盖：max4=MODEL_TARGET；max8=MODEL_TARGET。

~~~~~~~text
使用 [npx](https://docs.n8n.io/hosting/installation/npm/) 立即试用 n8n（需要 [Node.js](https://nodejs.org/en/)）：
~~~~~~~

预期依据：Node.js 前置要求和 npx 命令一起保留，不补版本

根因节点：
- Q2-427-59 · L59 · MODEL_CLASSIFIED_SOURCE；max8=MODEL_TARGET。

## Q2-427-22 · n8n-io/n8n · L22–24

主因：DEPENDENCY_BLOCKED。旧模型：KEEP / USAGE；当前：REVIEW / REVIEW_QUEUE / DEPENDENCY_NOT_ADMITTED。
新版覆盖：max4=MODEL_TARGET；max8=MODEL_TARGET。

~~~~~~~text
```
npx n8n
```
~~~~~~~

预期依据：npx 试用命令需保留 Node.js 前提

根因节点：
- Q2-427-59 · L59 · MODEL_CLASSIFIED_SOURCE；max8=MODEL_TARGET。

## Q2-427-26 · n8n-io/n8n · L26–26

主因：DEPENDENCY_BLOCKED。旧模型：KEEP / USAGE；当前：REVIEW / REVIEW_QUEUE / DEPENDENCY_NOT_ADMITTED。
新版覆盖：max4=MODEL_TARGET；max8=MODEL_TARGET。

~~~~~~~text
或使用 [Docker](https://docs.n8n.io/hosting/installation/docker/) 部署：
~~~~~~~

预期依据：Docker 部署引导和命令共同保留

根因节点：
- Q2-427-59 · L59 · MODEL_CLASSIFIED_SOURCE；max8=MODEL_TARGET。

## Q2-427-28 · n8n-io/n8n · L28–31

主因：DEPENDENCY_BLOCKED。旧模型：KEEP / USAGE；当前：REVIEW / REVIEW_QUEUE / DEPENDENCY_NOT_ADMITTED。
新版覆盖：max4=MODEL_TARGET；max8=MODEL_TARGET。

~~~~~~~text
```
docker volume create n8n_data
docker run -it --rm --name n8n -p 5678:5678 -v n8n_data:/home/node/.n8n docker.n8n.io/n8nio/n8n
```
~~~~~~~

预期依据：持久卷映射端口和镜像参数不能丢失

根因节点：
- Q2-427-59 · L59 · MODEL_CLASSIFIED_SOURCE；max8=MODEL_TARGET。

## Q2-427-33 · n8n-io/n8n · L33–33

主因：DEPENDENCY_BLOCKED。旧模型：KEEP / USAGE；当前：REVIEW / REVIEW_QUEUE / DEPENDENCY_NOT_ADMITTED。
新版覆盖：max4=MODEL_TARGET；max8=MODEL_TARGET。

~~~~~~~text
访问编辑器：http://localhost:5678
~~~~~~~

预期依据：访问地址是运行步骤，不认定两个可选启动方式均必需

根因节点：
- Q2-427-59 · L59 · MODEL_CLASSIFIED_SOURCE；max8=MODEL_TARGET。

## Q2-427-57 · n8n-io/n8n · L57–57

主因：DEPENDENCY_BLOCKED。旧模型：KEEP / LICENSE；当前：REVIEW / REVIEW_QUEUE / DEPENDENCY_NOT_ADMITTED。
新版覆盖：max4=MODEL_TARGET；max8=MODEL_TARGET。

~~~~~~~text
[企业许可证](mailto:license@n8n.io) 提供额外功能和支持。
~~~~~~~

预期依据：额外功能支持明确企业许可，不能归为社区能力

根因节点：
- Q2-427-59 · L59 · MODEL_CLASSIFIED_SOURCE；max8=MODEL_TARGET。

## Q2-522-63 · appsmithorg/appsmith · L63–63

主因：OLD_MODEL_REVIEW。旧模型：REVIEW / OTHER；当前：REVIEW / REVIEW_QUEUE / SEMANTIC_REVIEW_NEEDED。
新版覆盖：max4=MODEL_TARGET；max8=MODEL_TARGET。

~~~~~~~text
Introducing a new agentic AI platform that integrates the latest AI models with private and proprietary data at scale — inside the tools and systems that teams use every day. Appsmith Agents expands generative AI’s capabilities for the millions of knowledge workers in sales, support, customer success, human resources, and other business teams. By giving continuous context to AI models, Appsmith Agents lets teams ask questions and configure automations specific to their business without model fine-tuning or complex RAG implementations. Visit [appsmith.com/ai](https://www.appsmith.com/ai) to learn more.
~~~~~~~

预期依据：Appsmith Agents 为具名相关产品，不并入 Appsmith 本体默认能力

处理方向：先确认新请求是否覆盖目标；实际 V5 判断只能通过新调用核验。

## Q2-546-39 · ByteBunny777/Docguard · L39–46

主因：INSTRUCTION_LIKE_SOURCE。旧模型：无有效选择 / 无；当前：REVIEW / REVIEW_QUEUE / INSTRUCTION_LIKE_SOURCE。
新版覆盖：max4=HARD_SOURCE_REVIEW；max8=HARD_SOURCE_REVIEW。

~~~~~~~text
| Category | What it catches |
|---|---|
| **Unicode tag smuggling** | Invisible Unicode "tag" characters (U+E0000–U+E007F) that silently encode ASCII text — a known technique for hiding instructions that render as nothing in any font but decode losslessly. docguard decodes the hidden payload and shows it to you. |
| **Invisible Unicode** | Zero-width spaces/joiners, word joiners, soft hyphens, and similar characters clustered inside otherwise normal text. |
| **Homoglyphs** | Words that mix Latin letters with visually-identical Cyrillic look-alikes (e.g. Cyrillic `а` standing in for Latin `a`) — used to sneak phrases past naive keyword filters. |
| **Hidden HTML/CSS** | `display:none`, `visibility:hidden`, `opacity:0`, `font-size:0`, the `hidden` attribute, and suspicious/long HTML comments — text that's invisible when rendered but present in the source an LLM or scraper reads. |
| **Suspicious phrases** | A curated list of known prompt-injection trigger phrases ("ignore previous instructions", "reveal your system prompt", etc.), matched case-insensitively. |
| **Encoded blobs** | base64-looking strings that actually decode to natural-language text, as opposed to hashes or IDs. |
~~~~~~~

预期依据：检测类别表必须保留启发式限制，表内攻击短语是数据

处理方向：核查是否为攻击样例说明；保持所有来源内容不可信且不可执行，不按仓库名放行。

