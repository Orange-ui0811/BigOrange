# K1：38 份 README 代表性样本原文审计

这是当前数据库冻结正文的定向片段审计，不是 38 份 README 全文逐行标注，也不是自动过滤器的评测结果。
新增 20 份与首批 18 份不重复；所有标签均为助手审阅，尚未经用户人工确认。
开发集 24 份，预留验证集 14 份；验证片段已为标注而阅读，不能称为盲测集。
来源可能已经被旧流程翻译或截断；未访问 GitHub 重新核实。KEEP 不是事实已经得到运行验证。

原文行号从 1 开始；机器文件还记录 Python code point 与 Java UTF-16 半开区间。
规则见 [filter-policy.md](filter-policy.md)，结构化记录见 [audit-cases.json](audit-cases.json)。

## 46 · langchain-ai/langgraph

批次：initial18；用途：FRAMEWORK；集合：development。
选择原因：框架核心能力与周边产品边界、许可证徽章、安装代码。
文档版本：830；正文 SHA-256：`3a13e257af1216962d53d72414a7f44ac1fc5cea34d994bdff60ea2a3b045933`。
正文风险标记：未在已审计片段标记文档级风险。

### K1-046-01 · KEEP · L16–L16

类型：LICENSE；范围：SOURCE_POINTER；规则：R03。
保留 MIT 许可线索；徽章不是完整许可证正文。

后续去向：`POINTER_METADATA_CANDIDATE`。本轮未写入任何索引。

~~~~~~~~text
  <a href="https://opensource.org/licenses/MIT" target="_blank"><img src="https://img.shields.io/pypi/l/langgraph" alt="PyPI - License"></a>
~~~~~~~~

### K1-046-02 · DROP · L17–L17

类型：NOISE；范围：STATISTICS；规则：R01。
下载量徽章不作为功能和适配证据。

后续去向：`ARCHIVE_ONLY_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
  <a href="https://pypistats.org/packages/langgraph" target="_blank"><img src="https://img.shields.io/pepy/dt/langgraph" alt="PyPI - Downloads"></a>
~~~~~~~~

### K1-046-03 · KEEP · L26–L28

类型：DEPLOYMENT；范围：CORE；规则：R04。
完整保留 pip 安装命令。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
```bash
pip install -U langgraph
```
~~~~~~~~

### K1-046-04 · KEEP · L30–L33

类型：SOURCE_POINTER；范围：RELATED_PRODUCT；规则：R07。
Deep Agents 与 JavaScript 库是相关产品入口，不能归为 LangGraph 本体已提供的能力。

后续去向：`POINTER_METADATA_CANDIDATE`。本轮未写入任何索引。

~~~~~~~~text
> [!TIP]
> If you're looking to quickly build agents, check out **[Deep Agents](https://docs.langchain.com/oss/python/deepagents/overview)** — a higher-level package built on LangGraph for agents that can plan, use subagents, and leverage file systems for complex tasks.

For an equivalent JS/TS library, check out [LangGraph.js](https://github.com/langchain-ai/langgraphjs) and the [JS docs](https://docs.langchain.com/oss/javascript/langgraph/overview).
~~~~~~~~

### K1-046-05 · KEEP · L35–L41

类型：CAPABILITY；范围：CORE；规则：R02。
保留框架定位、持久执行、人机协作和记忆声明；这是文档声明，不是运行验证。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## Why use LangGraph?

LangGraph provides low-level supporting infrastructure for *any* long-running, stateful workflow or agent:

- **[Durable execution](https://docs.langchain.com/oss/python/langgraph/durable-execution)** — Build agents that persist through failures and can run for extended periods, automatically resuming from exactly where they left off.
- **[Human-in-the-loop](https://docs.langchain.com/oss/python/langgraph/interrupts)** — Seamlessly incorporate human oversight by inspecting and modifying agent state at any point during execution.
- **[Comprehensive memory](https://docs.langchain.com/oss/python/langgraph/memory)** — Create truly stateful agents with both short-term working memory for ongoing reasoning and long-term persistent memory across sessions.
~~~~~~~~

### K1-046-06 · KEEP · L42–L43

类型：CAPABILITY；范围：RELATED_PRODUCT；规则：R07。
LangSmith 调试和部署必须保留产品归属，不能自动归入开源框架自身。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
- **[Debugging with LangSmith](https://www.langchain.com/langsmith)** — Gain deep visibility into complex agent behavior with visualization tools that trace execution paths, capture state transitions, and provide detailed runtime metrics.
- **[Production-ready deployment](https://docs.langchain.com/langsmith/deployments)** — Deploy sophisticated agent systems confidently with scalable infrastructure designed to handle the unique challenges of stateful, long-running workflows.
~~~~~~~~

## 50 · EbookFoundation/free-programming-books

批次：initial18；用途：RESOURCE_COLLECTION；集合：development。
选择原因：学习资源仓库、贡献入口与装饰徽章区分。
文档版本：834；正文 SHA-256：`c571f60b7e798ef8c7cd32f9e59a7847d1f8b77a2cd0a9ae816bef4103f797c7`。
正文风险标记：未在已审计片段标记文档级风险。

### K1-050-01 · KEEP · L1–L1

类型：REPO_IDENTITY；范围：RESOURCE_COLLECTION；规则：R12。
学习资源集合不是可部署业务应用。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
# List of Free Learning Resources In Many Languages
~~~~~~~~

### K1-050-02 · KEEP · L6–L6

类型：LICENSE；范围：SOURCE_POINTER；规则：R03。
许可证徽章提供授权线索。

后续去向：`POINTER_METADATA_CANDIDATE`。本轮未写入任何索引。

~~~~~~~~text
[![License: CC BY 4.0](https://img.shields.io/github/license/EbookFoundation/free-programming-books)](https://creativecommons.org/licenses/by/4.0/)&#160;
~~~~~~~~

### K1-050-03 · DROP · L31–L32

类型：NOISE；范围：STATISTICS；规则：R01。
Fork 与 Star 统计徽章不进入适配证据。

后续去向：`ARCHIVE_ONLY_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
[![GitHub repo forks](https://img.shields.io/github/forks/EbookFoundation/free-programming-books?style=flat&logo=github&logoColor=whitesmoke&label=Forks)](https://github.com/EbookFoundation/free-programming-books/network)&#160;
[![GitHub repo stars](https://img.shields.io/github/stars/EbookFoundation/free-programming-books?style=flat&logo=github&logoColor=whitesmoke&label=Stars)](https://github.com/EbookFoundation/free-programming-books/stargazers)&#160;
~~~~~~~~

### K1-050-04 · KEEP · L43–L45

类型：SOURCE_POINTER；范围：STUDY_CONTRIBUTION；规则：R09。
贡献指南对学习和参与项目有用，不能按非功能文字全部删除。

后续去向：`POINTER_METADATA_CANDIDATE`。本轮未写入任何索引。

~~~~~~~~text
## How To Contribute

Please read [CONTRIBUTING](docs/CONTRIBUTING.md). If you're new to GitHub, [welcome](docs/HOWTO.md)! Remember to abide by our adapted from ![Contributor Covenant 1.3](https://img.shields.io/badge/Contributor%20Covenant-1.3-4baaaa.svg) [Code of Conduct](docs/CODE_OF_CONDUCT.md) too ([translations](#translations) also available).
~~~~~~~~

### K1-050-05 · KEEP · L74–L78

类型：SOURCE_POINTER；范围：RESOURCE_COLLECTION；规则：R09。
保留书单入口；链接不能证明对应书籍内容已经收录。

后续去向：`POINTER_METADATA_CANDIDATE`。本轮未写入任何索引。

~~~~~~~~text
### Books

[English, By Programming Language](books/free-programming-books-langs.md)

[English, By Subject](books/free-programming-books-subjects.md)
~~~~~~~~

### K1-050-06 · KEEP · L237–L239

类型：LICENSE；范围：CORE；规则：R03。
保留许可说明，不以默认开源许可替代。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## License

Each file included in this repository is licensed under the [CC BY License](LICENSE).
~~~~~~~~

## 228 · tidalcycles/strudel

批次：initial18；用途：APPLICATION；集合：development。
选择原因：极短正文仍含用途和迁移通知。
文档版本：1012；正文 SHA-256：`ddf07db52773d903a0d4734688feda7925f0d49bfc7b8dcad57a716439a2e21e`。
正文风险标记：SHORT_BUT_USEFUL。

### K1-228-01 · KEEP · L1–L4

类型：REPO_IDENTITY；范围：CORE；规则：R02。
短文仍包含浏览器实时编码工具定位和入口，不按长度直接丢弃。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
# strudel

Web 上的实时编码模式
https://strudel.cc/
~~~~~~~~

### K1-228-02 · KEEP · L6–L6

类型：MAINTENANCE；范围：CORE；规则：R08。
迁移到 Codeberg 影响后续推荐来源，必须保留。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
**开发已迁移至 https://codeberg.org/uzu/strudel**
~~~~~~~~

### K1-228-03 · DROP · L8–L8

类型：NOISE；范围：COURTESY；规则：R01。
迁移地址已保留后，单独的更新书签礼貌提示无需索引。

后续去向：`ARCHIVE_ONLY_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
请更新您的书签。
~~~~~~~~

## 268 · tteck/Proxmox

批次：initial18；用途：SCRIPT_COLLECTION；集合：development。
选择原因：混合 HTML 迁移通知与脚本安装风险。
文档版本：1052；正文 SHA-256：`cf8fe0df7dc0ee9d8354f946c2d00002c296af82b1e8db5e4a3bbf2d6ee6b27b`。
正文风险标记：未在已审计片段标记文档级风险。

### K1-268-01 · KEEP · L1–L1

类型：MAINTENANCE；范围：MIXED；规则：R11。
同一 HTML 行混有归档迁移地址和致谢，先保护完整迁移信息，不能整行按 HTML 删除。

后续去向：`PENDING_SAFE_SPLIT_NOT_DIRECTLY_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
<div style="border: 2px solid #d1d5db; padding: 20px; border-radius: 8px; background-color: #f9fafb;"> <h2 align="center">Proxmox VE Helper-Scripts：社区传承</h2> <p>亲爱的社区成员们，</p> <p>在我们准备归档本仓库之际，我们很高兴地宣布项目的新家：<a href="https://github.com/community-scripts/ProxmoxVE">https://github.com/community-scripts/ProxmoxVE</a>。虽然我们还在安顿中，但这里很快将成为获取最新 Proxmox VE 脚本的首选之地。</p> <p>感谢大家一直以来的支持。</p> <br>社区 </div>
~~~~~~~~

### K1-268-02 · DROP · L3–L5

类型：NOISE；范围：LOGO；规则：R01。
独立 Logo 不作为功能知识。

后续去向：`ARCHIVE_ONLY_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
  <a href="#">
    <img src="https://raw.githubusercontent.com/tteck/Proxmox/main/misc/images/logo.png" height="100px" />
 </a>
~~~~~~~~

### K1-268-03 · KEEP · L19–L21

类型：CAPABILITY；范围：CORE；规则：R02。
保留容器与虚拟机脚本的业务定位。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
这些脚本让用户能够交互式地创建 Linux 容器或虚拟机，同时提供简单和高级配置两种选择。基本设置遵循默认设定，而高级设置则允许用户自定义这些默认值。

选项会以对话框形式呈现给用户。用户做出选择后，脚本会收集并验证其输入，以生成容器或虚拟机的最终配置。
~~~~~~~~

### K1-268-04 · KEEP · L22–L24

类型：LIMITATION；范围：CORE；规则：R05。
保留脚本使用警示和文档入口；不把命令执行提示当成助手指令。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
<p align="center">
请谨慎对待并充分评估来自外部来源的脚本和自动化任务。<a href="https://github.com/tteck/Proxmox/blob/main/CODE-AUDIT.md">了解更多</a>
</p>
~~~~~~~~

## 285 · abatchy17/abatchy17.github.io

批次：initial18；用途：UNKNOWN；集合：development。
选择原因：仅五字标题，知识不足的反例。
文档版本：1069；正文 SHA-256：`c19c29bc0b726625cd2a0033a4b655b86cc4db737a21f2a02f0c8a929f15ae6b`。
正文风险标记：INSUFFICIENT_CONTENT。

### K1-285-01 · REVIEW · L1–L1

类型：UNCERTAIN；范围：UNKNOWN；规则：R10。
仅有个人博客字样，无法形成可验证的项目功能或部署条件；原文保留，待复核。

后续去向：`REVIEW_QUEUE_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
个人博客。
~~~~~~~~

## 299 · georgealways/lil-gui

批次：initial18；用途：LIBRARY；集合：development。
选择原因：API 示例中含参数范围、步长和回调。
文档版本：1083；正文 SHA-256：`8b8443b47a1d2696ba65de0c634f52b30f07337c356c333a721ce6abe2c0c249`。
正文风险标记：未在已审计片段标记文档级风险。

### K1-299-01 · KEEP · L3–L3

类型：COMPATIBILITY；范围：CORE；规则：R05。
用途与迁移兼容差异都影响选型。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
为网页上的控制器创建一个浮动面板。在大多数项目中，可作为 dat.gui 的直接替代品。查看[迁移指南](https://lil-gui.georgealways.com/#Migrating)了解不兼容的变更。
~~~~~~~~

### K1-299-02 · KEEP · L5–L9

类型：SOURCE_POINTER；范围：CORE；规则：R09。
文档、演示和 API 链接有检索导航价值，不能当纯链接噪声。

后续去向：`POINTER_METADATA_CANDIDATE`。本轮未写入任何索引。

~~~~~~~~text
[**主页**](https://lil-gui.georgealways.com/) •
[**基础演示**](https://lil-gui.georgealways.com/examples/basic/) •
[示例](https://lil-gui.georgealways.com/#Examples) •
[指南](https://lil-gui.georgealways.com/#Guide) •
[API](https://lil-gui.georgealways.com/#API) •
~~~~~~~~

### K1-299-03 · KEEP · L12–L53

类型：USAGE；范围：CORE；规则：R04。
保留完整示例及 addColor、onChange、数值步长上下文，不拆出失去对象关系的零散参数。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
```js
import GUI from 'lil-gui'; 

const gui = new GUI();

const myObject = {
	myBoolean: true,
	myFunction: function() { ... },
	myString: 'lil-gui',
	myNumber: 1
};

gui.add( myObject, 'myBoolean' );  // Checkbox
gui.add( myObject, 'myFunction' ); // Button
gui.add( myObject, 'myString' );   // Text Field
gui.add( myObject, 'myNumber' );   // Number Field

// Add sliders to number fields by passing min and max
gui.add( myObject, 'myNumber', 0, 1 );
gui.add( myObject, 'myNumber', 0, 100, 2 ); // snap to even numbers

// Create dropdowns by passing an array or object of named values
gui.add( myObject, 'myNumber', [ 0, 1, 2 ] );
gui.add( myObject, 'myNumber', { Label1: 0, Label2: 1, Label3: 2 } );

// Chainable methods
gui.add( myObject, 'myProperty' )
	.name( 'Custom Name' )
	.onChange( value => {
		console.log( value );
	} );

// Create color pickers for multiple color formats
const colorFormats = {
	string: '#ffffff',
	int: 0xffffff,
	object: { r: 1, g: 1, b: 1 },
	array: [ 1, 1, 1 ]
};

gui.addColor( colorFormats, 'string' );
```
~~~~~~~~

## 318 · tldraw/tldraw

批次：initial18；用途：SDK；集合：development。
选择原因：功能完整片段与尾部截断代码共存。
文档版本：1102；正文 SHA-256：`de3ada0cf62ca8d72ba797453807bd40c0e1356dd8952746bce98a0cc69bdd79`。
正文风险标记：INCOMPLETE_TAIL。

### K1-318-01 · DROP · L1–L6

类型：NOISE；范围：LOGO；规则：R01。
picture Logo 不作为能力证据。

后续去向：`ARCHIVE_ONLY_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
<div alt style="text-align: center; transform: scale(.25);">
	<picture>
		<source media="(prefers-color-scheme: dark)" srcset="https://github.com/tldraw/tldraw/raw/main/assets/github-hero-dark.png" />
		<img alt="tldraw" src="https://github.com/tldraw/tldraw/raw/main/assets/github-hero-light.png" />
	</picture>
</div>
~~~~~~~~

### K1-318-02 · KEEP · L23–L33

类型：CAPABILITY；范围：CORE；规则：R12。
这是画布 SDK 的能力，不能当作用户所需业务应用已经完成。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## 功能亮点

tldraw 提供了一个功能完备的无限画布引擎，旨在成为任何画布应用的基础。你可以创建自定义形状、工具、绑定和 UI 组件，以实现个性化体验。既可以使用默认的白板工具集，也可以利用库的底层原语构建全新的形状和交互。

- **多人协作** — 可自托管的实时协作，使用 [`@tldraw/sync`](https://tldraw.dev/docs/sync)
- **绘图与图表** — 压感绘图、几何形状、富文本、箭头、对齐吸附、边缘滚动、图像和视频支持、图像导出
- **运行时 API** — 通过编辑器 API 在运行时驱动画布
- **完全可扩展** — 自定义 [形状](https://tldraw.dev/docs/shapes)、[工具](https://tldraw.dev/docs/tools)、[绑定](https://tldraw.dev/sdk-features/bindings)、[UI 组件](https://tldraw.dev/sdk-features/ui-components)、副作用和事件钩子
- **AI 集成** — 用于 [结合 LLM 构建](https://tldraw.dev/docs/ai) 的画布原语
- **DOM 画布** — Web 渲染支持浏览器所支持的一切，包括来自 YouTube、Figma、GitHub 等 [嵌入式网站](https://tldraw.dev/sdk-features/embed-shape)
- **广泛支持** — 可在任何浏览器中运行，涵盖桌面、触摸屏、平板和移动设备
~~~~~~~~

### K1-318-03 · DROP · L35–L37

类型：NOISE；范围：CUSTOMER_LOGOS；规则：R01。
客户展示名单不作为适配依据。

后续去向：`ARCHIVE_ONLY_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## 谁在使用 tldraw

tldraw SDK 为众多产品的画布体验提供支持，包括 [Google](https://about.google)、[Shopify](https://www.shopify.com)、[BlackRock](https://www.blackrock.com)、[Autodesk](https://blogs.autodesk.com/forma/2024/06/20/forma-board-collaborative-tool-for-architects/)、[ClickUp](https://tldraw.dev/blog/clickup)、[Replit](https://docs.replit.com/replitai/canvas)、[Google Stitch](https://stitch.withgoogle.com)、[Luma](https://lumalabs.ai/app)、[Runway](https://runwayml.com)、[Padlet](https://tldraw.dev/blog/padlet)、[Mobbin](https://tldraw.dev/blog/mobbin)、[Jam](https://tldraw.dev/blog/jam)、[Craft](https://support.craft.do/en/write-and-edit/whiteboards)、[Honeycomb](https://www.honeycomb.io/platform/canvas)、[SchoolAI](https://schoolai.com/products/powerups)、[Brisk](https://www.briskteaching.com/post/boost-whiteboard)、[CADChat](https://cadchat.com)、[bigpi](https://www.bigpi.ai)、[Genio](https://genio.co)、[Pollination](https://www.pollination.solutions)、[Legendkeeper](https://www.legendkeeper.com)、[Matilda](https://www.matilda.io)、[Aries](https://aries.com/infinite)、[Alai](https://getalai.com)、[Dirac](https://www.diracinc.com) 等等。
~~~~~~~~

### K1-318-04 · KEEP · L39–L39

类型：SOURCE_POINTER；范围：STUDY_CONTRIBUTION；规则：R09。
案例展示入口与客户名单分开保留，供学习参考；不自动继承案例中的能力。

后续去向：`POINTER_METADATA_CANDIDATE`。本轮未写入任何索引。

~~~~~~~~text
请查看我们的 [案例展示](https://tldraw.dev/showcase)，了解团队如何基于 tldraw 进行构建。
~~~~~~~~

### K1-318-05 · KEEP · L41–L47

类型：DEPLOYMENT；范围：CORE；规则：R04。
保留已完整的安装代码。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## 快速开始

安装 tldraw 包：

```bash
npm i tldraw
```
~~~~~~~~

### K1-318-06 · REVIEW · L49–L54

类型：UNCERTAIN；范围：INCOMPLETE_CODE；规则：R10。
TSX 示例尾部截断，不能作为可执行完整示例；不连带删除此前完整说明。

后续去向：`REVIEW_QUEUE_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
然后，在你的 React 应用中使用 `<Tldraw />` 组件：

```tsx
import { Tldraw } from 'tldraw'
import 'tldraw/tldraw.
```
~~~~~~~~

## 329 · ToolJet/ToolJet

批次：initial18；用途：APPLICATION_BUILDER；集合：development。
选择原因：社区版、企业版 AI 功能边界及图片误推断。
文档版本：1113；正文 SHA-256：`8a5b7b040ffc9ccff78006e9f27de6837c2c2ee1ce4000ff242aa00c6a22a9f0`。
正文风险标记：INCOMPLETE_TAIL。

### K1-329-01 · KEEP · L1–L1

类型：REPO_IDENTITY；范围：CORE；规则：R12。
低代码构建器不等于已经完成库存管理系统。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
ToolJet 是 ToolJet AI 的开源基础——一个用于构建和部署内部工具、工作流和 AI 代理的 AI 原生平台。社区版提供了强大的可视化构建器、拖放式 UI，以及与数据库、API、SaaS 应用和对象存储的集成。有关 AI 驱动的 UI 生成、查询构建、调试和企业功能，请参阅 ToolJet AI。
~~~~~~~~

### K1-329-02 · DROP · L3–L3

类型：NOISE；范围：STAR_REQUEST；规则：R01。
求 Star 话术不进入检索证据。

后续去向：`ARCHIVE_ONLY_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
:star: 如果您觉得 ToolJet 有用，请考虑在 GitHub 上给我们一个 star！您的支持有助于我们持续创新并交付令人兴奋的功能。
~~~~~~~~

### K1-329-03 · KEEP · L13–L13

类型：LICENSE；范围：SOURCE_POINTER；规则：R03。
保留 AGPL 许可线索。

后续去向：`POINTER_METADATA_CANDIDATE`。本轮未写入任何索引。

~~~~~~~~text
[![GitHub 许可证（AGPL 许可证）](https://img.shields.io/github/license/ToolJet/ToolJet)](https://github.com/ToolJet/ToolJet)
~~~~~~~~

### K1-329-04 · REVIEW · L16–L18

类型：UNCERTAIN；范围：IMAGE_ONLY；规则：R10。
示例图片的库存管理 alt 不能独立证明仓库提供该现成功能。

后续去向：`REVIEW_QUEUE_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
<p align="center">
    <img src="docs/static/img/readme/banner.png" alt="ToolJet 仪表盘显示库存和订单"/>
</p>
~~~~~~~~

### K1-329-05 · KEEP · L26–L35

类型：CAPABILITY；范围：COMMUNITY；规则：R07。
保留社区版标题和能力范围。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
### 社区版 (CE)
- **可视化应用构建器：** 60+ 个响应式组件（表格、图表、表单、列表、进度条等）。
- **ToolJet 数据库：** 内置无代码数据库。
- **多页应用与多人协作编辑：** 协作构建复杂应用。
- **80+ 数据源：** 连接数据库、API、云存储和 SaaS 工具。
- **灵活部署：** 使用 Docker、Kubernetes、AWS、GCP、Azure 等进行自托管。
- **协作工具：** 内联评论、提及和细粒度访问控制。
- **可扩展性：** 使用 [ToolJet CLI](https://www.npmjs.com/package/@tooljet/cli) 创建插件和连接器。
- **任意位置编码：** 在应用内运行 JavaScript 和 Python。
- **安全设计：** AES-256-GCM 加密、仅代理数据流、SSO 支持。
~~~~~~~~

### K1-329-06 · KEEP · L37–L41

类型：CAPABILITY；范围：ENTERPRISE；规则：R07。
企业 AI 能力必须携带企业版限定，不能向免费社区版迁移。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
### ToolJet AI（企业版）
社区版所有功能，外加：
- **AI 应用生成：** 通过自然语言提示即时创建应用。
- **AI 查询构建器：** 借助 AI 辅助生成和转换查询。
- **AI 调试：** 一键识别并修复问题。
~~~~~~~~

### K1-329-07 · REVIEW · L54–L55

类型：UNCERTAIN；范围：INCOMPLETE_TEXT；规则：R10。
安装说明尾部不完整，待复核。

后续去向：`REVIEW_QUEUE_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## 快速入门
开始使用 ToolJet 的最简单方法是创建一个 [ToolJet Cloud](https://tooljet.com) 账户。ToolJet Cloud 提供 ToolJet 的托管解决方案。如果
~~~~~~~~

## 335 · alphacep/vosk-api

批次：initial18；用途：LIBRARY；集合：validation。
选择原因：短正文中离线能力、模型体积与语言绑定。
文档版本：1119；正文 SHA-256：`2ffaa7fcd366b0520820944f250dc05ec4d2e071760e9a1dcfe0955bfa0bf865`。
正文风险标记：SHORT_BUT_USEFUL。

### K1-335-01 · KEEP · L3–L3

类型：CAPABILITY；范围：CORE；规则：R02。
保留离线和语言支持声明，未来计划不能当作现有能力。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
Vosk 是一个离线开源语音识别工具包。它支持 20 多种语言和方言的语音识别——包括英语、印度英语、德语、法语、西班牙语、葡萄牙语、中文、俄语、土耳其语、越南语、意大利语、荷兰语、加泰罗尼亚语、阿拉伯语、希腊语、波斯语、菲律宾语、乌克兰语、哈萨克语、瑞典语、日语、世界语、印地语、捷克语、波兰语。还有更多语言即将支持。
~~~~~~~~

### K1-335-02 · KEEP · L5–L5

类型：DEPLOYMENT；范围：CORE；规则：R06。
原文写作 50 Mb，单位未核实；不擅自换为 MB，也不外推所有模型内存和运行资源。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
Vosk 模型体积小巧（50 Mb），但提供持续的大词汇量转录、通过流式 API 实现零延迟响应、可重新配置的词汇表以及说话人识别功能。
~~~~~~~~

### K1-335-03 · KEEP · L7–L7

类型：COMPATIBILITY；范围：CORE；规则：R02。
保留语言绑定支持。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
已为多种编程语言实现了语音识别绑定，例如 Python、Java、Node.JS、C#、C++、Rust、Go 等。
~~~~~~~~

### K1-335-04 · KEEP · L13–L15

类型：SOURCE_POINTER；范围：CORE；规则：R09。
仅表示存在文档入口，不表示已收录安装细节。

后续去向：`POINTER_METADATA_CANDIDATE`。本轮未写入任何索引。

~~~~~~~~text
# 文档

有关安装说明、示例和文档，请访问 [Vosk 网站](https://alphacephei.com/vosk)。
~~~~~~~~

## 354 · Donkie/Spoolman

批次：initial18；用途：APPLICATION；集合：development。
选择原因：打印耗材业务、数据库替代选项、安装指针。
文档版本：1138；正文 SHA-256：`46ed71b589a1a427680f72fde6ac2bb6f825711a1dd74b3c945958343ffda211`。
正文风险标记：未在已审计片段标记文档级风险。

### K1-354-01 · DROP · L1–L5

类型：NOISE；范围：LOGO；规则：R01。
独立 Logo 不进入功能知识。

后续去向：`ARCHIVE_ONLY_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://github.com/Donkie/Spoolman/assets/2332094/4e6e80ac-c7be-4ad2-9a33-dedc1b5ba30e">
  <source media="(prefers-color-scheme: light)" srcset="https://github.com/Donkie/Spoolman/assets/2332094/3c120b3a-1422-42f6-a16b-8d5a07c33000">
  <img alt="耗材线轴图标" src="https://github.com/Donkie/Spoolman/assets/2332094/3c120b3a-1422-42f6-a16b-8d5a07c33000">
</picture>
~~~~~~~~

### K1-354-02 · KEEP · L9–L11

类型：REPO_IDENTITY；范围：CORE；规则：R12。
耗材管理场景明确，不能扩写为通用库存 ERP。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
_追踪你的 3D 打印机耗材线轴库存。_

Spoolman 是一个自托管的 web 服务，旨在帮助你高效管理 3D 打印机耗材线轴并监控其使用情况。它作为一个集中式数据库，与流行的 3D 打印软件如 [OctoPrint](https://octoprint.org/) 和 [Klipper](https://www.klipper3d.org/)/[Moonraker](https://moonraker.readthedocs.io/en/latest/) 无缝集成。连接后，它会在打印过程中自动更新线轴重量，为你提供耗材使用情况的实时洞察。
~~~~~~~~

### K1-354-03 · KEEP · L16–L28

类型：CAPABILITY；范围：CORE；规则：R06。
保留功能、REST API 和数据库选项；多种数据库是可选实现，不是全部必装。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
### 功能
* **耗材管理**：全面记录耗材类型、制造商和单个线轴的信息。
* **API 集成**：提供了 [REST API](https://donkie.github.io/Spoolman/)，方便与其他软件集成，实现自动化工作流程和数据交换。
* **实时更新**：通过 WebSocket 获得线轴信息的实时更新，在打印过程中立即反馈。
* **中央耗材数据库**：社区维护的制造商和耗材数据库，简化了向库存中添加新线轴的操作。前往 [SpoolmanDB](https://github.com/Donkie/SpoolmanDB) 参与贡献。
* **基于 Web 的客户端**：Spoolman 内置了 web 客户端，让你轻松管理数据：
  * 查看、创建、编辑和删除耗材数据。
  * 添加自定义字段，根据你的特定需求定制信息。
  * 打印带有二维码的标签，便于线轴识别和追踪。
  * 通过 [Weblate](https://hosted.weblate.org/projects/spoolman/) 参与将其翻译成 18 种语言。
* **数据库支持**：SQLite、PostgreSQL、MySQL 和 CockroachDB。
* **多打印机管理**：同时处理来自多台打印机的线轴更新。
* **高级监控**：与 [Prometheus](https://prometheus.io/) 集成，对耗材使用情况进行详细的历史分析，帮助你追踪和优化打印流程。设置方法请参阅 [Wiki](https://github.com/Donkie/Spoolman/wiki/Filament-Usage-History)。
~~~~~~~~

### K1-354-04 · REVIEW · L36–L37

类型：UNCERTAIN；范围：IMAGE_ONLY；规则：R10。
未读取图片内容，不能从图片 URL 推断功能。

后续去向：`REVIEW_QUEUE_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
**Web 客户端预览：**
![image](https://github.com/Donkie/Spoolman/assets/2332094/33928d5e-440f-4445-aca9-456c4370ad0d)
~~~~~~~~

### K1-354-05 · KEEP · L39–L40

类型：SOURCE_POINTER；范围：CORE；规则：R09。
安装文档链接可保留，不能据此编造 Docker、内存或部署步骤。

后续去向：`POINTER_METADATA_CANDIDATE`。本轮未写入任何索引。

~~~~~~~~text
## 安装
有关如何安装 Spoolman 的详细信息，请参阅 [Wiki 上的安装页面](https://github.com/Donkie/Spoolman/wiki/Installation)。
~~~~~~~~

## 357 · Slicer/Slicer

批次：initial18；用途：APPLICATION；集合：development。
选择原因：科研软件、DOI 和安全徽章、许可链接。
文档版本：1141；正文 SHA-256：`85b4bcaec946ebf22e76da5be7fc747f0cee737ce579410fd18a06ba71446170`。
正文风险标记：未在已审计片段标记文档级风险。

### K1-357-01 · KEEP · L1–L1

类型：SOURCE_POINTER；范围：PUBLICATION；规则：R09。
DOI 是可追溯学术来源入口。

后续去向：`POINTER_METADATA_CANDIDATE`。本轮未写入任何索引。

~~~~~~~~text
[![DOI: 10.1016/j.mri.2012.05.001](https://img.shields.io/badge/DOI-10.1016/j.mri.2012.05.001-blue.svg)](https://pubmed.ncbi.nlm.nih.gov/22770690/)
~~~~~~~~

### K1-357-02 · DROP · L3–L3

类型：NOISE；范围：CI_BADGE；规则：R01。
动态 CI 状态不证明冻结版本已经通过验证。

后续去向：`ARCHIVE_ONLY_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
[![CI（构建）](https://github.com/Slicer/Slicer/actions/workflows/ci.yml/badge.svg)](https://github.com/Slicer/Slicer/actions/workflows/ci.yml)
~~~~~~~~

### K1-357-03 · KEEP · L4–L4

类型：SOURCE_POINTER；范围：SECURITY；规则：R09。
安全评估徽章只保留为链接，不能声明项目已安全认证。

后续去向：`POINTER_METADATA_CANDIDATE`。本轮未写入任何索引。

~~~~~~~~text
[![OpenSSF 记分卡](https://api.securityscorecards.dev/projects/github.com/Slicer/Slicer/badge)](https://securityscorecards.dev/viewer/?uri=github.com/Slicer/Slicer)
~~~~~~~~

### K1-357-04 · KEEP · L6–L8

类型：CAPABILITY；范围：CORE；规则：R02。
保留可视化、图像分析与多平台定位，不从项目名补充当前片段未写的领域限制。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
Slicer（或称 3D Slicer）是一款免费、开源的软件包，用于可视化与图像分析。

3D Slicer 原生设计支持多平台，包括 Windows、Linux 和 macOS。
~~~~~~~~

### K1-357-05 · KEEP · L10–L11

类型：SOURCE_POINTER；范围：CORE；规则：R09。
源码构建文档入口对二次开发有用。

后续去向：`POINTER_METADATA_CANDIDATE`。本轮未写入任何索引。

~~~~~~~~text
所有平台的构建说明均可在 Slicer 维基上找到：
- https://slicer.readthedocs.io/en/latest/developer_guide/build_instructions/index.html
~~~~~~~~

### K1-357-06 · KEEP · L19–L19

类型：LICENSE；范围：SOURCE_POINTER；规则：R03。
只确认许可入口，不能凭链接默认许可证类型。

后续去向：`POINTER_METADATA_CANDIDATE`。本轮未写入任何索引。

~~~~~~~~text
关于使用和贡献的信息，请查看 License.txt 文件。
~~~~~~~~

## 408 · immich-app/immich

批次：initial18；用途：APPLICATION；集合：development。
选择原因：备份风险、功能支持矩阵及截断表格。
文档版本：1192；正文 SHA-256：`ef5f3557bbdd22f3d1f6f47f4994ce53dd528d6b4da62a7244fb2a2a67a12c0a`。
正文风险标记：INCOMPLETE_TAIL。

### K1-408-01 · KEEP · L3–L3

类型：LICENSE；范围：SOURCE_POINTER；规则：R03。
保留 AGPL 许可信息。

后续去向：`POINTER_METADATA_CANDIDATE`。本轮未写入任何索引。

~~~~~~~~text
  <a href="https://opensource.org/license/agpl-v3"><img src="https://img.shields.io/badge/License-AGPL_v3-blue.svg?color=3F51B5&style=for-the-badge&label=License&logoColor=000000&labelColor=ececec" alt="许可证：AGPLv3"></a>
~~~~~~~~

### K1-408-02 · DROP · L11–L13

类型：NOISE；范围：LOGO；规则：R01。
Logo 不构成功能依据。

后续去向：`ARCHIVE_ONLY_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
<p align="center">
<img src="design/immich-logo-stacked-light.svg" width="300" title="Login With Custom URL">
</p>
~~~~~~~~

### K1-408-03 · DROP · L21–L40

类型：NOISE；范围：TRANSLATION_NAV；规则：R01。
README 翻译导航不等于产品支持这些界面语言。

后续去向：`ARCHIVE_ONLY_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
<p align="center">
  <a href="readme_i18n/README_ca_ES.md">Català</a>
  <a href="readme_i18n/README_es_ES.md">Español</a>
  <a href="readme_i18n/README_fr_FR.md">Français</a>
  <a href="readme_i18n/README_it_IT.md">Italiano</a>
  <a href="readme_i18n/README_ja_JP.md">日本語</a>
  <a href="readme_i18n/README_ko_KR.md">한국어</a>
  <a href="readme_i18n/README_de_DE.md">Deutsch</a>
  <a href="readme_i18n/README_nl_NL.md">Nederlands</a>
  <a href="readme_i18n/README_tr_TR.md">Türkçe</a>
  <a href="readme_i18n/README_zh_CN.md">简体中文</a>
  <a href="readme_i18n/README_zh_TW.md">正體中文</a>
  <a href="readme_i18n/README_uk_UA.md">Українська</a>
  <a href="readme_i18n/README_ru_RU.md">Русский</a>
  <a href="readme_i18n/README_pt_BR.md">Português Brasileiro</a>
  <a href="readme_i18n/README_sv_SE.md">Svenska</a>
  <a href="readme_i18n/README_ar_JO.md">العربية</a>
  <a href="readme_i18n/README_vi_VN.md">Tiếng Việt</a>
  <a href="readme_i18n/README_th_TH.md">ภาษาไทย</a>
</p>
~~~~~~~~

### K1-408-04 · KEEP · L43–L44

类型：LIMITATION；范围：CORE；规则：R05。
保留备份相关风险提示，不得仅抽取正向功能。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
> [!WARNING]
> ⚠️ 请始终遵循 [3-2-1](https://www.backblaze.com/blog/the-3-2-1-backup-strategy/) 备份计划，保护您珍贵的照片和视频！
~~~~~~~~

### K1-408-05 · KEEP · L74–L93

类型：CAPABILITY；范围：SUPPORT_MATRIX；规则：R06。
表头和每行必须配对保留，移动端与 Web 的否或不适用不能丢失。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
| 功能                                      | 移动端 | 网页端 |
| :------------------------------------------- | ------ | --- |
| 上传和查看视频和照片                         | 是    | 是 |
| 打开应用时自动备份                          | 是    | 不适用 |
| 防止资源重复                                | 是    | 是 |
| 选择性备份相册                              | 是    | 不适用 |
| 下载照片和视频到本地设备                    | 是    | 是 |
| 多用户支持                                  | 是    | 是 |
| 相册和共享相册                              | 是    | 是 |
| 可拖动/滑动滚动条                            | 是    | 是 |
| 支持 raw 格式                               | 是    | 是 |
| 元数据查看（EXIF、地图）                    | 是    | 是 |
| 按元数据、物体、面部和 CLIP 搜索            | 是    | 是 |
| 管理功能（用户管理）                        | 否    | 是 |
| 后台备份                                    | 是    | 不适用 |
| 虚拟滚动                                    | 是    | 是 |
| OAuth 支持                                  | 是    | 是 |
| API 密钥                                    | 不适用    | 是 |
| 实况照片/动态照片备份和播放                 | 是    | 是 |
| 支持 360 度图片显示                         | 否    | 是 |
~~~~~~~~

### K1-408-06 · REVIEW · L94–L94

类型：UNCERTAIN；范围：INCOMPLETE_TABLE；规则：R10。
尾部残缺表格行不进入新的证据索引。

后续去向：`REVIEW_QUEUE_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
| 用户自定义
~~~~~~~~

## 435 · navidrome/navidrome

批次：initial18；用途：APPLICATION；集合：development。
选择原因：HTML 外包装、稳定版本和部署选项。
文档版本：1219；正文 SHA-256：`dba3bec97a90c2da46ed98151b6e9d9a2812f498a701a525c547a8aa295b1fab`。
正文风险标记：OUTER_HTML_WRAPPER。

### K1-435-01 · DROP · L1–L1

类型：NOISE；范围：WRAPPER_ONLY；规则：R11。
仅允许去掉已配对的外层 html 围栏标记；结束标记在第 45 行，内部内容必须重新解析保留。

后续去向：`ARCHIVE_ONLY_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
```html
~~~~~~~~

附带限定原文 L45–L45：

~~~~~~~~text
```
~~~~~~~~

### K1-435-02 · KEEP · L15–L15

类型：REPO_IDENTITY；范围：CORE；规则：R02。
保留音乐服务器用途。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
Navidrome 是一个开源的、基于 Web 的音乐收藏服务器和流媒体播放器。它让你可以从任何浏览器或移动设备自由地聆听你的音乐收藏。它就像你的个人 Spotify！
~~~~~~~~

### K1-435-03 · KEEP · L18–L18

类型：MAINTENANCE；范围：CORE；规则：R08。
master 不稳定及推荐发布版本的限定影响部署。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
**注意**：`master` 分支在开发过程中可能处于不稳定甚至损坏的状态。请使用 [releases](https://github.com/navidrome/navidrome/releases) 而不是 `master` 分支，以获得稳定的二进制文件集。
~~~~~~~~

### K1-435-04 · KEEP · L22–L22

类型：SOURCE_POINTER；范围：MIXED；规则：R11。
反馈、贡献入口与社群邀请同在一行，不能删除有用入口。

后续去向：`PENDING_SAFE_SPLIT_NOT_DIRECTLY_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
__欢迎任何反馈！__ 如果你需要/想要一个新功能，发现了一个 bug，或者想到了任何改进 Navidrome 的方法，请提交 [GitHub issue](https://github.com/navidrome/navidrome/issues) 或加入我们的 [Subreddit](https://www.reddit.com/r/navidrome/) 讨论。如果你想以其他方式为项目做出贡献（[UI/后端开发](https://www.navidrome.org/docs/developers/)、[翻译](https://www.navidrome.org/docs/developers/translations/)、[主题](https://www.navidrome.org/docs/developers/creating-themes)），请加入我们的 [Discord 服务器](https://discord.gg/xh7j7yF) 聊天。
~~~~~~~~

### K1-435-05 · KEEP · L24–L26

类型：SOURCE_POINTER；范围：CORE；规则：R09。
安装指南入口应保留。

后续去向：`POINTER_METADATA_CANDIDATE`。本轮未写入任何索引。

~~~~~~~~text
## 安装

请参阅 [项目网站](https://www.navidrome.org/docs/installation/) 上的说明。
~~~~~~~~

### K1-435-06 · KEEP · L28–L32

类型：DEPLOYMENT；范围：HOSTING_OPTION；规则：R07。
托管服务是部署替代方式，需保留服务归属，不能推断必须购买云服务。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## 云托管

[PikaPods](https://www.pikapods.com) 与我们合作，为您提供 [官方支持的云托管解决方案](https://www.navidrome.org/docs/installation/managed/#pikapods)。收入的一部分将用于资助 Navidrome 的开发，而您无需支付额外费用。

[![PikaPods](https://www.pikapods.com/static/run-button.svg)](https://www.pikapods.com/pods?run=navidrome)
~~~~~~~~

### K1-435-07 · KEEP · L34–L44

类型：CAPABILITY；范围：CORE；规则：R06。
保留平台和 Docker 支持；支持 Docker 不等于只能使用 Docker。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## 特性

 - 处理非常**庞大的音乐收藏**
 - 流式传输几乎任何**可用的音频格式**
 - 读取并使用你精心整理的**元数据**
 - 对**合辑**（Various Artists 专辑）和**盒装套装**（多碟专辑）的出色支持
 - **多用户**，每个用户拥有自己的播放次数、播放列表、收藏等...
 - **极低的资源占用**
 - **多平台**，可在 macOS、Linux 和 Windows 上运行。也提供 **Docker** 镜像
 - 为所有主流平台提供即用型二进制文件，包括**树莓派**
 - 自动**监控你的库**
~~~~~~~~

## 517 · konvajs/konva

批次：initial18；用途：LIBRARY；集合：validation。
选择原因：浏览器和 Node 条件差异、构建测试。
文档版本：1301；正文 SHA-256：`bc23fa99253e2c8f66be9d05dfe2bf24de7e3fccbae0aa27d83d5e8794d883de`。
正文风险标记：未在已审计片段标记文档级风险。

### K1-517-01 · KEEP · L67–L71

类型：COMPATIBILITY；范围：BROWSER；规则：R05。
浏览器 ES2015 与 IE11 polyfill 条件一起保留。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
# Browsers support

Konva works in all modern mobile and desktop browsers. A browser need to be capable to run javascript code from ES2015 spec. For older browsers you may need polyfills for missing functions.

At the current moment `Konva` doesn't work in IE11 directly. To make it work you just need to provide some polyfills such as `Array.prototype.find`, `String.prototype.trimLeft`, `String.prototype.trimRight`, `Array.from`.
~~~~~~~~

### K1-517-02 · KEEP · L133–L142

类型：DEPLOYMENT；范围：NODE_JS；规则：R06。
canvas 或 skia 是 Node 场景依赖选项，不施加给所有浏览器用途。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
### 4 NodeJS env

In order to run `konva` in nodejs environment you also need to install `canvas` or `skia-canvas` package manually for rendering backend.

```bash
# node-canvas backend
npm install konva canvas
# skia-canvas backend
npm install konva skia-canvas
```
~~~~~~~~

### K1-517-03 · DROP · L157–L163

类型：NOISE；范围：SPONSOR_LOGOS；规则：R01。
赞助 Logo 列表不用于功能适配。

后续去向：`ARCHIVE_ONLY_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
# Backers

![https://simpleshow.com](https://avatars.githubusercontent.com/u/99720652?s=200&v=4 'https://simpleshow.com')
![https://www.notably.ai/](https://avatars.githubusercontent.com/u/80046841?s=200&v=4 'https://www.notably.ai/')

- [myposter GmbH](https://www.myposter.de/)
- [queue.gg](https://queue.gg/)
~~~~~~~~

### K1-517-04 · KEEP · L169–L175

类型：USAGE；范围：STUDY_CONTRIBUTION；规则：R04。
构建与测试步骤是学习源码和二次开发知识。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## Building the Konva Framework

To make a full build run `npm run build`. The command will compile all typescript files, combine then into one bundle and run minifier.

## Testing

Konva uses Mocha for testing.
~~~~~~~~

## 518 · langgenius/dify

批次：initial18；用途：APPLICATION；集合：validation。
选择原因：明确硬件下限、付费边界、附加许可条件。
文档版本：1302；正文 SHA-256：`9445a20331a37307ad934180a076f2f161a627cbde37a5b985e9dc8538fa9fcd`。
正文风险标记：未在已审计片段标记文档级风险。

### K1-518-01 · KEEP · L65–L70

类型：DEPLOYMENT；范围：CORE；规则：R06。
CPU 至少 2 核、内存至少 4 GiB 的数值、单位与最低条件一起保留。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## Quick start

> Before installing Dify, make sure your machine meets the following minimum system requirements:
>
> - CPU >= 2 Core
> - RAM >= 4 GiB
~~~~~~~~

### K1-518-02 · KEEP · L74–L81

类型：DEPLOYMENT；范围：DOCKER；规则：R04。
保留 compose 路径与命令顺序。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
The easiest way to start the Dify server is through [Docker Compose](docker/docker-compose.yaml). Before running Dify with the following commands, make sure that [Docker](https://docs.docker.com/get-docker/) and [Docker Compose](https://docs.docker.com/compose/install/) are installed on your machine:

```bash
cd dify
cd docker
cp .env.example .env
docker compose up -d
```
~~~~~~~~

### K1-518-03 · KEEP · L104–L108

类型：CAPABILITY；范围：CORE；规则：R02。
保留 RAG 和 Agent 功能声明。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
**4. RAG Pipeline**:
Extensive RAG capabilities that cover everything from document ingestion to retrieval, with out-of-box support for text extraction from PDFs, PPTs, and other common document formats.

**5. Agent capabilities**:
You can define agents based on LLM Function Calling or ReAct, and add pre-built or custom tools for the agent. Dify provides 50+ built-in tools for AI agents, such as Google Search, DALL·E, Stable Diffusion and WolframAlpha.
~~~~~~~~

### K1-518-04 · REVIEW · L118–L119

类型：UNCERTAIN；范围：COMMERCIAL_OFFER；规则：R10。
免费调用额度可能过期，冻结正文不能证明当前优惠仍有效。

后续去向：`REVIEW_QUEUE_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
- **Cloud <br/>**
  We host a [Dify Cloud](https://dify.ai) service for anyone to try with zero setup. It provides all the capabilities of the self-deployed version, and includes 200 free GPT-4 calls in the sandbox plan. If you run into issues with Dify Cloud, [contact our Cloud support team](mailto:cloud@dify.ai?subject=%5BGitHub%5DDify%20Cloud%20Support).
~~~~~~~~

### K1-518-05 · KEEP · L121–L126

类型：DEPLOYMENT；范围：EDITION_BOUNDARY；规则：R07。
社区版与企业版部署信息分清范围。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
- **Self-hosting Dify Community Edition<br/>**
  Quickly get Dify running in your environment with this [starter guide](#quick-start).
  Use our [documentation](https://docs.dify.ai) for further references and more in-depth instructions.

- **Dify for enterprise / organizations<br/>**
  We provide additional enterprise-centric features. [Send us an email](mailto:business@dify.ai?subject=%5BGitHub%5DBusiness%20License%20Inquiry) to discuss your enterprise needs. <br/>
~~~~~~~~

### K1-518-06 · DROP · L212–L214

类型：NOISE；范围：STAR_HISTORY；规则：R01。
Star 曲线不作为能力依据。

后续去向：`ARCHIVE_ONLY_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## Star history

[![Star History Chart](https://api.star-history.com/svg?repos=langgenius/dify&type=Date)](https://star-history.com/#langgenius/dify&Date)
~~~~~~~~

### K1-518-07 · KEEP · L220–L222

类型：LICENSE；范围：CORE；规则：R03。
Apache 基础加额外条件必须完整保留，不能简化为标准 Apache。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## License

This repository is licensed under the [Dify Open Source License](LICENSE), based on Apache 2.0 with additional conditions.
~~~~~~~~

## 519 · appwrite/appwrite

批次：initial18；用途：BACKEND_PLATFORM；集合：validation。
选择原因：多平台 shell 代码、架构说明和许可。
文档版本：1303；正文 SHA-256：`cfb44cca4051f192a2084950487d3de0cb78d346dce0c04f48a5ee98ca5bcf6f`。
正文风险标记：未在已审计片段标记文档级风险。

### K1-519-01 · REVIEW · L60–L62

类型：UNCERTAIN；范围：COMMERCIAL_OFFER；规则：R10。
公开测试与免费云服务描述需核实时间有效性。

后续去向：`REVIEW_QUEUE_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## Installation & Setup

The easiest way to get started with Appwrite is by [signing up for Appwrite Cloud](https://cloud.appwrite.io/). While Appwrite Cloud is in public beta, you can build with Appwrite completely free, and we won't collect your credit card information.
~~~~~~~~

### K1-519-02 · KEEP · L64–L79

类型：DEPLOYMENT；范围：UNIX_SHELL；规则：R04。
Unix Docker 命令和转义按原文保留，只作知识数据。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## Self-Hosting

Appwrite is designed to run in a containerized environment. Running your server is as easy as running one command from your terminal. You can either run Appwrite on your localhost using docker-compose or on any other container orchestration tool, such as [Kubernetes](https://kubernetes.io/docs/home/), [Docker Swarm](https://docs.docker.com/engine/swarm/), or [Rancher](https://rancher.com/docs/).

Before running the installation command, make sure you have [Docker](https://www.docker.com/products/docker-desktop) installed on your machine:

### Unix

```bash
docker run -it --rm \
    --publish 20080:20080 \
    --volume /var/run/docker.sock:/var/run/docker.sock \
    --volume "$(pwd)"/appwrite:/usr/src/code/appwrite:rw \
    --entrypoint="install" \
    appwrite/appwrite:1.9.0
```
~~~~~~~~

### K1-519-03 · KEEP · L94–L103

类型：DEPLOYMENT；范围：POWERSHELL；规则：R04。
PowerShell 反引号换行不能被 Markdown 清洗破坏。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
#### PowerShell

```powershell
docker run -it --rm `
    --publish 20080:20080 `
    --volume /var/run/docker.sock:/var/run/docker.sock `
    --volume ${pwd}/appwrite:/usr/src/code/appwrite:rw `
    --entrypoint="install" `
    appwrite/appwrite:1.9.0
```
~~~~~~~~

### K1-519-04 · KEEP · L199–L205

类型：ARCHITECTURE；范围：CORE；规则：R02。
架构结构用于源码学习和二次开发判断。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## Architecture

![Appwrite Architecture showing how Appwrite is built and the services and tools it uses](docs/specs/overview.drawio.svg)

Appwrite uses a microservices architecture that was designed for easy scaling and delegation of responsibilities. In addition, Appwrite supports multiple APIs, such as REST, WebSocket, and GraphQL to allow you to interact with your resources by leveraging your existing knowledge and protocols of choice.

The Appwrite API layer was designed to be extremely fast by leveraging in-memory caching and delegating any heavy-lifting tasks to the Appwrite background workers. The background workers also allow you to precisely control your compute capacity and costs using a message queue to handle the load. You can learn more about our architecture in the [contribution guide](CONTRIBUTING.md#architecture-1).
~~~~~~~~

### K1-519-05 · KEEP · L221–L223

类型：LICENSE；范围：CORE；规则：R03。
保留 BSD 许可表述。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## License

This repository is available under the [BSD 3-Clause License](./LICENSE).
~~~~~~~~

## 523 · home-assistant/home-assistant.io

批次：initial18；用途：DOCUMENTATION_SITE；集合：validation。
选择原因：文档网站源码与主应用的身份区分。
文档版本：1307；正文 SHA-256：`394fe2441b22197c59cbb664d50cb03553ddb2708598dce7b1e8ba61f19b339f`。
正文风险标记：未在已审计片段标记文档级风险。

### K1-523-01 · DROP · L1–L1

类型：NOISE；范围：SOCIAL_BADGE；规则：R01。
社群成员数徽章不进入功能索引。

后续去向：`ARCHIVE_ONLY_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
[![Discord](https://img.shields.io/discord/330944238910963714.svg)](https://www.home-assistant.io/join-chat/)
~~~~~~~~

### K1-523-02 · KEEP · L2–L2

类型：LICENSE；范围：SOURCE_POINTER；规则：R03。
保留 CC BY-NC-SA 许可线索，不能与核心应用许可混淆。

后续去向：`POINTER_METADATA_CANDIDATE`。本轮未写入任何索引。

~~~~~~~~text
[![License: CC BY-NC-SA 4.0](https://img.shields.io/badge/License-CC%20BY--NC--SA%204.0-lightgrey.svg)](https://creativecommons.org/licenses/by-nc-sa/4.0/)
~~~~~~~~

### K1-523-03 · KEEP · L6–L8

类型：REPO_IDENTITY；范围：DOCS_SITE；规则：R12。
该仓库是网站源码，不是 Home Assistant 核心服务。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
# Home Assistant website

This is the source for the [Home-Assistant.io website](https://home-assistant.io).
~~~~~~~~

### K1-523-04 · KEEP · L24–L38

类型：DEPLOYMENT；范围：DOCS_SITE；规则：R04。
命令用于网站本地预览，不能推荐为智能家居服务器部署步骤。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## Site preview

In order to make the preview available on `http://127.0.0.1:4000`, use the following [bundler](
https://bundler.io/
) command:

```bash
bundle exec rake preview
```

If the preview is not running on your local machine, pass the IP of the target machine from where it should be served as a parameter, i.e. to access on `http://192.168.0.123:4000`:

```bash
bundle exec rake preview[192.168.0.123]
```
~~~~~~~~

### K1-523-05 · KEEP · L40–L52

类型：USAGE；范围：DOCS_SITE；规则：R04。
isolate 与 integrate 成对保留，避免只抽取临时修改而漏恢复动作。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## Speeding up site generation

Every release we post long changelogs to the website. This slows down generation of the website significantly! We include some tools to temporarily exclude the blog posts that you're not working on out of the way.

```bash
bundle exec rake isolate[filename-of-blogpost]
```

When you're done working on the site, run the following command to move the posts back again:

```bash
bundle exec rake integrate
```
~~~~~~~~

## 543 · chatchat-space/Langchain-Chatchat

批次：initial18；用途：APPLICATION；集合：validation。
选择原因：模型前置服务、版本环境、迁移备份。
文档版本：1327；正文 SHA-256：`fc61a39466ced1764c8bb9c2640a6b92aef77b8dfb263a91a22b01ca20980451`。
正文风险标记：未在已审计片段标记文档级风险。

### K1-543-01 · KEEP · L128–L134

类型：DEPLOYMENT；范围：VERSION_0_3_0；规则：R06。
保留 Python 3.8–3.11 与硬件选择的版本上下文。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
### pip 安装部署

#### 0. 软硬件要求

💡 软件方面，本项目已支持在 Python 3.8-3.11 环境中进行使用，并已在 Windows、macOS、Linux 操作系统中进行测试。

💻 硬件方面，因 0.3.0 版本已修改为支持不同模型部署框架接入，因此可在 CPU、GPU、NPU、MPS 等不同硬件条件下使用。
~~~~~~~~

### K1-543-02 · KEEP · L154–L168

类型：DEPLOYMENT；范围：INFERENCE_SERVICE；规则：R05。
推理框架先行和独立环境警告不能与安装步骤分离。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
#### 2. 模型推理框架并加载模型

从 0.3.0 版本起，Langchain-Chatchat 不再根据用户输入的本地模型路径直接进行模型加载，涉及到的模型种类包括
LLM、Embedding、Reranker
及后续会提供支持的多模态模型等，均改为支持市面常见的各大模型推理框架接入，如 [Xinference](https://github.com/xorbitsai/inference)、[Ollama](https://github.com/ollama/ollama)、[LocalAI](https://github.com/mudler/LocalAI)、[FastChat](https://github.com/lm-sys/FastChat)、[One API](https://github.com/songquanpeng/one-api)
等。

因此，请确认在启动 Langchain-Chatchat 项目前，首先进行模型推理框架的运行，并加载所需使用的模型。

这里以 Xinference 举例,
请参考 [Xinference文档](https://inference.readthedocs.io/zh-cn/latest/getting_started/installation.html) 进行框架部署与模型加载。

> [!WARNING]  
> 为避免依赖冲突，请将 Langchain-Chatchat 和模型部署框架如 Xinference 等放在不同的 Python 虚拟环境中, 比如 conda, venv,
> virtualenv 等。
~~~~~~~~

### K1-543-03 · KEEP · L229–L238

类型：DEPLOYMENT；范围：KNOWLEDGE_BASE；规则：R04。
Embedding 前置条件、知识库初始化命令和警告组成证据单元。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
#### 4. 初始化知识库

> [!WARNING]  
> 进行知识库初始化前，请确保已经启动模型推理框架及对应 `embedding` 模型，且已按照上述**步骤3**完成模型接入配置。

```shell
chatchat kb -r
```

更多功能可以查看 `chatchat kb --help`
~~~~~~~~

### K1-543-04 · KEEP · L315–L320

类型：COMPATIBILITY；范围：MIGRATION；规则：R05。
备份与迁移兼容要求影响升级可行性。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
### 旧版本迁移

* 0.3.x 结构改变很大,强烈建议您按照文档重新部署. 以下指南不保证100%兼容和成功. 记得提前备份重要数据!

- 首先按照 `安装部署` 中的步骤配置运行环境，修改配置文件
- 将 0.2.x 项目的 knowledge_base 目录拷贝到配置的 `DATA` 目录下
~~~~~~~~

### K1-543-05 · DROP · L345–L351

类型：NOISE；范围：SOCIAL_QR；规则：R01。
社群二维码和群邀请不进入项目能力索引。

后续去向：`ARCHIVE_ONLY_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
[![Telegram](https://img.shields.io/badge/Telegram-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white "langchain-chatchat")](https://t.me/+RjliQ3jnJ1YyN2E9)

### 项目交流群

<img src="docs/img/qr_code_117_2.jpg" alt="二维码" width="300" />

🎉 Langchain-Chatchat 项目微信交流群，如果你也对本项目感兴趣，欢迎加入群聊参与讨论交流。
~~~~~~~~

## 47 · raphaelsalaja/audio

批次：additional20；用途：LIBRARY；集合：development。
选择原因：轻量音频库的初始化顺序和源码目录。
文档版本：831；正文 SHA-256：`0be006f91621cb08f362e6dbd4db305b9e049b89dd4f893921f1f43287db7340`。
正文风险标记：未在已审计片段标记文档级风险。

### K1-047-01 · KEEP · L4–L4

类型：LICENSE；范围：SOURCE_POINTER；规则：R03。
保留许可证徽章信息。

后续去向：`POINTER_METADATA_CANDIDATE`。本轮未写入任何索引。

~~~~~~~~text
[![license](https://img.shields.io/github/license/raphaelsalaja/audio)](LICENSE)
~~~~~~~~

### K1-047-02 · DROP · L5–L5

类型：NOISE；范围：CI_BADGE；规则：R01。
CI 徽章不是冻结版本的测试报告。

后续去向：`ARCHIVE_ONLY_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
[![CI](https://github.com/raphaelsalaja/audio/actions/workflows/ci.yml/badge.svg)](https://github.com/raphaelsalaja/audio/actions/workflows/ci.yml)
~~~~~~~~

### K1-047-03 · KEEP · L7–L9

类型：CAPABILITY；范围：CORE；规则：R06。
保留音频库用途和 11 kB 声明；不当作本轮实测大小。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
Declarative audio synthesis for the web. Describe sounds as plain objects, play them with one function call.

> ~11 kB gzipped, tree-shakeable, works with vanilla JS or React.
~~~~~~~~

### K1-047-04 · KEEP · L17–L30

类型：USAGE；范围：CORE；规则：R04。
保留先 await ensureReady 再调用 click 的顺序，不能打乱初始化步骤。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## Quick start

```ts
import { defineSound, ensureReady } from "@web-kits/audio";

const click = defineSound({
  source: { type: "sine", frequency: { start: 1200, end: 300 } },
  envelope: { attack: 0.001, decay: 0.06, sustain: 0, release: 0.03 },
  gain: 0.3,
});

await ensureReady();
click();
```
~~~~~~~~

### K1-047-05 · KEEP · L66–L73

类型：ARCHITECTURE；范围：CORE；规则：R02。
monorepo 目录信息可用于源码学习。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## Monorepo structure

```
packages/audio       Core library + CLI (published to npm)
apps/web             Documentation, registry, and homepage
ui/                  Shared UI components
.web-kits/           First-party sound patches & generated code
```
~~~~~~~~

## 54 · crankyoldgit/IRremoteESP8266

批次：additional20；用途：EMBEDDED_LIBRARY；集合：development。
选择原因：红外硬件、Arduino 环境和破坏性升级。
文档版本：838；正文 SHA-256：`2c9fdf447a17e3191357043c4d114cacc4c2d10d1d59d249e94512459533b401`。
正文风险标记：未在已审计片段标记文档级风险。

### K1-054-01 · DROP · L3–L3

类型：NOISE；范围：CI_BADGE；规则：R01。
动态构建状态不作为硬件兼容证据。

后续去向：`ARCHIVE_ONLY_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
[![Build Status](https://github.com/crankyoldgit/IRremoteESP8266/actions/workflows/Build.yml/badge.svg)](../../actions/workflows/Build.yml)
~~~~~~~~

### K1-054-02 · KEEP · L10–L11

类型：DEPLOYMENT；范围：HARDWARE；规则：R06。
ESP8266 或 ESP32、Arduino、940 nm 红外条件一起保留。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
This library enables you to **send _and_ receive** infra-red signals on an [ESP8266](https://github.com/esp8266/Arduino) or an
[ESP32](https://github.com/espressif/arduino-esp32) using the [Arduino framework](https://www.arduino.cc/) using common 940nm IR LEDs and common IR receiver modules. e.g. TSOP{17,22,24,36,38,44,48}* demodulators etc.
~~~~~~~~

### K1-054-03 · KEEP · L16–L24

类型：COMPATIBILITY；范围：VERSION_BOUNDARY；规则：R05。
2.0 和 2.5 之前的破坏性变更需要保留版本条件。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
#### Upgrading from pre-v2.0
Usage of the library has been slightly changed in v2.0. You will need to change your usage to work with v2.0 and beyond. You can read more about the changes required on our [Upgrade to v2.0](https://github.com/crankyoldgit/IRremoteESP8266/wiki/Upgrading-to-v2.0) page.

#### Upgrading from pre-v2.5
The library has changed from using constants declared as `#define` to
[const](https://google.github.io/styleguide/cppguide.html#Constant_Names) with
the appropriate naming per the
[C++ style guide](https://google.github.io/styleguide/cppguide.html).
This may potentially cause old programs to not compile.
~~~~~~~~

### K1-054-04 · KEEP · L48–L53

类型：DEPLOYMENT；范围：ARDUINO；规则：R04。
IDE 版本与各系统安装步骤有实际适配价值。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## Installation
##### Official releases via the Arduino IDE v1.8+ (Windows & Linux)
1. Click the _"Sketch"_ -> _"Include Library"_ -> _"Manage Libraries..."_ Menu items.
1. Enter `IRremoteESP8266` into the _"Filter your search..."_ top right search box.
1. Click on the IRremoteESP8266 result of the search.
1. Select the version you wish to install and click _"Install"_.
~~~~~~~~

## 55 · lvgl/lvgl

批次：additional20；用途：EMBEDDED_LIBRARY；集合：development。
选择原因：典型内存条件与商业产品边界。
文档版本：839；正文 SHA-256：`5f585c2aec54b37f5d9cac12c51aa6766e9c97de7244b4e31195b3650e15a1fe`。
正文风险标记：未在已审计片段标记文档级风险。

### K1-055-01 · KEEP · L43–L50

类型：DEPLOYMENT；范围：CORE；规则：R06。
100 KB RAM、200–300 KB 闪存及屏幕缓冲条件按原文保留，不泛化为所有场景的最低值。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## Overview

**LVGL** is a free and open-source UI library that enables you to create graphical user interfaces
for any MCUs and MPUs from any vendor on any platform.

**Requirements**: LVGL has no external dependencies, which makes it easy to compile for any modern target,
from small MCUs to multi-core Linux-based MPUs with 3D support. For a *typical* UI, you need only ~100kB RAM,
~200–300kB flash, and a buffer size of 1/10 of the screen for rendering.
~~~~~~~~

### K1-055-02 · REVIEW · L52–L54

类型：UNCERTAIN；范围：MARKETING_COMPATIBILITY；规则：R10。
采用量与广泛兼容宣传不能证明任意硬件均可运行。

后续去向：`REVIEW_QUEUE_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
**Wide adoption**: Chip vendors (like NXP, Espressif, Renesas, and so on), RTOS projects (Zephyr, NuttX, etc.),
and board manufacturers (Riverdi, Seeed Studio, VIEWE, Elecrow, etc.) have all integrated LVGL already. If a development board has a display, it's very likely
the vendor offers LVGL support too.
~~~~~~~~

### K1-055-03 · KEEP · L56–L56

类型：CAPABILITY；范围：PRO_EDITION；规则：R07。
Pro 导出和使用条件应归属商业产品，不能移植成核心库能力。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
**Professional tools**: Instead of writing C code, you can hugely speed up and simplify UI development by using [LVGL Pro](#lvgl-pro), a complete toolkit with Editor, Figma integration, Online Viewer, and CLI. It exports pure LVGL C code from XML, with no extra runtime or hidden magic. It's free for non-commercial use and evaluation.
~~~~~~~~

### K1-055-04 · KEEP · L284–L295

类型：LICENSE；范围：EDITION_BOUNDARY；规则：R03。
核心 MIT 与 Pro 收费许可边界必须保留。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## License

The LVGL library is distributed under the **MIT license**, so you can use it freely in both
open-source and commercial products with no royalties. See [`LICENCE.txt`](LICENCE.txt).

All third-party libraries bundled with LVGL are released under MIT-compatible licenses too,
so you can use LVGL and its dependencies with confidence.

**[LVGL Pro](https://lvgl.io/pro)** has separate licensing:
- The **Community** and **Evaluation** tiers are **free for non-commercial use**, perfect for
  learning, hobby projects, and evaluating the tools.
- Commercial use of LVGL Pro requires a paid license. See the [Pricing and Details](https://lvgl.io/pro#pricing).
~~~~~~~~

## 57 · nolangz/pixel2motion

批次：additional20；用途：AUTOMATION_TOOL；集合：development。
选择原因：浏览器前置条件、商业服务和发布清单。
文档版本：841；正文 SHA-256：`a526ab2482a02bdfbc2920782640a810ef9dace5a19516a88d5d5a18eef6549a`。
正文风险标记：未在已审计片段标记文档级风险。

### K1-057-01 · REVIEW · L8–L10

类型：UNCERTAIN；范围：COMMERCIAL_OFFER；规则：R10。
即将推出的商业服务不能当作当前开源版本现成功能。

后续去向：`REVIEW_QUEUE_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
  Better commercial Pixel2Motion services are coming online: polished logo-to-motion workflows, project-ready previews, and production support beyond the open-source skill.
  <br>
  中文：更完整的 Pixel2Motion 商业服务正在上线，面向更稳定的 logo-to-motion 交付、预览和项目支持。
~~~~~~~~

### K1-057-02 · KEEP · L151–L164

类型：DEPLOYMENT；范围：CORE；规则：R04。
Python 3.10+、Chrome 或 Chromium、Playwright 各有职责；不能把浏览器与 Playwright 误写为互相替代。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## Requirements

- Python 3.10+
- `Pillow` and `numpy` for image analysis helpers
- Chrome or Chromium for geometry and HTML rendering
- Playwright for deterministic frame capture and motion continuity QA

Recommended local setup:

```bash
python3 -m venv .venv
.venv/bin/pip install pillow numpy playwright
.venv/bin/python -m playwright install chromium
```
~~~~~~~~

### K1-057-03 · KEEP · L166–L170

类型：DEPLOYMENT；范围：CONDITIONAL_PATH；规则：R06。
CHROME_BIN 是条件性配置，不应把示例机器路径当作全平台固定值。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
If Chrome is not on the default path, set `CHROME_BIN` before running render checks:

```bash
export CHROME_BIN="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
```
~~~~~~~~

### K1-057-04 · KEEP · L180–L185

类型：USAGE；范围：STUDY_CONTRIBUTION；规则：R03。
发布检查清单可作开发资料；添加 LICENSE 的待办不是已经授予某种许可。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## Publishing Checklist

- Confirm `SKILL.md`, `agents/openai.yaml`, `references/`, `scripts/`, and `docs/` are committed.
- Keep generated logo deliverables, motion captures, local virtual environments, caches, and per-logo `outputs/` out of git.
- Enable GitHub Pages from branch `main`, folder `/docs`, after the first push.
- Add a `LICENSE` file before publishing if this repository should grant reuse rights.
~~~~~~~~

## 76 · react/react

批次：additional20；用途：LIBRARY；集合：development。
选择原因：标题、许可证和噪声在同一行。
文档版本：860；正文 SHA-256：`4d20edc8d043718c1459dd5d5e25777a282cc09092ecab0f9cc7559f0eac9842`。
正文风险标记：MIXED_INLINE_BADGES。

### K1-076-01 · KEEP · L1–L1

类型：REPO_IDENTITY；范围：MIXED；规则：R11。
标题、许可、npm 和 CI 同行，先保护有用片段，不能一刀切整行删除。

后续去向：`PENDING_SAFE_SPLIT_NOT_DIRECTLY_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
# [React](https://react.dev/) &middot; [![GitHub license](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/facebook/react/blob/main/LICENSE) [![npm version](https://img.shields.io/npm/v/react.svg?style=flat)](https://www.npmjs.com/package/react) [![(Runtime) Build and Test](https://github.com/facebook/react/actions/workflows/runtime_build_and_test.yml/badge.svg)](https://github.com/facebook/react/actions/workflows/runtime_build_and_test.yml) [![(Compiler) TypeScript](https://github.com/facebook/react/actions/workflows/compiler_typescript.yml/badge.svg?branch=main)](https://github.com/facebook/react/actions/workflows/compiler_typescript.yml) [![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](https://legacy.reactjs.org/docs/how-to-contribute.html#your-first-pull-request)
~~~~~~~~

### K1-076-02 · KEEP · L3–L7

类型：CAPABILITY；范围：CORE_AND_RELATED；规则：R07。
保留组件定位并区分 React Native 的相关产品范围。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
React is a JavaScript library for building user interfaces.

* **Declarative:** React makes it painless to create interactive UIs. Design simple views for each state in your application, and React will efficiently update and render just the right components when your data changes. Declarative views make your code more predictable, simpler to understand, and easier to debug.
* **Component-Based:** Build encapsulated components that manage their own state, then compose them to make complex UIs. Since component logic is written in JavaScript instead of templates, you can easily pass rich data through your app and keep the state out of the DOM.
* **Learn Once, Write Anywhere:** We don't make assumptions about the rest of your technology stack, so you can develop new features in React without rewriting existing code. React can also render on the server using [Node](https://nodejs.org/en) and power mobile apps using [React Native](https://reactnative.dev/).
~~~~~~~~

### K1-076-03 · KEEP · L41–L58

类型：USAGE；范围：CORE；规则：R05。
示例与 JSX 非必须的限定一起保留，避免把推荐写法变成硬要求。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## Examples

We have several examples [on the website](https://react.dev/). Here is the first one to get you started:

```jsx
import { createRoot } from 'react-dom/client';

function HelloMessage({ name }) {
  return <div>Hello {name}</div>;
}

const root = createRoot(document.getElementById('container'));
root.render(<HelloMessage name="Taylor" />);
```

This example will render "Hello Taylor" into a container on the page.

You'll notice that we used an HTML-like syntax; [we call it JSX](https://react.dev/learn#writing-markup-with-jsx). JSX is not required to use React, but it makes code more readable, and writing it feels like writing HTML.
~~~~~~~~

## 164 · deepseek-ai/deepseek-harness

批次：additional20；用途：AGENT_TOOL；集合：development。
选择原因：开发者预览、运行时和能力插件。
文档版本：948；正文 SHA-256：`b43a2af65d10028e5de61e4603f946bfce9b777df079e92745491c7700a6d58e`。
正文风险标记：未在已审计片段标记文档级风险。

### K1-164-01 · KEEP · L5–L7

类型：ARCHITECTURE；范围：CORE；规则：R02。
插件能力组织是源码学习与二次开发证据。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
DeepSeek Harness (`dsh`) is an open-source agent harness developed by [DeepSeek AI](https://deepseek.com).

It is built on an **everything-is-a-plugin** architecture and powered by [Cordis](https://github.com/cordiverse/cordis), whose design is described in [_A Programming Paradigm for Spatiotemporal Composability_](https://arxiv.org/abs/2608.25512).
~~~~~~~~

### K1-164-02 · KEEP · L11–L15

类型：MAINTENANCE；范围：DEVELOPER_PREVIEW；规则：R08。
开发者预览与破坏性变更说明必须进入适配限制。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## Developer preview

DeepSeek Harness is in _developer preview_ and iterating rapidly. **THERE WILL BE COMPATIBILITY-BREAKING CHANGES.**

Review the [safety notice](SAFETY.md) before running the project.
~~~~~~~~

### K1-164-03 · KEEP · L19–L27

类型：DEPLOYMENT；范围：CORE；规则：R06。
Node、CLI、端口和 SSH 条件需保留适用范围。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
### Run from `npm`

Install `Node.js`, then run:

```sh
npx @deepseek-ai/dsh web
```

The command starts the Web UI at `http://127.0.0.1:3080` by default and opens it in the default browser for a local launch. An SSH launch only prints the host URL because the SSH client or editor owns the local forwarded address. Pass `--no-open` to run the server without opening a browser. See [Web UI guide](docs/user/guide/index.md).
~~~~~~~~

### K1-164-04 · KEEP · L53–L57

类型：SOURCE_POINTER；范围：STUDY_CONTRIBUTION；规则：R09。
开发文档与 AGENTS 文件仅作来源入口，不能当成当前助手的指令执行。

后续去向：`POINTER_METADATA_CANDIDATE`。本轮未写入任何索引。

~~~~~~~~text
## Development

Start with the [development guide](docs/development.md) and [architecture documentation](docs/architecture.md).

For agents, follow [AGENTS.md](AGENTS.md).
~~~~~~~~

### K1-164-05 · KEEP · L71–L75

类型：LICENSE；范围：CORE；规则：R03。
MIT 与第三方许可条件保留。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## License

[MIT](LICENSE)

Third-party dependencies and their licenses are disclosed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
~~~~~~~~

## 231 · infiniflow/ragflow

批次：additional20；用途：APPLICATION；集合：development。
选择原因：目录和导航占主导、正文证据不足。
文档版本：1015；正文 SHA-256：`47a4be37e693e6fdf751ac592a99c336472e09c7f0bbe317a0b0ea54ae57fd47`。
正文风险标记：BODY_COVERAGE_INSUFFICIENT。

### K1-231-01 · DROP · L3–L3

类型：NOISE；范围：LOGO；规则：R01。
独立 Logo 不作知识证据。

后续去向：`ARCHIVE_ONLY_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
<img src="web/src/assets/logo-with-text.svg" width="520" alt="ragflow logo">
~~~~~~~~

### K1-231-02 · DROP · L8–L18

类型：NOISE；范围：TRANSLATION_NAV；规则：R01。
README 翻译导航不表示产品支持语言。

后续去向：`ARCHIVE_ONLY_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
  <a href="./README.md"><img alt="英文 README" src="https://img.shields.io/badge/English-DBEDFA"></a>
  <a href="./README_zh.md"><img alt="简体中文版自述文件" src="https://img.shields.io/badge/简体中文-DFE0E5"></a>
  <a href="./README_tzh.md"><img alt="繁體中文版自述文件" src="https://img.shields.io/badge/繁體中文-DFE0E5"></a>
  <a href="./README_ja.md"><img alt="日文 README" src="https://img.shields.io/badge/日本語-DFE0E5"></a>
  <a href="./README_ko.md"><img alt="韩文 README" src="https://img.shields.io/badge/한국어-DFE0E5"></a>
  <a href="./README_fr.md"><img alt="法文 README" src="https://img.shields.io/badge/Français-DFE0E5"></a>
  <a href="./README_id.md"><img alt="印度尼西亚文 README" src="https://img.shields.io/badge/Bahasa Indonesia-DFE0E5"></a>
  <a href="./README_pt_br.md"><img alt="葡萄牙文 (巴西) README" src="https://img.shields.io/badge/Português(Brasil)-DFE0E5"></a>
  <a href="./README_ar.md"><img alt="阿拉伯文 README" src="https://img.shields.io/badge/Arabic-DFE0E5"></a>
  <a href="./README_tr.md"><img alt="土耳其文 README" src="https://img.shields.io/badge/Türkçe-DFE0E5"></a>
</p>
~~~~~~~~

### K1-231-03 · KEEP · L33–L35

类型：LICENSE；范围：SOURCE_POINTER；规则：R03。
Apache 许可入口可以保留，但不等同完整条款核验。

后续去向：`POINTER_METADATA_CANDIDATE`。本轮未写入任何索引。

~~~~~~~~text
    <a href="https://github.com/infiniflow/ragflow/blob/main/LICENSE">
        <img height="21" src="https://img.shields.io/badge/License-Apache--2.0-ffffff?labelColor=d4eaf7&color=2e6cc4" alt="许可证">
    </a>
~~~~~~~~

### K1-231-04 · REVIEW · L56–L73

类型：UNCERTAIN；范围：TOC_WITHOUT_BODY；规则：R10。
只有目录锚点且对应正文缺少，不能推断功能和系统要求已经得到证实。

后续去向：`REVIEW_QUEUE_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
<details open>
<summary><b>📕 目录</b></summary>

- 💡 [什么是 RAGFlow？](#-what-is-ragflow)
- 🎮 [开始使用](#-get-started)
- 📌 [最新更新](#-latest-updates)
- 🌟 [主要特性](#-key-features)
- 🔎 [系统架构](#-system-architecture)
- 🎬 [自托管](#-self-hosting)
- 🔧 [配置](#-configurations)
- 🔧 [构建 Docker 镜像](#-build-a-docker-image)
- 🔨 [从源码启动开发服务](#-launch-service-from-source-for-development)
- 📚 [文档](#-documentation)
- 📜 [路线图](#-roadmap)
- 🏄 [社区](#-community)
- 🙌 [贡献](#-contributing)

</details>
~~~~~~~~

### K1-231-05 · KEEP · L75–L77

类型：REPO_IDENTITY；范围：CORE；规则：R12。
保留现有最简用途说明；不足以判断本地部署资源。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## 💡 什么是 RAGFlow？

[RAGFlow](https://ragflow.io/) 是一个领先的开源检索增强生成 ([RAG](https://ragflow.io/basics/what-is-rag)) 引擎
~~~~~~~~

## 247 · jeecgboot/JeecgBoot

批次：additional20；用途：APPLICATION_BUILDER；集合：development。
选择原因：技术事实与夸张宣传混合、JDK 分支差异。
文档版本：1031；正文 SHA-256：`c96706cc539a55bf58c4a7916a9b410a93b198f49abd5092f8626406a04722fa`。
正文风险标记：INCOMPLETE_TAIL。

### K1-247-01 · KEEP · L22–L24

类型：CAPABILITY；范围：MIXED；规则：R11。
保留技术栈原文，80% 等收益宣传不是本轮评测结论；后续需要子块区分。

后续去向：`PENDING_SAFE_SPLIT_NOT_DIRECTLY_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
JeecgBoot是一款企业级低代码平台集成了AI应用平台功能，旨在帮助开发者快速实现低代码开发和构建、部署个性化的 AI 应用。
前后端分离架构Ant Design4、Vue3，SpringBoot2/3，SpringCloud Alibaba，Mybatis-plus，Shiro/SpringAuthorizationServer，强大的代码生成器让前后端代码一键生成，无需写任何代码；提供强大的报表和大屏工具，满足企业级数据产品需求！
引领AI低代码开发模式: AI生成->OnlineCoding-> 代码生成-> 手工MERGE， 帮助Java项目解决80%的重复工作，让开发更多关注业务，提高效率、节省成本，同时又不失灵活性！低代码能力：Online表单、表单设计、流程设计、Online报表、大屏/仪表盘设计、报表设计; AI应用平台功能：AI知识库问答、AI模型管理、AI流程编排、AI聊天等，支持含ChatGPT、DeepSeek、Ollama等多种AI大模型
~~~~~~~~

### K1-247-02 · KEEP · L27–L27

类型：CAPABILITY；范围：CORE；规则：R02。
保留 AI 集成功能声明，仍需区分声明与运行验证。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
`AI赋能低代码:` 提供一套成熟AI应用平台功能：包含AI应用管理、AI模型管理、AI对话助手、AI知识库问答、AI流程编排、AI流程设计器，AI建表等功能; 支持各种AI大模型ChatGPT、DeepSeek、Ollama、智普、千问等.
~~~~~~~~

### K1-247-03 · REVIEW · L37–L40

类型：UNCERTAIN；范围：MARKETING_SUITABILITY；规则：R10。
适合 ERP 或 CRM 及效率提升宣传不证明已提供完整业务应用。

后续去向：`REVIEW_QUEUE_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
适用项目
-----------------------------------
JeecgBoot低代码平台，可以应用在任何J2EE项目的开发中，支持信创国产化。尤其适合SAAS项目、企业信息管理系统（MIS）、内部办公系统（OA）、企业资源计划系统（ERP）、客户关系管理系统（CRM）、AI知识库等，其半智能手工Merge的开发方式，可以显著提高开发效率70%以上，极大降低开发成本。
又是一个全栈式 AI 开发平台，快速帮助企业构建和部署个性化的 AI 应用。
~~~~~~~~

### K1-247-04 · KEEP · L48–L54

类型：COMPATIBILITY；范围：VERSION_MATRIX；规则：R06。
JDK 与版本分支表头、对应行一起保留。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
版本说明
-----------------------------------

|下载                                 | JDK17 + SpringBoot3.3 + Shiro                                               |JDK17 + SpringBoot3.3+ SpringAuthorizationServer   | JDK17/JDK8 + SpringBoot2.7 |
|------|----------------------------------------------------|-----------------------------------------------------------------------------|--------------------------------------------|
| Github |  [`springboot3`](https://github.com/jeecgboot/JeecgBoot/tree/springboot3)  | [`springboot3_sas`](https://github.com/jeecgboot/JeecgBoot/tree/springboot3_sas) 分支 |[`master`](https://github.com/jeecgboot/JeecgBoot) 分支|
| Gitee |  [`springboot3`](https://gitee.com/jeecg/JeecgBoot/tree/springboot3/)      | [`springboot3_sas`](https://gitee.com/jeecg/JeecgBoot/tree/springboot3_sas) 分支 |[`master`](https://gitee.com/jeecg/JeecgBoot)     分支 |
~~~~~~~~

### K1-247-05 · KEEP · L81–L82

类型：SOURCE_POINTER；范围：CORE；规则：R09。
官网和演示链接已完整，尾部另一链接截断不影响这两行。

后续去向：`POINTER_METADATA_CANDIDATE`。本轮未写入任何索引。

~~~~~~~~text
- 官方网站：  [http://www.jeecg.com](http://www.jeecg.com)
- 在线演示：  [平台演示](https://boot3.jeecg.com) | [APP演示](https://jeecg.com/appIndex)
~~~~~~~~

### K1-247-06 · REVIEW · L83–L83

类型：UNCERTAIN；范围：INCOMPLETE_LINK；规则：R10。
该行混有完整与截断链接，暂待复核，后续安全拆分后只保留完整入口。

后续去向：`REVIEW_QUEUE_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
- 入门指南：  [快速入门](http://www.jeecg.com/doc/quickstart)  | [代码生成使用](https://help.jeecg.com/java/codeg
~~~~~~~~

## 254 · hacs/integration

批次：additional20；用途：INTEGRATION；集合：development。
选择原因：集成管理功能与捐赠链接区分。
文档版本：1038；正文 SHA-256：`7f8b77550f15722c167222a01346b0ae050e4ad6559a6bfb2c72981a731a4d41`。
正文风险标记：未在已审计片段标记文档级风险。

### K1-254-01 · KEEP · L11–L21

类型：CAPABILITY；范围：HOME_ASSISTANT；规则：R02。
保留集成、插件和前端管理能力及宿主范围。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## 这是什么？

HACS 是一个集成，为用户提供了一个强大的 UI，用于处理自定义集成和插件的下载。

**HACS 的主要功能亮点：**

- 帮助您发现新的自定义元素。
- 帮助您安装（下载）新的自定义元素。
- 帮助您跟踪自定义元素。
  - 管理（安装/升级/移除）
  - 快捷方式跳转到仓库/问题跟踪器
~~~~~~~~

### K1-254-02 · KEEP · L25–L27

类型：SOURCE_POINTER；范围：CORE；规则：R09。
文档、配置和常见问题入口有导航价值。

后续去向：`POINTER_METADATA_CANDIDATE`。本轮未写入任何索引。

~~~~~~~~text
- [通用文档](https://hacs.xyz/)
- [配置](https://hacs.xyz/docs/configuration/start)
- [常见问题](https://hacs.xyz/docs/faq/what)
~~~~~~~~

### K1-254-03 · DROP · L30–L31

类型：NOISE；范围：DONATION；规则：R01。
捐赠链接不用于项目匹配。

后续去向：`ARCHIVE_ONLY_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
- [成为 GitHub 赞助者？ ❤️](https://github.com/sponsors/ludeeus)
- [请我喝~~咖啡~~啤酒？ 🍺🙈](https://buymeacoffee.com/ludeeus)
~~~~~~~~

## 259 · piitaya/lovelace-mushroom

批次：additional20；用途：UI_COMPONENTS；集合：development。
选择原因：零依赖的限定、定制限制和版本化功能。
文档版本：1043；正文 SHA-256：`9fdef4d27c0dbb0658a8277e0cb946207d2dec109109e86aee2d2c93c1dda8d2`。
正文风险标记：INCOMPLETE_TAIL。

### K1-259-01 · DROP · L9–L9

类型：NOISE；范围：DONATION；规则：R01。
捐赠图片不进入证据。

后续去向：`ARCHIVE_ONLY_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
<a href="https://www.buymeacoffee.com/piitaya" target="_blank"><img src="https://www.buymeacoffee.com/assets/img/custom_images/white_img.png" alt="Buy Me A Coffee" style="height: auto !important;width: auto !important;" ></a>
~~~~~~~~

### K1-259-02 · KEEP · L13–L24

类型：CAPABILITY；范围：HOME_ASSISTANT；规则：R05。
零依赖指不依赖其他卡片，不意味着不依赖 Home Assistant。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## 什么是 Mushroom ？

Mushroom 是一套用于 [Home Assistant][home-assistant] 仪表盘 UI 的卡片集合。

Mushroom 的使命是提供易于使用的组件，帮助你构建 [Home Assistant][home-assistant] 仪表盘。

### 特性

- 🛠 所有卡片及所有选项均提供编辑器（无需编辑 `yaml`）
- 😍 图标选择器
- 🖌 颜色选择器
- 🚀 零依赖：无需安装其他卡片。
~~~~~~~~

### K1-259-03 · KEEP · L30–L30

类型：LIMITATION；范围：CORE；规则：R07。
不支持深度定制的限制保留，所列替代项目的能力不归给 Mushroom。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
Mushroom 的目标并非提供深度自定义的卡片。你可以使用出色的 [UI Lovelace Minimalist][ui-lovelace-minimalist] 和 [Button card][button-card] 插件来实现此目的。
~~~~~~~~

### K1-259-04 · KEEP · L49–L61

类型：DEPLOYMENT；范围：MANUAL_INSTALL；规则：R04。
YAML、安装路径和高级模式前提一起保留。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
### 手动安装

1. 从 [最新发布][release-url] 下载 `mushroom.js` 文件。
2. 将 `mushroom.js` 文件放入 `config/www` 文件夹。
3. 在仪表盘中添加对 `mushroom.js` 的引用。有两种方式：
   - **使用 UI：** _设置_ → _仪表盘_ → _更多选项图标_ → _资源_ → _添加资源_ → 将 _Url_ 设置为 `/local/mushroom.js` → 将 _资源类型_ 设置为 `JavaScript Module`。
     **注意：** 如果看不到资源菜单，需要在 _用户资料_ 中启用 _高级模式_
   - **使用 YAML：** 将以下代码添加到 `lovelace` 部分。
     ```yaml
     resources:
       - url: /local/mushroom.js
         type: module
     ```
~~~~~~~~

### K1-259-05 · KEEP · L95–L103

类型：COMPATIBILITY；范围：VERSION_BOUNDARY；规则：R06。
旧卡片与 2024.8 模板徽章的版本区别保留。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
### 遗留卡片

某些卡片被视为遗留卡片，在卡片选择器中不可用，但你仍可使用它们：

- 🛠 [遗留模板卡片](docs/cards/legacy-template.md)

### 徽章

如果你使用 Home Assistant 2024.8 或更高版本，可使用 [模板徽章](docs/badges/template.md)。
~~~~~~~~

### K1-259-06 · REVIEW · L107–L107

类型：UNCERTAIN；范围：INCOMPLETE_TEXT；规则：R10。
主题说明尾部截断，待复核。

后续去向：`REVIEW_QUEUE_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
Mushroom 在没有主题的情况下也能使用，但你可以通过安装 Mushroom The
~~~~~~~~

## 265 · 78/xiaozhi-esp32

批次：additional20；用途：EMBEDDED_APPLICATION；集合：development。
选择原因：离线唤醒与云端推理区分、OTA 升级限制。
文档版本：1049；正文 SHA-256：`801435f63acb81161df1b082ad8be69e24ad4e83ef69acf45336c133f920318b`。
正文风险标记：未在已审计片段标记文档级风险。

### K1-265-01 · REVIEW · L13–L13

类型：UNCERTAIN；范围：IMAGE_ONLY；规则：R10。
图片的控制万物宣传不足以证明具体设备兼容性。

后续去向：`REVIEW_QUEUE_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
<img src="docs/mcp-based-graph.jpg" alt="通过 MCP 控制万物" width="320">
~~~~~~~~

### K1-265-02 · KEEP · L15–L21

类型：COMPATIBILITY；范围：VERSION_BOUNDARY；规则：R08。
v1 与 v2 OTA 不兼容、手工烧录和维护时间按快照记录，不能宣称当前仍在维护。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## 版本说明

当前 v2 版本与 v1 版本分区表不兼容，因此无法通过 OTA 从 v1 升级到 v2。分区表详情请参见 [partitions/v2/README.md](partitions/v2/README.md)。

所有运行 v1 版本的硬件均可通过手动烧录固件升级至 v2。

v1 稳定版本为 1.9.2。运行 `git checkout v1` 即可切换到 v1 分支。v1 分支将持续维护至 2026 年 2 月。
~~~~~~~~

### K1-265-03 · KEEP · L23–L36

类型：CAPABILITY；范围：DEVICE_AND_CLOUD；规则：R05。
离线唤醒不等于完整离线对话；云端大模型和设备条件必须保留。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
### 已实现功能

- Wi-Fi / ML307 Cat.1 4G
- 离线语音唤醒 [ESP-SR](https://github.com/espressif/esp-sr)
- 支持两种通信协议（[Websocket](docs/websocket.md) 或 MQTT+UDP）
- 使用 OPUS 音频编解码
- 基于流式 ASR + LLM + TTS 架构的语音交互
- 声纹识别，识别当前说话人 [3D Speaker](https://github.com/modelscope/3D-Speaker)
- OLED / LCD 显示屏，支持表情显示
- 电量显示与电源管理
- 多语言支持（中文、英文、日语）
- 支持 ESP32-C3、ESP32-S3、ESP32-P4 芯片平台
- 设备端 MCP，用于设备控制（扬声器、LED、舵机、GPIO 等）
- 云端 MCP，扩展大模型能力（智能家居控制、PC 桌面操作、知识搜索、邮件等）
~~~~~~~~

### K1-265-04 · KEEP · L51–L58

类型：SOURCE_POINTER；范围：HARDWARE_COMPATIBILITY；规则：R09。
开发板入口包含兼容信息，不能因链接通向硬件页面就按广告删除。

后续去向：`POINTER_METADATA_CANDIDATE`。本轮未写入任何索引。

~~~~~~~~text
### 支持 70+ 开源硬件（部分列表）

- <a href="https://oshwhub.com/li-chuang-kai-fa-ban/li-chuang-shi-zhan-pai-esp32-s3-kai-fa-ban" target="_blank" title="立创·实战派 ESP32-S3 开发板">立创·实战派 ESP32-S3 开发板</a>
- <a href="https://github.com/espressif/esp-box" target="_blank" title="乐鑫 ESP32-S3-BOX3">乐鑫 ESP32-S3-BOX3</a>
- <a href="https://docs.m5stack.com/zh_CN/core/CoreS3" target="_blank" title="M5Stack CoreS3">M5Stack CoreS3</a>
- <a href="https://docs.m5stack.com/en/atom/Atomic%20Echo%20Base" target="_blank" title="AtomS3R + Echo Base">M5Stack AtomS3R + Echo Base</a>
- <a href="https://gf.bilibili.com/item/detail/1108782064" target="_blank" title="神奇按钮 2.4">神奇按钮 2.4</a>
- <a href="https://www.waveshare.net/shop/ESP32-S3-Touch-AMOLED-1.8.htm" target="_blank" title="微雪电子 ESP32-S3-Touch-AMOLED-1.8">微雪电子 ESP32-S3-Touch-AMOLED-1.8</a>
~~~~~~~~

## 278 · MarlinFirmware/Marlin

批次：additional20；用途：FIRMWARE；集合：development。
选择原因：正文主要是 Logo、许可及翻译表格。
文档版本：1062；正文 SHA-256：`3b2b5b28a85d3f0923d4ad911addff545cb4d7e3423de072e181a7991203e9e8`。
正文风险标记：BODY_COVERAGE_INSUFFICIENT。

### K1-278-01 · DROP · L1–L1

类型：NOISE；范围：LOGO；规则：R01。
独立 Logo 不作为硬件功能。

后续去向：`ARCHIVE_ONLY_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
<p align="center"><img src="buildroot/share/pixmaps/logo/marlin-outrun-nf-500.png" height="250" alt="MarlinFirmware 的标志" /></p>
~~~~~~~~

### K1-278-02 · KEEP · L3–L3

类型：REPO_IDENTITY；范围：CORE；规则：R12。
仅能确认 3D 打印机固件定位，不能据此判断具体机型。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
<h1 align="center">Marlin 3D 打印机固件</h1>
~~~~~~~~

### K1-278-03 · KEEP · L6–L6

类型：LICENSE；范围：SOURCE_POINTER；规则：R03。
GPL-V3.0 许可线索保留。

后续去向：`POINTER_METADATA_CANDIDATE`。本轮未写入任何索引。

~~~~~~~~text
    <a href="/LICENSE"><img alt="GPL-V3.0 许可证" src="https://img.shields.io/github/license/marlinfirmware/marlin.svg"></a>
~~~~~~~~

### K1-278-04 · DROP · L16–L49

类型：NOISE；范围：TRANSLATION_NAV；规则：R01。
此 HTML 表格是翻译导航而非功能表；不能套用所有表格一律保留。

后续去向：`ARCHIVE_ONLY_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
### 🌍 翻译

<table>
<tr>
  <td><a href="//translate.google.com/translate?u=github.com/MarlinFirmware/Marlin&sl=auto&tl=an">阿拉贡语</a></td>
  <td><a href="//translate.google.com/translate?u=github.com/MarlinFirmware/Marlin&sl=auto&tl=bg">保加利亚语</a></td>
  <td><a href="//translate.google.com/translate?u=github.com/MarlinFirmware/Marlin&sl=auto&tl=ca">加泰罗尼亚语</a></td>
  <td><a href="//translate.google.com/translate?u=github.com/MarlinFirmware/Marlin&sl=auto&tl=cs">捷克语</a></td>
  <td><a href="//translate.google.com/translate?u=github.com/MarlinFirmware/Marlin&sl=auto&tl=da">丹麦语</a></td>
  <td><a href="//translate.google.com/translate?u=github.com/MarlinFirmware/Marlin&sl=auto&tl=de">德语</a></td>
  <td><a href="//translate.google.com/translate?u=github.com/MarlinFirmware/Marlin&sl=auto&tl=el">希腊语</a></td>
</tr>
<tr>
  <td><a href="//github.com/MarlinFirmware/Marlin">英语</a></td>
  <td><a href="//translate.google.com/translate?u=github.com/MarlinFirmware/Marlin&sl=auto&tl=es">西班牙语</a></td>
  <td><a href="//translate.google.com/translate?u=github.com/MarlinFirmware/Marlin&sl=auto&tl=eu">巴斯克语</a></td>
  <td><a href="//translate.google.com/translate?u=github.com/MarlinFirmware/Marlin&sl=auto&tl=fi">芬兰语</a></td>
  <td><a href="//translate.google.com/translate?u=github.com/MarlinFirmware/Marlin&sl=auto&tl=fr">法语</a></td>
  <td><a href="//translate.google.com/translate?u=github.com/MarlinFirmware/Marlin&sl=auto&tl=gl">加利西亚语</a></td>
  <td><a href="//translate.google.com/translate?u=github.com/MarlinFirmware/Marlin&sl=auto&tl=hr">克罗地亚语</a></td>
</tr>
<tr>
  <td><a href="//translate.google.com/translate?u=github.com/MarlinFirmware/Marlin&sl=auto&tl=hu">匈牙利语</a></td>
  <td><a href="//translate.google.com/translate?u=github.com/MarlinFirmware/Marlin&sl=auto&tl=it">意大利语</a></td>
  <td><a href="//translate.google.com/translate?u=github.com/MarlinFirmware/Marlin&sl=auto&tl=ja">日语</a></td>
  <td><a href="//translate.google.com/translate?u=github.com/MarlinFirmware/Marlin&sl=auto&tl=ko">韩语</a></td>
  <td><a href="//translate.google.com/translate?u=github.com/MarlinFirmware/Marlin&sl=auto&tl=nl">荷兰语</a></td>
  <td><a href="//translate.google.com/translate?u=github.com/MarlinFirmware/Marlin&sl=auto&tl=pl">波兰语</a></td>
  <td><a href="//translate.google.com/translate?u=github.com/MarlinFirmware/Marlin&sl=auto&tl=pt">葡萄牙语</a></td>
</tr>
<tr>
  <td><a href="//translate.google.com/translate?u=github.com/MarlinFirmware/Marlin&sl=auto&tl=pt-BR">葡萄牙语 (巴西)</a></td>
</tr>
</table>
~~~~~~~~

## 279 · OrcaSlicer/OrcaSlicer

批次：additional20；用途：DESKTOP_APPLICATION；集合：validation。
选择原因：官方来源警告表格和切片参数能力。
文档版本：1063；正文 SHA-256：`ecb1d5d998e8924f16f2b056c52e649dff9b42831a0d9b7bcdf1b5ca9309639a`。
正文风险标记：未在已审计片段标记文档级风险。

### K1-279-01 · DROP · L7–L7

类型：NOISE；范围：TREND_BADGE；规则：R01。
趋势排名徽章不用于功能或可信度判断。

后续去向：`ARCHIVE_ONLY_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
<a href="https://trendshift.io/repositories/15552" target="_blank"><img src="https://trendshift.io/api/badge/repositories/15552" alt="OrcaSlicer/OrcaSlicer | Trendshift" style="width: 250px; height: 55px;" width="250" height="55"/></a>
~~~~~~~~

### K1-279-02 · KEEP · L34–L46

类型：LIMITATION；范围：SOURCE_WARNING；规则：R05。
HTML 表格中的来源警告和版本下载指引必须保留，不能按样式标签删除。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
<table border="2" style="border-color: #ffa500; background-color:rgb(232, 220, 180); color: #856404;">
<tr>
<td>
<strong>⚠️ 注意：</strong><br>
多个点击诱饵和恶意网站，如 <b>orca-slicer[.]com</b> 和 <b>orcaslicer[.]net</b>，正冒充官方 OrcaSlicer 网站。这些网站可能会将您重定向到危险下载或包含误导性信息。<br>
<b>我们唯一的官方网站是 <a href="https://www.orcaslicer.com/">www.orcaslicer.com</a>。</b><br><br>
如果您在搜索结果中遇到任何此类网站，请<b>举报</b>它们为不安全或钓鱼网站，以帮助维护社区安全，可通过以下渠道：<br>
 - <a href="https://safebrowsing.google.com/safebrowsing/report_phish/">Google 安全浏览</a><br>
 - <a href="https://www.microsoft.com/en-us/wdsi/support/report-unsafe-site">Microsoft 安全情报</a><br>
 - <a href="https://ipthreat.net/tools/reportphishing">IPThreat</a>
</td>
</tr>
</table>
~~~~~~~~

### K1-279-03 · KEEP · L50–L61

类型：CAPABILITY；范围：CORE；规则：R02。
校准、精度和切片设置不等于自主诊断自动调参 Agent。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
# 主要功能

- **[高级校准工具](https://www.orcaslicer.com/wiki/calibration_guide)**  
  全面的套件：温度塔、流量、回抽等，以实现最佳性能。
- **[精确壁厚](https://www.orcaslicer.com/wiki/quality_settings_precision#precise-wall) 和 [接缝控制](https://www.orcaslicer.com/wiki/quality_settings_seam)**  
  调整外壁间距并应用围巾接缝，以提高打印精度。
- **[三明治模式](https://www.orcaslicer.com/wiki/quality_settings_wall_and_surfaces#innerouterinner) 和 [多边形孔](https://www.orcaslicer.com/wiki/quality_settings_precision#polyholes) 支持**  
  使用不同的填充[图案](https://www.orcaslicer.com/wiki/strength_settings_patterns)和精确的孔形状，以获得更清晰的效果。
- **[悬垂](https://www.orcaslicer.com/wiki/quality_settings_overhangs) 和 [支撑优化](https://www.orcaslicer.com/wiki#support-settings)**  
  通过精确的支撑放置，修改几何形状以实现可打印的悬垂。
- **[精细控制与自定义](https://www.orcaslicer.com/wiki#process-settings)**  
  微调打印速度、层高、压力和温度。
~~~~~~~~

## 284 · gematik/ref-idp-server

批次：additional20；用途：REFERENCE_IMPLEMENTATION；集合：validation。
选择原因：明确非生产用途、子模块和测试配置。
文档版本：1068；正文 SHA-256：`8af0504df485013b999ea410e0c71cf04b0b0b0c12b53c031263b50ceccefbdb`。
正文风险标记：未在已审计片段标记文档级风险。

### K1-284-01 · KEEP · L1–L3

类型：LIMITATION；范围：NON_PRODUCTION；规则：R05。
参考实现不适合生产必须与推荐一起呈现。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## 免责声明

本软件并非为生产用途开发。其开发目的是为了验证规范的可行性。
~~~~~~~~

### K1-284-02 · KEEP · L7–L11

类型：ARCHITECTURE；范围：NON_PRODUCTION；规则：R12。
子模块用途保留，继承第 1–3 行非生产限定。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
**IDP-Global** 项目由多个子项目组成。这些子项目包括：

* **IDP-Server:** 中央IDP的参考实现
* **IDP-Client:** 客户端，用于在中央IDP上使用SMC-B-Aut或HBA-Aut密钥请求ACCESS_TOKEN
* **IDP-Testsuite:** 针对中央IDP的审批测试套件，还包括针对Fast Track和联邦IDP的测试
~~~~~~~~

附带限定原文 L1–L3：

~~~~~~~~text
## 免责声明

本软件并非为生产用途开发。其开发目的是为了验证规范的可行性。
~~~~~~~~

### K1-284-03 · KEEP · L23–L33

类型：USAGE；范围：BUILD_OPTIONS；规则：R04。
构建和跳过测试是不同命令，不能把跳过测试说成已验证。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
###### 示例1：构建并运行所有测试

```console
$ mvn clean install -pl idp-server -am
```

###### 示例2：跳过单元测试和集成测试构建，并为dockerfile设置commit_hash参数

```console
$ mvn clean install -pl idp-server -am -Dskip.unittests -Dskip.inttests -Dcommit_hash=`git log --pretty=format:'%H' -n 1`
```
~~~~~~~~

附带限定原文 L1–L3：

~~~~~~~~text
## 免责声明

本软件并非为生产用途开发。其开发目的是为了验证规范的可行性。
~~~~~~~~

### K1-284-04 · KEEP · L69–L84

类型：DEPLOYMENT；范围：TEST_ENVIRONMENT；规则：R05。
配置优先级和测试环境限制保留。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
idp-server按以下优先级顺序确定URL（如果存在）：

1. jvm参数：--idp.serverUrl=https://myServerUrlAsJvmArgument.de
2. 环境变量：IDP_SERVER_URL=myServerUrlFromEnv:8080
3. spring boot配置（application.yml）：

```
idp:
   serverUrl: "https://urlPreConfiguredUrl"
```

在开发过程中，建议不要在application.yml中设置"serverUrl"，因为某些单元测试会因此失败。
背景：serverUrl会在discovery文档中多次设置，并在单元测试中使用。
单元测试中会使用随机（空闲）端口，因此这些端口是serverUrl的一部分。

4. 预编译值：IdpConstants.DEFAULT_SERVER_URL
~~~~~~~~

附带限定原文 L1–L3：

~~~~~~~~text
## 免责声明

本软件并非为生产用途开发。其开发目的是为了验证规范的可行性。
~~~~~~~~

### K1-284-05 · KEEP · L121–L127

类型：LICENSE；范围：CORE；规则：R03。
保留 Apache 许可正文片段。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## 许可证

版权所有 2020-2026 gematik GmbH

Apache许可证，版本2.0

有关许可下的具体权限和限制，请参阅[LICENSE](./LICENSE)文件。
~~~~~~~~

附带限定原文 L1–L3：

~~~~~~~~text
## 免责声明

本软件并非为生产用途开发。其开发目的是为了验证规范的可行性。
~~~~~~~~

## 294 · vijishmadhavan/ArtLine

批次：additional20；用途：ML_APPLICATION；集合：validation。
选择原因：Colab 徽章是运行入口，示例图不是效果证据。
文档版本：1078；正文 SHA-256：`6f90b1ada500451f7e3d02e3074206fdbc7c88d2b085fbc5d615ec636ac650c0`。
正文风险标记：INCOMPLETE_TAIL。

### K1-294-01 · DROP · L3–L3

类型：NOISE；范围：SPONSOR；规则：R01。
赞助推广不作项目能力证据。

后续去向：`ARCHIVE_ONLY_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
**你可以赞助我来支持我的开源工作 💖 [赞助](https://github.com/sponsors/vijishmadhavan?o=sd&sc=t)**
~~~~~~~~

### K1-294-02 · KEEP · L14–L16

类型：CAPABILITY；范围：CORE；规则：R02。
保留图像转换用途及输入提示。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## ControlNet + ArtLine

该模型设计为接收一张肖像图片和一条对应的文字指令，然后根据该指令调整图片的风格。
~~~~~~~~

### K1-294-03 · REVIEW · L19–L23

类型：UNCERTAIN；范围：IMAGE_ONLY；规则：R10。
未读取示例图像，不能自动声称输出质量。

后续去向：`REVIEW_QUEUE_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
![model](https://i.imgur.com/QNCw4CN.jpg)

![model](https://i.imgur.com/jJuF3UN.jpg)

![model](https://i.imgur.com/zizgSTf.jpg)
~~~~~~~~

### K1-294-04 · KEEP · L70–L79

类型：SOURCE_POINTER；范围：RUNNABLE_DEMO；规则：R09。
Colab 和 RunwayML 按钮是运行入口，保留两种模型标签与各自链接；不能把第三方托管能力并入本仓库。

后续去向：`POINTER_METADATA_CANDIDATE`。本轮未写入任何索引。

~~~~~~~~text
**模型-（平滑版）**

[<img src="https://colab.research.google.com/assets/colab-badge.svg" align="center">](https://colab.research.google.com/github/vijishmadhavan/Light-Up/blob/master/ArtLine.ipynb)


**模型-（画质版）**

[<img src="https://colab.research.google.com/assets/colab-badge.svg" align="center">](https://colab.research.google.com/github/vijishmadhavan/Light-Up/blob/master/ArtLine(AR).ipynb)

[![在 RunwayML 中打开 Badge](https://open-app.runwayml.com/gh-badge.svg)](https://open-app.runwayml.com/?model=akhaliq/ArtLine)
~~~~~~~~

### K1-294-05 · REVIEW · L104–L104

类型：UNCERTAIN；范围：INCOMPLETE_TEXT；规则：R10。
完整技术句和截断尾句混在一行，暂待复核；后续可拆出完整原句，不能补全残句。

后续去向：`REVIEW_QUEUE_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
* **渐进式调整大小** (https://arxiv.org/abs/1710.10196),(https://arxiv.org/pdf/1707.02921.pdf)。渐进式调整大小采用了逐步增加图像尺寸的思路，在这个项目中，图像尺寸逐渐增大，并相应调整了学习率。感谢 fast.ai 让我了解了 P。
~~~~~~~~

## 306 · phaserjs/phaser

批次：additional20；用途：GAME_FRAMEWORK；集合：validation。
选择原因：HTML 示例、原生导出第三方依赖、体积表格。
文档版本：1090；正文 SHA-256：`e5f3361904db3b866d5c1804f6a234b4fb94fd3f170ddc4462c321d646189bda`。
正文风险标记：INCOMPLETE_TAIL。

### K1-306-01 · DROP · L5–L7

类型：NOISE；范围：SOCIAL_STATISTICS；规则：R01。
社交和下载数量徽章不进入功能索引。

后续去向：`ARCHIVE_ONLY_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
[![Discord](https://img.shields.io/discord/244245946873937922?style=for-the-badge)](https://discord.gg/phaser)
![JSDelivr](https://img.shields.io/jsdelivr/npm/hm/phaser?style=for-the-badge)
![GitHub](https://img.shields.io/github/downloads/phaserjs/phaser/total?style=for-the-badge)
~~~~~~~~

### K1-306-02 · KEEP · L9–L11

类型：CAPABILITY；范围：CORE_AND_THIRD_PARTY；规则：R07。
框架不等于现成游戏；原生导出依赖第三方工具，需保留条件。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
Phaser 是一个快速、免费且有趣的开源 HTML5 游戏框架，支持在桌面和移动网页浏览器上使用 WebGL 和 Canvas 渲染，并已持续开发超过 13 年。

游戏可以构建为网页游戏，也可以作为 YouTube Playables、Discord Activities、Reddit 游戏、Twitch Overlays，或使用第三方工具编译为 iOS、Android、Steam 和原生应用。您可以使用 JavaScript 或 TypeScript 进行开发。Phaser 支持超过 40 种不同的前端框架，包括 React 和 Vue。
~~~~~~~~

### K1-306-03 · KEEP · L35–L49

类型：USAGE；范围：CORE；规则：R04。
HTML script 是有效调用示例，不是需要一律删除的页面装饰。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## 从 CDN 使用 Phaser

[Phaser 已上架 jsDelivr](https://www.jsdelivr.com/package/npm/phaser)（一个面向开发者的超快 CDN）。在您的 HTML 中包含 _以下任一_ 代码：

```html
<script src="//cdn.jsdelivr.net/npm/phaser@4.2.1/dist/phaser.js"></script>
<script src="//cdn.jsdelivr.net/npm/phaser@4.2.1/dist/phaser.min.js"></script>
```

也可从 Cloudflare 的 [cdnjs](https://cdnjs.com/libraries/phaser) 获取：

```html
<script src="https://cdnjs.cloudflare.com/ajax/libs/phaser/4.2.1/phaser.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/phaser/4.2.1/phaser.min.js"></script>
```
~~~~~~~~

### K1-306-04 · KEEP · L67–L77

类型：DEPLOYMENT；范围：BUILD_SIZE_MATRIX；规则：R06。
不同构建体积与 gzip 列含义一起保留。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## Phaser 有多大？

如果您查看 `phaser.js` 并看到一个超过 8 MB 的文件，请不要惊慌。其中超过 84% 是内联文档——JSDoc 注释、类型注解和详细的方法描述，这些为 TypeScript 定义和 API 文档提供支持。它是为您的 IDE 准备的，而不是为您的玩家。

压缩后的构建版本 `phaser.min.js` 会去除所有这些内容，在标准 gzip Web 服务器压缩下大小为 **345 KB**。这是包含所有功能的完整版本，但仍比大多数纹理图片要小。您还可以通过调整构建设置排除游戏不需要的功能来进一步减小体积。

| 构建版本 | 原始大小 | 压缩大小 (gzip) |
| ----- | -------- | ------------------ |
| `phaser.js`（含文档） | 8.23 MB | - |
| `phaser.min.js`（完整） | 1.29 MB | 345 KB |
| `phaser-arcade.min.js` | 1.18 MB | 313 KB |
~~~~~~~~

### K1-306-05 · REVIEW · L79–L81

类型：UNCERTAIN；范围：INCOMPLETE_TEXT；规则：R10。
尾部说明不完整，不能自行补齐功能。

后续去向：`REVIEW_QUEUE_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
### 为什么选择 Phaser？

- **久经考验** - 十多年的活跃开发。数万款游戏发布。一个庞大的开发者社区，他们
~~~~~~~~

## 344 · blakeblackshear/frigate

批次：additional20；用途：APPLICATION；集合：validation。
选择原因：推荐硬件不等于硬门槛、商标例外。
文档版本：1128；正文 SHA-256：`95900900ec962928aa8a66007740a1951c2392999805ec70a4157af98aac5efa`。
正文风险标记：未在已审计片段标记文档级风险。

### K1-344-01 · DROP · L1–L3

类型：NOISE；范围：LOGO；规则：R01。
独立 Logo 不作功能依据。

后续去向：`ARCHIVE_ONLY_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
<p align="center">
  <img align="center" alt="logo" src="docs/static/img/branding/frigate.png">
</p>
~~~~~~~~

### K1-344-02 · KEEP · L15–L17

类型：DEPLOYMENT；范围：CORE；规则：R06。
本地 NVR 和强烈建议 GPU 的限定保留，推荐条件不能升级成必需。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
一个完整且本地运行的NVR，专为[Home Assistant](https://www.home-assistant.io)设计，具备AI对象检测功能。使用OpenCV和Tensorflow在本地为IP摄像头执行实时对象检测。

强烈推荐使用GPU或AI加速器。AI加速器的性能甚至优于最好的CPU，且开销极低。请参阅Frigate支持的[对象检测器](https://docs.frigate.video/configuration/object_detectors/)。
~~~~~~~~

### K1-344-03 · DROP · L34–L36

类型：NOISE；范围：DONATION；规则：R01。
捐赠信息不作适配判断。

后续去向：`ARCHIVE_ONLY_NOT_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## 捐赠

如果您想捐赠支持开发，请使用[Github Sponsors](https://github.com/sponsors/blakeblackshear)。
~~~~~~~~

### K1-344-04 · KEEP · L38–L45

类型：LICENSE；范围：CODE_VS_TRADEMARK；规则：R03。
代码 MIT 与品牌商标例外必须同时保留。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## 许可证

本项目采用**MIT许可证**。

- **代码：** 本仓库中的源代码、配置文件和文档均基于[MIT许可证](LICENSE)提供。您可以自由使用、修改和分发代码，但需保留原始版权声明。
- **商标：** “Frigate”名称、“Frigate NVR”品牌以及Frigate标识是**Frigate, Inc.的商标**，**不**受MIT许可证的覆盖。

有关我们品牌资产可接受使用的详细信息，请参阅我们的[商标政策](TRADEMARK.md)。
~~~~~~~~

## 378 · nexus-js/ui

批次：additional20；用途：LIBRARY；集合：validation。
选择原因：维护者招募徽章和短文档有效知识。
文档版本：1162；正文 SHA-256：`d3946d8567dd142a67a351ccb4d83d5f044d743825b2e1b36880407835a20fde`。
正文风险标记：SHORT_BUT_USEFUL。

### K1-378-01 · KEEP · L3–L3

类型：MAINTENANCE；范围：CORE；规则：R08。
寻求维护者徽章含维护状态信息，不是纯装饰。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
![急需维护者](https://img.shields.io/badge/maintainers-wanted-red.svg)
~~~~~~~~

### K1-378-02 · KEEP · L5–L7

类型：REPO_IDENTITY；范围：VERSION_2；规则：R12。
短正文仍含版本和 SDK 定位。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
##### 版本 2

NexusUI 是一个 JavaScript 工具包，提供 Web Audio 接口和其他辅助方法，用于在浏览器中构建乐器。
~~~~~~~~

### K1-378-03 · KEEP · L9–L11

类型：SOURCE_POINTER；范围：CORE；规则：R09。
API 和文档链接可作后续导航，不虚构其内容。

后续去向：`POINTER_METADATA_CANDIDATE`。本轮未写入任何索引。

~~~~~~~~text
更多信息，请访问[网站](http://nexus-js.github.io/ui/)。

有关文档和教程，请查看[API](http://nexus-js.github.io/ui/api/)。
~~~~~~~~

## 421 · w3c/epub-rs-bugs

批次：additional20；用途：ISSUE_TRACKER；集合：validation。
选择原因：问题跟踪库不能作为现成阅读器推荐。
文档版本：1205；正文 SHA-256：`fc12fbc19843f4a51406347885a58de5aa05fa1972dcb04c12793607cb48233d`。
正文风险标记：未在已审计片段标记文档级风险。

### K1-421-01 · KEEP · L1–L5

类型：REPO_IDENTITY；范围：ISSUE_TRACKER；规则：R12。
仓库用于收集阅读系统问题，不是可部署的电子书阅读器。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
# EPUB 3 阅读系统缺陷仓库

这是一个专门用于收集和维护 EPUB 阅读系统中发现的缺陷状态的仓库。缺陷可能涉及显示、无障碍访问及其他问题。

我们希望这个仓库能成为内容创作者、内容转换者、软件开发者和阅读系统开发者之间开放的对话平台，以更好地理解现实世界中 EPUB 存在的问题以及预期表现。欢迎内容创作者记录他们发现的问题以及可能有用的变通方案，直到修复就位。欢迎阅读系统开发者分享他们正在努力解决的问题，或说明某些问题正在考虑解决中。
~~~~~~~~

### K1-421-02 · KEEP · L7–L16

类型：USAGE；范围：STUDY_CONTRIBUTION；规则：R12。
问题报告模板对参与标准和问题研究有用，不证明实现了阅读功能。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
# 为 EPUB 阅读系统缺陷仓库做贡献
* 请仅报告缺陷，不要报告新功能或增强请求
* 请阅读现有议题，确保不会创建重复的议题
* 请遵循[缺陷报告模板](https://github.com/w3c/epub-rs-bugs/blob/master/bug-report-template.md)

  * 对于每个议题，标题应清晰、简明地描述该缺陷

  * 议题应带有相应的主题、阅读系统标签以及您测试该议题时使用的设备/操作系统标签

  * 如果您是所提及的阅读系统的开发者，您可以添加 RS 标签，以注明该议题是否已被确认，或修复是否已在您的开发流程中
~~~~~~~~

## 561 · YaddyVirus/Poor-Mans-Filament-Tracker

批次：additional20；用途：APPLICATION；集合：validation。
选择原因：打印设备兼容、安全限制、重量估计误差。
文档版本：1345；正文 SHA-256：`abf89913c455db8db79e9c98a2a5c7de4f8890f2d5b60a58438bd7a0dcb1325a`。
正文风险标记：未在已审计片段标记文档级风险。

### K1-561-01 · KEEP · L15–L24

类型：CAPABILITY；范围：CORE；规则：R05。
幽默叙述中仍含无云依赖、Docker、局域网与 SQLite 信息，不能按语气全部删除。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
There are smart spool systems out there with RFID tags, companion apps,
and subscriptions. This is not one of them. This is a 100 MB Docker
container that talks to a Bambu Lab printer directly and figures out
which spool is loaded by staring really hard at the material and color.

No cloud. No proprietary tags sold in packs of four for the price of an
actual filament roll. Just a container, the printer's LAN credentials,
and a SQLite file quietly doing the bookkeeping — so nobody has to guess
whether that half-empty spool of "definitely PLA, probably black" is 40%
or 60% gone.
~~~~~~~~

### K1-561-02 · KEEP · L97–L104

类型：DEPLOYMENT；范围：BAMBU_LAN；规则：R06。
Docker、P1 或 X1 或 A1、LAN 访问条件是具体适配要求。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
### Requirements

- Docker (with Compose)
- A Bambu Lab printer (P1/X1/A1 series) with LAN access enabled
- Three values from the printer's own screen (Settings → Network /
  Device / WLAN): its IP, serial number, and LAN access code

Nothing to sign up for, nothing to buy.
~~~~~~~~

### K1-561-03 · KEEP · L118–L123

类型：DEPLOYMENT；范围：CONFIGURATION；规则：R05。
保留环境变量配置及凭据不可提交的警示，只是来源知识，不读取用户密钥。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
### Configuration

Everything is an environment variable, set in `.env` (copy
`.env.example` and fill in the blanks). The `.env` file is gitignored
for a reason — printer credentials and HA tokens belong on the machine,
not in a commit history.
~~~~~~~~

### K1-561-04 · KEEP · L168–L179

类型：LIMITATION；范围：CORE；规则：R05。
单打印机、品牌限制和重量估算为零的失败情形必须进入证据。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
## ⚠️ Current limitations

Reasons this is called the *poor man's* tracker and not "Enterprise
Filament Resource Planning Suite":

- **Bambu Lab only, one printer per instance.** The direct connection
  speaks Bambu's own MQTT/FTPS dialect. Other printers and this project
  have nothing to say to each other.
- **Weight is the slicer's estimate, not a scale reading.** It comes
  from the 3MF's `slice_info.config`, because a printer is not a postal
  scale and has no idea what a spool weighs. If the fetch fails, the
  print is recorded at 0 g and flagged for manual correction.
~~~~~~~~

### K1-561-05 · KEEP · L180–L198

类型：LIMITATION；范围：CORE；规则：R05。
无 RFID、成本估计、币种仅符号、无认证和单 SQLite 都影响推荐，不能过滤为负面噪声。

后续去向：`KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED`。本轮未写入任何索引。

~~~~~~~~text
- **No RFID, no NFC, no electronic spool ID of any kind.** Not paying
  for that is the whole point. Identification works by matching material
  and color against the library, which means two identical spools
  genuinely cannot be told apart by software — a one-time confirmation
  settles it, and the answer is remembered.
- **Cost is derived, not frozen at print time.** Per-print cost is
  computed live from the spool's *current* cost ÷ weight ratio. Editing
  a spool's cost later quietly recosts its past prints. Great for fixing
  typos; a footgun for anyone doing real accounting.
- **The currency picker changes a symbol, not a value.** No exchange
  rates, no conversion. It will not make the filament cheaper. Nothing
  will.
- **No login, no auth, nothing.** Anyone who can reach the container's
  port can see and edit every spool. This is a garage tool for a trusted
  home network — port-forwarding it to the open internet is a choice,
  and a bad one.
- **One SQLite file, one instance.** Perfectly happy tracking one
  person's filament habit. Not the backend for a print farm with a shift
  schedule.
~~~~~~~~

