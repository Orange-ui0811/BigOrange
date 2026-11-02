# 智能分析 A/B 原始内容对照

逐条保留模型生成结果与真正送入 Prompt 的证据。来源绑定不等于事实核实。

## agent_business / 第 1 轮 / A

耗时 58.93s；检索 0.45s；按标价估算 ¥0.0828。

### 输入

{"topics": ["Agent", "智能体"], "purpose": "赚钱/接单/创业", "experience": "有两年基础", "languages": ["TypeScript"], "timeCommitment": "三个月内", "customPurpose": "想改造成服务小团队的工具，商业价值是建议，不能承诺收益", "useRag": true, "usePersona": false, "preferBeginnerProjects": false, "preferCareerProjects": false}

### 实际 Prompt 证据

[candidateId=E1;evidenceId=E1;repo=datawhalechina/hello-agents;mode=RAW_FALLBACK] facts=English 中文 Hello-Agents 🤖 《从零开始构建智能体》

[candidateId=E2;evidenceId=E2;repo=microsoft/ai-agents-for-beginners;mode=RAW_FALLBACK] facts=AI 智能体入门 课程 AI Agents for Beginners 一门教你开始构建 AI 智能体所需一切知识的课程 GitHub license

[candidateId=E3;evidenceId=E3;repo=langchain-ai/langchain] facts=证据:ph/overview) —— 借助我们的低级智能体编排框架，构建能够可靠处理复杂任务的智能体; 集成 —— 聊天与嵌入模型、工具与工具包，以及更多; LangSmith 部署 —— 通过专为长时间运行、有状态工作流设计的平台，部署并扩展智能体

### 最终内容

```json
{
  "analysis": {
    "trend": "AI智能体方向热度高但拥挤，GitHub高星项目集中，竞争充分；适合从细分场景或垂直领域切入，如小团队工具。",
    "prospect": "AI智能体在服务小团队、自动化流程方面有落地机会，可通过改造现有工具、提供定制化服务实现商业价值。",
    "demand": "小团队需要低成本、易用的智能体工具来提升效率，尤其是结合提示工程与开发者工作流的协作类产品。",
    "summary": "智能体赛道拥挤但细分需求未被满足，建议先学基础，再改造团队工具尝试变现。"
  },
  "recommendations": [
    {
      "candidateId": "E1",
      "name": "Hello-Agents 从零构建智能体",
      "description": "中文教程《从零开始构建智能体》，系统学习 Agent 核心概念、架构与实现，适合快速入门并掌握可改造成服务的基础知识。",
      "difficulty": "入门",
      "difficultyReason": "教程性质，内容结构清晰，用户两年 TypeScript 基础可快速理解概念并动手实践。",
      "vibeCodingReason": "课程边学边练，非常适合在 AI 辅助下完成代码生成、调试和学习笔记整理。",
      "recommendReason": "您想通过 Agent 方向赚钱，但需先扎实基础。此教程中文友好，可直接作为知识储备，为后续改造团队工具提供理论支撑，避免盲目试错。",
      "prerequisites": "TypeScript 基础，了解基本编程概念",
      "evidenceIds": [
        "E1"
      ],
      "score": 92,
      "references": [
        {
          "evidenceIds": [
            "E1"
          ],
          "url": "https://github.com/datawhalechina/hello-agents",
          "name": "datawhalechina/hello-agents"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    },
    {
      "candidateId": "B:6",
      "name": "MonkeyCode AI 辅助编程工具",
      "description": "面向开发者的 AI 辅助编程与团队协作工具，聚焦提示工程与工作流结合，模块独立，易于二次开发和功能扩展。",
      "difficulty": "入门",
      "difficultyReason": "项目结构模块化，基于已有功能可快速上手贡献；TypeScript 基础足够对接并理解其前端交互。",
      "vibeCodingReason": "项目本身涉及 AI 生成代码和提示词管理，天然适合通过 AI 辅助添加工单管理、团队看板等新功能。",
      "recommendReason": "该项目可改造成服务小团队的专属智能体工作台，例如增加多项目管理、计费订阅，具备商业化潜力；且上手难度低，三个月内可实现 MVP 并推向早期用户。",
      "prerequisites": "TypeScript，基本 React 知识，了解提示工程",
      "evidenceIds": [
        "B:6"
      ],
      "score": 88,
      "references": [
        {
          "evidenceIds": [
            "B:6"
          ],
          "url": "https://github.com/chaitin/monkeycode",
          "name": "chaitin/monkeycode"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    }
  ],
  "learning": {
    "steps": [
      "第一步：完成《Hello-Agents》教程，掌握智能体设计原则与常见架构。",
      "第二步：克隆 MonkeyCode 仓库，熟悉其代码结构，尝试添加一个简单的 Agent 对话面板。",
      "第三步：围绕小团队需求增加功能（如任务分配、AI 辅助写周报），部署演示版本并收集反馈。"
    ],
    "timeline": "预计 8-12 周（每周投入 15 小时），基础学习 2 周，项目改造 6 周，测试上线 2 周。",
    "tips": "改造时优先保留核心提示工程逻辑，用现有接口扩展团队协作层；避免从零造轮子，可参考 LangChain 思想但暂不引入重框架。"
  }
}
```

## agent_business / 第 1 轮 / B

耗时 60.70s；检索 1.22s；按标价估算 ¥0.0923。

### 输入

{"topics": ["Agent", "智能体"], "purpose": "赚钱/接单/创业", "experience": "有两年基础", "languages": ["TypeScript"], "timeCommitment": "三个月内", "customPurpose": "想改造成服务小团队的工具，商业价值是建议，不能承诺收益", "useRag": true, "usePersona": false, "preferBeginnerProjects": false, "preferCareerProjects": false}

### 实际 Prompt 证据

[candidateId=E1;evidenceId=E1;repo=datawhalechina/hello-agents] facts=证据:从基础理论到实际应用，全面掌握智能体系统的设计与实现; 为此，我们发起了 Hello-Agents 项目，希望能为社区提供一本从零开始、理论与实战并重的智能体系统构建指南。; &emsp;&emsp;Hello-Agents 是 Datawhale 社区的 系统性智能体学习教程 。

[candidateId=E2;evidenceId=E2;repo=agent-analytics/awesome-multi-agent-orchestrators] facts=摘要:Awesome-multi-agent-orchestrators 是一个关于多智能体编排的精选列表与目录。基于TypeScript，提供测试与CI保障，有数据库支持和Docker配置，但许可证未明确。;能力:系统梳理多智能体编排领域的资源，展现领…

[candidateId=E3;evidenceId=E3;repo=777genius/agent-teams-ai] facts=摘要:Agent Teams AI 是一个多智能体协作平台，允许用户像管理团队一样指挥多个 AI 智能体自主执行任务、相互通信和审查，支持 200+ 模型和 75+ LLM 提供商。;证据:Agent Teams AI 是一个多智能体协作平台，允许用户像管理团队一样指挥多个 AI 智能体自主执行任务、相…

### 最终内容

```json
{
  "analysis": {
    "trend": "AI智能体方向持续高热，但头部项目已极度拥挤，多智能体协作与垂直应用开发正成为突破口。",
    "prospect": "多智能体平台和低代码AI工具在中小企业中有较大改造空间，可切入团队协作自动化赛道。",
    "demand": "小团队需要低成本、可定制的智能体协作系统来提升效率，市场对二次开发服务需求明确。",
    "summary": "Agent赛道拥挤但细分机会多，建议聚焦多智能体协作平台的二次开发与商业落地。"
  },
  "recommendations": [
    {
      "candidateId": "C:218",
      "name": "Dify 多模型 Agent 应用平台",
      "description": "开源 AI 应用开发平台，集成可视化编排、多模型管理、Agent 与工作流，支持私有化部署，可快速构建企业级 AI 助手。",
      "difficulty": "中等",
      "difficultyReason": "项目涉及前后端协作与多模型集成，但前端使用 TypeScript，用户两年经验可快速上手，后端需略懂 Python。",
      "vibeCodingReason": "平台本身是 AI 开发工具，可直接用于 Vibe Coding，通过 AI 生成工作流与 Agent，加速定制开发。",
      "recommendReason": "可基于 Dify 为小团队提供定制 AI 助手服务，私有化部署满足数据安全需求，二次开发市场空间大，作品集价值高，三个月内可完成原型并接单。",
      "prerequisites": "TypeScript、React、基础 Python/REST API、AI 概念",
      "evidenceIds": [
        "C:218"
      ],
      "score": 91,
      "references": [
        {
          "evidenceIds": [
            "C:218"
          ],
          "url": "https://github.com/langgenius/dify",
          "name": "langgenius/dify"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    },
    {
      "candidateId": "E3",
      "name": "Agent Teams AI 多智能体协作平台",
      "description": "多智能体协作平台，像管理团队一样指挥多个 AI 自主执行任务并相互审查，支持 200+ 模型与 75+ LLM 提供商。",
      "difficulty": "进阶",
      "difficultyReason": "需理解多智能体通信、任务编排与 LLM 调用链，架构较复杂，但用户两年 TypeScript 基础能应对挑战。",
      "vibeCodingReason": "多智能体平台天然适合 AI 辅助开发，可用智能体生成代码、审查逻辑，快速迭代团队协作工具。",
      "recommendReason": "可直接改造为小团队内部任务分配与自动化系统，商业场景明确，能体现智能体工程能力，三个月可打造 MVP 并验证商业可行性。",
      "prerequisites": "TypeScript、Node.js、LLM 基础、提示工程",
      "evidenceIds": [
        "E3"
      ],
      "score": 88,
      "references": [
        {
          "evidenceIds": [
            "E3"
          ],
          "url": "https://github.com/777genius/agent-teams-ai",
          "name": "777genius/agent-teams-ai"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    }
  ],
  "learning": {
    "steps": [
      "第1-2周：精读 Hello-Agents 教程，掌握智能体设计理论与多智能体协作原理。",
      "第3-6周：深入 Dify 或 Agent Teams AI 源码，理解架构与扩展点，动手实现第一个定制 Agent。",
      "第7-12周：围绕小团队需求开发完整工具，集成支付或权限，进行用户测试与商业化准备。"
    ],
    "timeline": "3 个月内，前 2 周打基础，中间 4 周熟悉项目并开发插件，最后 6 周打磨原型及商业验证。",
    "tips": "先选一个项目深度参与，避免分散；利用 AI 编程助手加速开发；及早与目标小团队沟通需求，确保产品方向。"
  }
}
```

## agent_business / 第 2 轮 / A

耗时 35.94s；检索 0.66s；按标价估算 ¥0.0550。

### 输入

{"topics": ["Agent", "智能体"], "purpose": "赚钱/接单/创业", "experience": "有两年基础", "languages": ["TypeScript"], "timeCommitment": "三个月内", "customPurpose": "想改造成服务小团队的工具，商业价值是建议，不能承诺收益", "useRag": true, "usePersona": false, "preferBeginnerProjects": false, "preferCareerProjects": false}

### 实际 Prompt 证据

[candidateId=E1;evidenceId=E1;repo=datawhalechina/hello-agents;mode=RAW_FALLBACK] facts=English 中文 Hello-Agents 🤖 《从零开始构建智能体》

[candidateId=E2;evidenceId=E2;repo=microsoft/ai-agents-for-beginners;mode=RAW_FALLBACK] facts=AI 智能体入门 课程 AI Agents for Beginners 一门教你开始构建 AI 智能体所需一切知识的课程 GitHub license

[candidateId=E3;evidenceId=E3;repo=langchain-ai/langchain] facts=证据:ph/overview) —— 借助我们的低级智能体编排框架，构建能够可靠处理复杂任务的智能体; 集成 —— 聊天与嵌入模型、工具与工具包，以及更多; LangSmith 部署 —— 通过专为长时间运行、有状态工作流设计的平台，部署并扩展智能体

### 最终内容

```json
{
  "analysis": {
    "trend": "AI智能体方向虽已拥挤，但面向小团队的垂直工具仍有细分机会，趋势持续上升。",
    "prospect": "将智能体改造为服务小团队的工具化产品，商业验证周期短，前景取决于落地场景的精准度。",
    "demand": "中小企业对自动化流程、内部知识库Agent的需求旺盛，但要求低代码、易部署。",
    "summary": "智能体赛道火热，可结合TypeScript基础从实践项目切入，快速构建团队协作工具。"
  },
  "recommendations": [
    {
      "candidateId": "C:218",
      "name": "Dify - 低代码AI应用开发平台",
      "description": "提供可视化的AI应用编排，支持快速构建和部署智能体、工作流，集成多种模型与工具，适合将Agent能力产品化。",
      "difficulty": "中等",
      "difficultyReason": "需要理解后端架构、多模型集成和私有化部署，但平台本身降低编码门槛，结合你的两年TypeScript基础可较快速掌握。",
      "vibeCodingReason": "平台具有直观的可视化编排界面，适合通过自然语言描述意图、一步步搭建Agent逻辑，AI辅助开发体验流畅。",
      "recommendReason": "直接匹配你赚钱/创业的目标，能快速产出面向小团队的Agent服务原型，展现从平台搭建到商业落地的完整能力链路，为求职或接单增加作品深度。",
      "prerequisites": "Node.js、Python基础、基本的LLM概念、RESTful API设计",
      "evidenceIds": [
        "C:218"
      ],
      "score": 91,
      "references": [
        {
          "evidenceIds": [
            "C:218"
          ],
          "url": "https://github.com/langgenius/dify",
          "name": "langgenius/dify"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    },
    {
      "candidateId": "B:6",
      "name": "MonkeyCode - AI辅助编程团队工具",
      "description": "结合AI辅助编程与团队工作流，帮助理解提示工程与开发者工具的集成，功能模块独立，适合TypeScript开发者上手。",
      "difficulty": "入门",
      "difficultyReason": "基于TypeScript且功能模块化，你的两年经验足以快速理解并贡献代码，复杂度低。",
      "vibeCodingReason": "项目本身就是围绕AI增强开发者效率，非常适合体验用AI代理辅助编码、调试和协作的氛围。",
      "recommendReason": "通过参与此项目，你既能巩固TypeScript技能，又能深入提示工程和开发者工具生态，为后续构建更复杂的智能体服务打好基础，成果可直接展示给潜在客户或雇主。",
      "prerequisites": "TypeScript、React或Node.js基础、Git协作",
      "evidenceIds": [
        "B:6"
      ],
      "score": 85,
      "references": [
        {
          "evidenceIds": [
            "B:6"
          ],
          "url": "https://github.com/chaitin/monkeycode",
          "name": "chaitin/monkeycode"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    }
  ],
  "learning": {
    "steps": [
      "第1-2周：通过《从零开始构建智能体》(hello-agents) 掌握Agent基本原理，完成微软AI Agents for Beginners课程",
      "第3-6周：深入学习LangChain框架，重点在智能体编排与工具集成，同时搭建Dify平台进行实践",
      "第7-12周：基于Dify开发面向小团队的具体Agent工具（如客服助手、内部知识库），并用MonkeyCode项目强化TypeScript实战"
    ],
    "timeline": "三个月内，每天投入2-3小时，可完成从理论到原型落地的全流程。",
    "tips": "优先选择自己或朋友团队的真实痛点作为Agent的服务场景，边学边验证需求，避免闭门造车。初期不要追求完美，快速出MVP在社交媒体分享，吸引潜在客户反馈。"
  }
}
```

## agent_business / 第 2 轮 / B

耗时 66.99s；检索 1.20s；按标价估算 ¥0.0916。

### 输入

{"topics": ["Agent", "智能体"], "purpose": "赚钱/接单/创业", "experience": "有两年基础", "languages": ["TypeScript"], "timeCommitment": "三个月内", "customPurpose": "想改造成服务小团队的工具，商业价值是建议，不能承诺收益", "useRag": true, "usePersona": false, "preferBeginnerProjects": false, "preferCareerProjects": false}

### 实际 Prompt 证据

[candidateId=E1;evidenceId=E1;repo=datawhalechina/hello-agents] facts=证据:从基础理论到实际应用，全面掌握智能体系统的设计与实现; 为此，我们发起了 Hello-Agents 项目，希望能为社区提供一本从零开始、理论与实战并重的智能体系统构建指南。; &emsp;&emsp;Hello-Agents 是 Datawhale 社区的 系统性智能体学习教程 。

[candidateId=E2;evidenceId=E2;repo=agent-analytics/awesome-multi-agent-orchestrators] facts=摘要:Awesome-multi-agent-orchestrators 是一个关于多智能体编排的精选列表与目录。基于TypeScript，提供测试与CI保障，有数据库支持和Docker配置，但许可证未明确。;能力:系统梳理多智能体编排领域的资源，展现领…

[candidateId=E3;evidenceId=E3;repo=777genius/agent-teams-ai] facts=摘要:Agent Teams AI 是一个多智能体协作平台，允许用户像管理团队一样指挥多个 AI 智能体自主执行任务、相互通信和审查，支持 200+ 模型和 75+ LLM 提供商。;证据:Agent Teams AI 是一个多智能体协作平台，允许用户像管理团队一样指挥多个 AI 智能体自主执行任务、相…

### 最终内容

```json
{
  "analysis": {
    "trend": "AI智能体方向持续高热，GitHub上85%的热门项目与AI辅助编程相关，Agent正深度融入开发工具链，但头部竞争激烈，需从垂直场景切入。",
    "prospect": "面向小团队的Agent协作工具需求显著，可定制、可私有化部署的轻量服务存在商业机会，尤其在任务自动化与多智能体协调领域。",
    "demand": "市场需要易于集成多模型、支持工作流编排和团队管理的Agent平台，TypeScript全栈方案更受前端开发者青睐。",
    "summary": "Agent方向拥挤但垂直需求明确，建议基于现有平台二次开发，聚焦团队协作场景。"
  },
  "recommendations": [
    {
      "candidateId": "C:218",
      "name": "Dify - AI应用与Agent平台",
      "description": "开源LLMOps平台，提供可视化的AI应用构建、Agent编排和私有化部署能力，支持多模型集成与团队协作，适合快速交付企业级智能助手。",
      "difficulty": "进阶",
      "difficultyReason": "涉及Python后端、React前端、Docker部署及复杂工作流设计，需要综合工程能力，但TypeScript基础可用于前端定制与扩展。",
      "vibeCodingReason": "平台已封装模型调用与编排逻辑，AI可辅助生成前端界面、任务流模板和插件代码，降低开发门槛。",
      "recommendReason": "契合Agent兴趣与赚钱目标，TypeScript技能可直接用于前端二次开发，私有化部署能快速包装成小团队工具，商业化路径清晰；三个月内可完成定制并推向市场。",
      "prerequisites": "TypeScript, React, Python基础, LLM概念, Docker",
      "evidenceIds": [
        "C:218"
      ],
      "score": 90,
      "references": [
        {
          "evidenceIds": [
            "C:218"
          ],
          "url": "https://github.com/langgenius/dify",
          "name": "langgenius/dify"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    },
    {
      "candidateId": "E3",
      "name": "Agent Teams AI - 多智能体协作平台",
      "description": "多智能体协作平台，允许像管理团队一样指挥多个AI代理自主执行任务、相互审查，支持200+模型和75+提供商，自然匹配团队协同场景。",
      "difficulty": "进阶",
      "difficultyReason": "需理解多智能体通信、任务分配与评价机制，系统集成复杂度高，但平台已提供核心编排能力，二次开发可通过TypeScript实现前端调度。",
      "vibeCodingReason": "AI可辅助生成智能体策略、通信协议和处理用户指令的代码片段，快速实验多代理协作模式。",
      "recommendReason": "直接命中Agent与多智能体方向，商业价值在于可改造为垂直行业的团队助理工具（如客服、研发协作），TypeScript有利于构建管理界面和定制代理行为，创业可行性高。",
      "prerequisites": "TypeScript, Agent基础理论, LLM调用经验",
      "evidenceIds": [
        "E3"
      ],
      "score": 88,
      "references": [
        {
          "evidenceIds": [
            "E3"
          ],
          "url": "https://github.com/777genius/agent-teams-ai",
          "name": "777genius/agent-teams-ai"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    }
  ],
  "learning": {
    "steps": [
      "系统学习Agent基础理论与多智能体架构（参考Datawhale的Hello-Agents教程 E1）",
      "深入所选平台源码（Dify或Agent Teams AI），搭建本地环境，理解核心流程与扩展点",
      "针对小团队场景设计功能（如任务自动分配、模型切换），用TypeScript实现前端定制或插件开发",
      "进行私有化部署测试，收集种子用户反馈并迭代"
    ],
    "timeline": "三个月：第1个月理论学习与环境搭建，第2个月功能开发与前端定制，第3个月测试、修改与商业化准备",
    "tips": "避免追求大而全，聚焦一个细分痛点（如设计稿转代码审核、客服话术自动优化）；善用AI辅助生成代码和文档；初期可采用免费模型降低运营成本。"
  }
}
```

## data_analysis / 第 1 轮 / A

耗时 30.87s；检索 0.58s；按标价估算 ¥0.0431。

### 输入

{"topics": ["数据分析"], "purpose": "求职", "experience": "有基础", "languages": ["Python"], "timeCommitment": "两个月内", "customPurpose": "想展示数据处理和可视化能力", "useRag": true, "usePersona": false, "preferBeginnerProjects": false, "preferCareerProjects": true}

### 实际 Prompt 证据

[candidateId=E1;evidenceId=E1;repo=apache/doris] facts=摘要:Apache Doris 是一个面向 AI 智能体的实时分析和混合搜索数据库。;能力:证明能够理解并修改分布式数据库核心模块，掌握 Java 性能调优、实时查询优化、混合搜索引擎设计。;技术:Java;证据:职业成长项目; Apache Doris 是一个面向 AI 智能体的实时分析和混合搜索数据库。; 证明能够理解并修…

[candidateId=E2;evidenceId=E2;repo=elastic/kibana] facts=摘要:Kibana 是Elastic Stack的可视化窗口，用于探索、分析和展示所有数据。基于TypeScript、React、Next.js构建，拥有完善的测试体系（Playwright、Storybook）和CI/CD。项目规模极大，Issue数量高达14491，无统一非断言许可证。;能力:具备大规模TypeScri…

[candidateId=E3;evidenceId=E3;repo=gupy-io/gupy-skills-candidato] facts=证据:最简单的方式是使用 Skills CLI （ npx skills ），它会将技能安装到每个工具的正确目录中。; js 18+ （用于 npx ）。

### 最终内容

```json
{
  "analysis": {
    "trend": "当前项目候选均偏向后端数据库（Apache Doris/Java）或前端可视化工具（Kibana/TypeScript），与数据分析领域流行的Python生态（Pandas、Streamlit等）技术栈不符。",
    "prospect": "直接参与这些项目无法有效展示 Python 数据处理与可视化能力，对求职目标的帮助有限；但若转向底层数据引擎开发，可提供长期架构经验，与当前目标偏离。",
    "demand": "求职市场更看重结合业务场景的端到端数据分析作品集，而非底层系统开发贡献，因此需要 Python 主导的数据分析项目。",
    "summary": "候选项目技术栈与用户Python基础及数据分析可视化求职目标不匹配，无合适推荐。"
  },
  "recommendations": [],
  "learning": {
    "steps": [
      "第一步：用 Pandas 清洗和探索公开数据集，完成描述性统计与异常检测",
      "第二步：使用 Matplotlib/Seaborn 或 Plotly 绘制核心图表，并构建 Streamlit 交互式仪表板",
      "第三步：撰写分析报告，突出数据洞察与可视化叙事，部署到云端（如 Streamlit Cloud）"
    ],
    "timeline": "预计 6-8 周完成一个完整的数据分析作品集项目",
    "tips": "优先选择有行业背景的数据集（如电商、金融），强化数据清洗与可视化表达，量化分析结论。"
  }
}
```

## data_analysis / 第 1 轮 / B

耗时 70.66s；检索 3.61s；按标价估算 ¥0.0865。

### 输入

{"topics": ["数据分析"], "purpose": "求职", "experience": "有基础", "languages": ["Python"], "timeCommitment": "两个月内", "customPurpose": "想展示数据处理和可视化能力", "useRag": true, "usePersona": false, "preferBeginnerProjects": false, "preferCareerProjects": true}

### 实际 Prompt 证据



### 最终内容

```json
{
  "analysis": {
    "trend": "数据分析正从离线报表转向实时分析与AI场景融合，大规模分析引擎和可视化平台成为企业数据基础设施的投入重点。",
    "prospect": "数据平台、实时分析、可视化交互岗位需求稳定，具备工程化数据处理与展示能力的候选人在求职中更具竞争力。",
    "demand": "求职者需要用项目证明自己能理解数据处理链路、分析查询与可视化呈现，而不是仅会写简单脚本。",
    "summary": "围绕实时分析引擎与可视化工具积累项目，突出数据处理、查询优化和展示能力。"
  },
  "recommendations": [
    {
      "candidateId": "C:210",
      "name": "Apache Doris 实时分析引擎",
      "description": "围绕大规模实时数据分析引擎进行设计与优化，理解混合搜索在 AI 场景中的应用，沉淀大数据查询与实时分析基础设施经验。",
      "difficulty": "进阶",
      "difficultyReason": "项目涉及大规模实时分析与分布式系统场景，用户有 Python 基础但需要补足大数据与系统设计知识，适合从文档、测试或局部模块切入。",
      "vibeCodingReason": "可通过 AI 辅助理解查询优化与混合搜索设计，并生成测试用例或 SQL 示例，降低大数据引擎的上手门槛。",
      "recommendReason": "与数据分析求职目标直接相关，能展示对大规模实时分析引擎的理解。通过参与局部模块和示例，可形成数据处理与系统优化方向的履历证据，两个月内可产出小贡献。",
      "prerequisites": "SQL、Python 基础、基本的数据仓库与分布式系统概念",
      "evidenceIds": [
        "C:210"
      ],
      "score": 86,
      "references": [
        {
          "evidenceIds": [
            "C:210"
          ],
          "url": "https://github.com/apache/doris",
          "name": "apache/doris"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    },
    {
      "candidateId": "C:260",
      "name": "Kibana 数据可视化平台",
      "description": "参与大规模数据可视化工具开发，理解 React 生态与测试策略，构建复杂数据展示与交互界面，展示复杂系统驾驭能力。",
      "difficulty": "进阶",
      "difficultyReason": "项目以数据可视化和前端交互为主，用户主要使用 Python，需要额外学习 React/TypeScript 生态，建议按组件级任务渐进参与。",
      "vibeCodingReason": "可视化界面与交互组件较多，AI 可辅助生成组件代码、测试用例与样式，提高从零学习前端生态的效率。",
      "recommendReason": "数据可视化是求职作品集的关键能力，参与 Kibana 可展示复杂数据展示与交互开发。虽然有前端栈门槛，但在 AI 辅助下可快速完成组件级任务并沉淀案例。",
      "prerequisites": "JavaScript/React 基础、数据可视化概念、测试基础",
      "evidenceIds": [
        "C:260"
      ],
      "score": 82,
      "references": [
        {
          "evidenceIds": [
            "C:260"
          ],
          "url": "https://github.com/elastic/kibana",
          "name": "elastic/kibana"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    }
  ],
  "learning": {
    "steps": [
      "第一步：通读项目文档与贡献指南，选择数据处理、分析查询或可视化组件相关的入门任务。",
      "第二步：本地搭建环境，基于官方示例跑通最小数据链路，并用 Python/SQL 模拟分析场景。",
      "第三步：完成一个小型修复、测试或文档改进，整理成可展示的项目案例。"
    ],
    "timeline": "预计 8 周：前 2 周熟悉架构与工具链，中间 4 周完成核心学习与首个任务，最后 2 周整理作品集与复盘。",
    "tips": "优先选择能形成清晰产出的任务，例如分析示例、测试用例、可视化组件优化；持续记录技术决策，便于面试时复述贡献。"
  }
}
```

## data_analysis / 第 2 轮 / A

耗时 50.14s；检索 0.57s；按标价估算 ¥0.0882。

### 输入

{"topics": ["数据分析"], "purpose": "求职", "experience": "有基础", "languages": ["Python"], "timeCommitment": "两个月内", "customPurpose": "想展示数据处理和可视化能力", "useRag": true, "usePersona": false, "preferBeginnerProjects": false, "preferCareerProjects": true}

### 实际 Prompt 证据

[candidateId=E1;evidenceId=E1;repo=apache/doris] facts=摘要:Apache Doris 是一个面向 AI 智能体的实时分析和混合搜索数据库。;能力:证明能够理解并修改分布式数据库核心模块，掌握 Java 性能调优、实时查询优化、混合搜索引擎设计。;技术:Java;证据:职业成长项目; Apache Doris 是一个面向 AI 智能体的实时分析和混合搜索数据库。; 证明能够理解并修…

[candidateId=E2;evidenceId=E2;repo=elastic/kibana] facts=摘要:Kibana 是Elastic Stack的可视化窗口，用于探索、分析和展示所有数据。基于TypeScript、React、Next.js构建，拥有完善的测试体系（Playwright、Storybook）和CI/CD。项目规模极大，Issue数量高达14491，无统一非断言许可证。;能力:具备大规模TypeScri…

[candidateId=E3;evidenceId=E3;repo=gupy-io/gupy-skills-candidato] facts=证据:最简单的方式是使用 Skills CLI （ npx skills ），它会将技能安装到每个工具的正确目录中。; js 18+ （用于 npx ）。

### 最终内容

```json
{
  "analysis": {
    "trend": "数据分析与AI结合紧密，实时分析数据库与数据可视化工具成为企业刚需。",
    "prospect": "大规模实时分析引擎与可视化平台相关岗位价值高，开源贡献经历可显著提升竞争力。",
    "demand": "求职需展示数据处理与可视化实战能力，参与真实大型项目比普通练习更有说服力。",
    "summary": "推荐Doris与Kibana，提升实时分析与可视化能力，匹配数据分析求职。"
  },
  "recommendations": [
    {
      "candidateId": "C:210",
      "name": "Apache Doris 实时分析数据库",
      "description": "Apache Doris 是面向 AI 智能体的实时分析和混合搜索数据库，项目涉及分布式数据库核心模块、Java 性能调优与实时查询优化；参与可深入理解大规模数据分析引擎设计与优化。",
      "difficulty": "进阶",
      "difficultyReason": "用户有 Python 基础但该项目以 Java 为主，涉及分布式系统与查询优化，需补充 Java 与数据库内核知识，两个月内完成有效贡献较紧张。",
      "vibeCodingReason": "AI 可辅助理解 Java 核心模块、生成测试与性能调优代码，降低陌生语言与大型代码库门槛，但分布式逻辑需人工校验。",
      "recommendReason": "该项目在职业成长项目中评分较高，能展示大规模实时数据分析引擎的设计与优化能力，与数据分析求职目标高度契合。参与查询优化或混合搜索模块可作为简历亮点，但需投入时间学习 Java 生态和分布式概念。",
      "prerequisites": "熟悉 Python 编程、SQL 基础；了解 Java 基本语法、分布式系统概念与数据库原理。",
      "evidenceIds": [
        "C:210"
      ],
      "score": 86,
      "references": [
        {
          "evidenceIds": [
            "C:210"
          ],
          "url": "https://github.com/apache/doris",
          "name": "apache/doris"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    },
    {
      "candidateId": "C:260",
      "name": "Elastic Kibana 数据可视化平台",
      "description": "Kibana 是 Elastic Stack 可视化窗口，用于探索、分析和展示数据；基于 TypeScript、React、Next.js 构建，拥有测试体系；可参与数据可视化功能与测试，项目规模较大。",
      "difficulty": "进阶",
      "difficultyReason": "用户以 Python 为主，项目使用 TypeScript/React/Next.js，需补前端栈与测试工具；大型项目结构复杂，两个月适合聚焦可视化或图表模块。",
      "vibeCodingReason": "AI 可快速生成 TypeScript 组件与 Playwright 测试草稿，辅助理解 React/Next.js 大型代码库，但用户需校验数据交互与可视化逻辑。",
      "recommendReason": "与数据分析求职中的可视化能力高度匹配，参与 Kibana 可展示大规模数据可视化工具开发经验、React 生态与测试策略，证明复杂系统驾驭能力，是展示数据处理与展示能力的优质履历项目。需关注前端学习曲线。",
      "prerequisites": "Python 基础、了解数据可视化概念；建议熟悉 JavaScript/TypeScript 基础、React 组件模型与 Git 协作。",
      "evidenceIds": [
        "C:260"
      ],
      "score": 82,
      "references": [
        {
          "evidenceIds": [
            "C:260"
          ],
          "url": "https://github.com/elastic/kibana",
          "name": "elastic/kibana"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    }
  ],
  "learning": {
    "steps": [
      "第一步：熟悉项目文档与开发环境，运行 Doris 或 Kibana 本地实例，选择适合的 good first issue。",
      "第二步：针对 Doris 补 Java 与 SQL 执行路径，或针对 Kibana 补 TypeScript/React 与图表组件；实现一个小型功能或修复。",
      "第三步：完善测试与文档，提交 PR 并响应评审，沉淀技术博客或简历项目说明。"
    ],
    "timeline": "预计周期：8周，每周投入10-15小时；前2周熟悉，中4周实现，后2周测试与PR。",
    "tips": "优先选择标记为 good first issue 的模块，聚焦单一功能而非全面铺开；利用 AI 辅助生成测试和代码骨架，但核心逻辑需人工验证；记录性能优化与问题定位过程，作为面试素材。"
  }
}
```

## data_analysis / 第 2 轮 / B

耗时 62.47s；检索 1.09s；按标价估算 ¥0.0952。

### 输入

{"topics": ["数据分析"], "purpose": "求职", "experience": "有基础", "languages": ["Python"], "timeCommitment": "两个月内", "customPurpose": "想展示数据处理和可视化能力", "useRag": true, "usePersona": false, "preferBeginnerProjects": false, "preferCareerProjects": true}

### 实际 Prompt 证据



### 最终内容

```json
{
  "analysis": {
    "trend": "实时分析、混合搜索与数据可视化正在成为数据技术栈的关键方向。",
    "prospect": "企业数据平台与AI应用结合紧密，实时分析与可视化能力是长期刚需。",
    "demand": "数据分析岗位既要求数据处理能力，也看重可视化呈现，项目贡献能补充实战证明。",
    "summary": "建议聚焦大数据分析引擎与可视化平台，用真实项目经历强化数据处理与可视化求职亮点。"
  },
  "recommendations": [
    {
      "candidateId": "C:210",
      "name": "Apache Doris 大规模实时数据分析引擎",
      "description": "Apache Doris 是面向大规模实时数据分析的引擎，支持混合搜索在AI场景的应用，适合积累大数据与AI基础设施架构经验。",
      "difficulty": "进阶",
      "difficultyReason": "用户有Python基础，但Doris核心涉及SQL与分布式系统，两月内适合从文档、测试与客户端工具切入。",
      "vibeCodingReason": "AI辅助可快速梳理大规模代码结构、生成SQL测试与数据分析示例，降低熟悉门槛。",
      "recommendReason": "契合数据分析求职目标，参与真实OLAP引擎能体现大规模数据处理、SQL分析与性能意识，让简历更具工程深度。",
      "prerequisites": "Python、SQL基础、基本分布式概念",
      "evidenceIds": [
        "C:210"
      ],
      "score": 86,
      "references": [
        {
          "evidenceIds": [
            "C:210"
          ],
          "url": "https://github.com/apache/doris",
          "name": "apache/doris"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    },
    {
      "candidateId": "C:260",
      "name": "Kibana 数据可视化平台",
      "description": "Kibana 是面向数据可视化的平台，涉及大规模数据可视化工具开发、React生态与测试策略，适合积累复杂前端与可视化工程经验。",
      "difficulty": "进阶",
      "difficultyReason": "用户Python背景，项目以TypeScript/React为主，需补齐前端基础，适合以可视化组件与测试方向切入。",
      "vibeCodingReason": "AI辅助可帮助理解前端组件结构、生成交互测试与可视化Demo，加速前端学习曲线。",
      "recommendReason": "求职目标强调可视化能力，Kibana是成熟数据可视化产品，参与其中能直接展示复杂数据展示与前端工程质量，补足数据分析的可视化呈现短板。",
      "prerequisites": "JavaScript/TypeScript基础、React、数据可视化概念",
      "evidenceIds": [
        "C:260"
      ],
      "score": 82,
      "references": [
        {
          "evidenceIds": [
            "C:260"
          ],
          "url": "https://github.com/elastic/kibana",
          "name": "elastic/kibana"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    }
  ],
  "learning": {
    "steps": [
      "明确Doris或Kibana的贡献方向，阅读架构文档与贡献指南",
      "用Python或SQL搭建本地示例，完成数据导入与可视化验证",
      "提交文档、测试或小功能PR，积累可展示项目经历"
    ],
    "timeline": "8周左右：前2周熟悉项目，第3-5周完成示例，第6-8周提交贡献",
    "tips": "聚焦数据分析与可视化相关模块，避免陷入底层存储或前端框架细节；保持每周稳定提交记录。"
  }
}
```

## printing_explore / 第 1 轮 / A

耗时 33.12s；检索 0.60s；按标价估算 ¥0.0459。

### 输入

{"topics": ["3D打印"], "purpose": "探索技术兴趣", "experience": "有基础", "languages": [], "timeCommitment": "三个月内", "customPurpose": "想了解开源切片或打印控制项目，说明硬件依赖", "useRag": true, "usePersona": false, "preferBeginnerProjects": false, "preferCareerProjects": false}

### 实际 Prompt 证据

[candidateId=E1;evidenceId=E1;repo=slic3r/slic3r] facts=证据:兼容： 支持所有已知的 G-code 方言（Marlin、Repetier、Mach3、LinuxCNC、Machinekit、Smoothie、Makerware、Sailfish）。; 模块化： Slic3r 的核心是 libslic3r，一个 C++ 库，提供细粒度的 API 和可重用组件。

[candidateId=E2;evidenceId=E2;repo=bencbartlett/3d-printed-mirror-array] facts=证据:修改代码来生成一个可3D打印的镜面阵列，将任意图像投射到任意焦平面上，是很容易的。; Python 3 和 Jupyter，并安装以下库： numpy 、 matplotlib 、 numpy-stl 、 hexy 、 vpython; 如果你不想渲染光线追踪可视化效果，…

[candidateId=E3;evidenceId=E3;repo=mainsail-crew/mainsail] facts=证据:打印机集群： 支持多台 3D 打印机; 文件管理器： 删除、重命名和上传你的 G-Code 及配置文件; 文件编辑器： 在浏览器中编辑 G-Code 和配置文件，支持语法高亮

### 最终内容

```json
{
  "analysis": {
    "trend": "3D打印领域开源项目成熟度适中，切片与控制类工具仍是核心需求，头部效应未完全形成，存在创新空间。",
    "prospect": "基于Web的打印管理、多机控制和模块化切片库具有良好的扩展前景，可结合AI优化参数与流程。",
    "demand": "用户希望探索切片与打印控制项目，需要明确硬件依赖，降低上手门槛，通过实际项目理解3D打印工作流。",
    "summary": "推荐经典切片引擎Slic3r和现代Web打印管理Mainsail，一个深入底层算法，一个聚焦集群控制，硬件依赖清晰。"
  },
  "recommendations": [
    {
      "candidateId": "E1",
      "name": "Slic3r 切片引擎",
      "description": "开源3D切片工具，将模型转换为G-code，支持多种打印机与控制板。提供C++核心库libslic3r，可深度定制切片逻辑。",
      "difficulty": "进阶",
      "difficultyReason": "项目以C++实现核心算法，涉及计算几何、路径规划等复杂逻辑，需要较强的代码阅读与调试能力。",
      "vibeCodingReason": "AI可辅助理解切片算法模块、自动生成G-code后处理脚本，或帮助编写自定义填充模式与支撑结构。",
      "recommendReason": "直接满足你对开源切片项目的探索需求，无需实体打印机即可运行和生成G-code，适合理解切片全流程。其C++核心库可让你深入修改算法，结合三个月时间可以完成模块分析或定制开发。",
      "prerequisites": "C++基础，了解3D打印切片基本概念（层高、填充、支撑等），能编译C++项目。",
      "evidenceIds": [
        "E1"
      ],
      "score": 85,
      "references": [
        {
          "evidenceIds": [
            "E1"
          ],
          "url": "https://github.com/slic3r/slic3r",
          "name": "slic3r/slic3r"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    },
    {
      "candidateId": "E3",
      "name": "Mainsail 打印管理平台",
      "description": "基于Web的3D打印机集群管理界面，支持文件管理、在线编辑G-code、多机监控，需配合Klipper使用。",
      "difficulty": "中等",
      "difficultyReason": "涉及前端界面、后端API与打印机固件联动，但作为Web应用使用通用技术栈，逻辑相对清晰。",
      "vibeCodingReason": "AI可快速生成前端组件、完善文件编辑器语法高亮规则，或辅助设计多机任务队列与状态监控逻辑。",
      "recommendReason": "如果你希望了解打印控制与集群管理，Mainsail是理想入口。硬件依赖明确（需至少一台支持Klipper的3D打印机），可动手部署真实环境，还能为日后智能工厂项目积累经验。",
      "prerequisites": "JavaScript/Vue.js基础，了解REST API和3D打印机通信协议（如Klipper），需一台可运行Klipper的打印机或模拟器。",
      "evidenceIds": [
        "E3"
      ],
      "score": 82,
      "references": [
        {
          "evidenceIds": [
            "E3"
          ],
          "url": "https://github.com/mainsail-crew/mainsail",
          "name": "mainsail-crew/mainsail"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    }
  ],
  "learning": {
    "steps": [
      "第一步：选定一个项目，搭建开发环境，编译/部署基础版本，验证功能（如用Slic3r切片测试立方体，或部署Mainsail连接打印机）。",
      "第二步：阅读核心模块代码，记录关键逻辑，利用AI辅助解读算法或组件交互，并尝试小修改以观察效果。",
      "第三步：基于兴趣实现一个定制功能，比如为Slic3r添加特殊填充图案，或为Mainsail增加定时任务功能，并撰写技术总结。"
    ],
    "timeline": "三个月，每月聚焦一个阶段，前两周环境准备，之后交替进行代码阅读与实践修改。",
    "tips": "优先选有明确硬件支持的方案；若暂无打印机，使用Slic3r的G-code预览和Mainsail的模拟模式依然能完成学习。利用AI生成测试用例或解释复杂代码片段，可以加速理解。"
  }
}
```

## printing_explore / 第 1 轮 / B

耗时 57.45s；检索 1.02s；按标价估算 ¥0.0916。

### 输入

{"topics": ["3D打印"], "purpose": "探索技术兴趣", "experience": "有基础", "languages": [], "timeCommitment": "三个月内", "customPurpose": "想了解开源切片或打印控制项目，说明硬件依赖", "useRag": true, "usePersona": false, "preferBeginnerProjects": false, "preferCareerProjects": false}

### 实际 Prompt 证据

[candidateId=E1;evidenceId=E1;repo=bencbartlett/3d-printed-mirror-array] facts=证据:因为这个项目带给我很多乐趣，而且我热爱开源代码，我决定公开我用来生成3D打印模型的所有源代码，并写一篇设计说明。; 修改代码来生成一个可3D打印的镜面阵列，将任意图像投射到任意焦平面上，是很容易的。

[candidateId=E2;evidenceId=E2;repo=mainsail-crew/mainsail] facts=证据:打印机集群： 支持多台 3D 打印机; 文件管理器： 删除、重命名和上传你的 G-Code 及配置文件; 文件编辑器： 在浏览器中编辑 G-Code 和配置文件，支持语法高亮

[candidateId=E3;evidenceId=E3;repo=iroslink/iblowy;mode=RAW_FALLBACK] facts=3D Printing All parts print without supports. Setting Value --------- ------- Material PETG Infill 20% Layer height 0.2mm Supports None All STL files are in the objs/ directory. Source CAD (Fusion 360 .f3d + .step ) is in cad/ . ---

### 最终内容

```json
{
  "analysis": {
    "trend": "3D打印方向热度高但本周暂无增长，20个仓库均星7.1K，成熟度适中，头部效应未完全形成。",
    "prospect": "生成式建模与打印控制两个细分方向仍有差异化探索空间。",
    "demand": "用户希望了解开源切片或打印控制项目，需要说明硬件依赖并动手实践。",
    "summary": "3D打印方向热度稳定，建议从打印控制与生成式建模两个项目入手探索。"
  },
  "recommendations": [
    {
      "candidateId": "E1",
      "name": "3D打印镜面阵列生成器",
      "description": "一个用于生成可3D打印镜面阵列的开源工具，通过修改代码参数即可将任意图像投射到任意焦平面，适合探索程序化建模与光学阵列设计。",
      "difficulty": "进阶",
      "difficultyReason": "项目涉及几何映射与代码参数调整，需要一定编程和3D打印基础；用户有编程经验，三个月可逐步上手，但整体难度偏高。",
      "vibeCodingReason": "生成式建模代码结构清晰，适合让AI辅助解释几何算法、修改参数并生成新的阵列配置。",
      "recommendReason": "该项目能帮助你从代码角度理解3D打印模型生成流程，而不仅是使用现成切片软件。通过修改参数观察图像与焦平面的映射，可建立对光学阵列和程序化建模的直观认识，适合有编程基础者在三个月内深入实践，但需要一台3D打印机或打印服务来验证成品。",
      "prerequisites": "基础编程、基础3D建模概念、3D打印基础操作。",
      "evidenceIds": [
        "E1"
      ],
      "score": 85,
      "references": [
        {
          "evidenceIds": [
            "E1"
          ],
          "url": "https://github.com/bencbartlett/3d-printed-mirror-array",
          "name": "bencbartlett/3d-printed-mirror-array"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    },
    {
      "candidateId": "E2",
      "name": "Mainsail 3D打印控制面板",
      "description": "面向3D打印机的控制面板，支持多台打印机集群管理、G-Code与配置文件的删除、重命名和上传，并可在浏览器中编辑文件，支持语法高亮。",
      "difficulty": "中等",
      "difficultyReason": "需要理解3D打印流程并完成部署配置，但对有基础的用户属于中等难度，主要门槛在于环境搭建与硬件接入。",
      "vibeCodingReason": "打印控制面板涉及配置文件和浏览器端交互，适合让AI辅助排查部署问题、理解文件管理流程并优化操作体验。",
      "recommendReason": "该项目能帮你了解打印机控制与管理链路，通过配置和操作面板管理多台打印机与G-Code文件，弥补从切片到打印执行之间的关键环节。需要准备可联网的3D打印机及配套控制服务，适合有基础的用户在三个月内搭建并熟悉打印控制流程。",
      "prerequisites": "基础3D打印机操作、G-Code文件基础、网络配置与基础服务部署概念。",
      "evidenceIds": [
        "E2"
      ],
      "score": 88,
      "references": [
        {
          "evidenceIds": [
            "E2"
          ],
          "url": "https://github.com/mainsail-crew/mainsail",
          "name": "mainsail-crew/mainsail"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    }
  ],
  "learning": {
    "steps": [
      "第一步：先搭建E2打印控制面板运行环境，熟悉G-Code文件管理与多打印机配置。",
      "第二步：阅读E1生成式建模代码，尝试修改参数生成简单镜面阵列模型。",
      "第三步：实际打印小样验证效果，结合AI辅助迭代模型或控制配置。"
    ],
    "timeline": "预计周期：2-4周熟悉环境与项目代码，1-2个月深入实践，3个月内完成可验证的小项目。",
    "tips": "建议先准备一台可用的3D打印机或使用打印服务；优先用AI辅助理解代码结构和参数；调试硬件时注意安全。"
  }
}
```

## printing_explore / 第 2 轮 / A

耗时 41.27s；检索 0.49s；按标价估算 ¥0.0619。

### 输入

{"topics": ["3D打印"], "purpose": "探索技术兴趣", "experience": "有基础", "languages": [], "timeCommitment": "三个月内", "customPurpose": "想了解开源切片或打印控制项目，说明硬件依赖", "useRag": true, "usePersona": false, "preferBeginnerProjects": false, "preferCareerProjects": false}

### 实际 Prompt 证据

[candidateId=E1;evidenceId=E1;repo=slic3r/slic3r] facts=证据:兼容： 支持所有已知的 G-code 方言（Marlin、Repetier、Mach3、LinuxCNC、Machinekit、Smoothie、Makerware、Sailfish）。; 模块化： Slic3r 的核心是 libslic3r，一个 C++ 库，提供细粒度的 API 和可重用组件。

[candidateId=E2;evidenceId=E2;repo=bencbartlett/3d-printed-mirror-array] facts=证据:修改代码来生成一个可3D打印的镜面阵列，将任意图像投射到任意焦平面上，是很容易的。; Python 3 和 Jupyter，并安装以下库： numpy 、 matplotlib 、 numpy-stl 、 hexy 、 vpython; 如果你不想渲染光线追踪可视化效果，…

[candidateId=E3;evidenceId=E3;repo=mainsail-crew/mainsail] facts=证据:打印机集群： 支持多台 3D 打印机; 文件管理器： 删除、重命名和上传你的 G-Code 及配置文件; 文件编辑器： 在浏览器中编辑 G-Code 和配置文件，支持语法高亮

### 最终内容

```json
{
  "analysis": {
    "trend": "3D打印开源生态持续活跃，切片和控制工具稳定迭代",
    "prospect": "个人制造和创客教育推动需求，定制化切片与控制前景广阔",
    "demand": "爱好者追求高效切片和远程控制，模块化项目如Slic3r和Mainsail满足不同层次需求",
    "summary": "开源3D打印项目丰富，Slic3r和Mainsail分别代表切片与控制方向，适合技术探索。"
  },
  "recommendations": [
    {
      "candidateId": "E1",
      "name": "Slic3r 开源切片软件",
      "description": "开源3D打印切片软件，兼容多种G-code方言，提供模块化C++开发库libslic3r，方便定制切片算法。",
      "difficulty": "进阶",
      "difficultyReason": "涉及C++和复杂的几何算法，用户有基础但需投入时间掌握硬件交互",
      "vibeCodingReason": "C++代码库允许AI辅助生成和修改切片算法，加速定制开发",
      "recommendReason": "作为经典开源切片项目，是探索3D打印底层技术的理想选择。用户可学习切片流程、G-code生成，并利用模块化库进行二次开发，深入理解打印控制。三个月可完成编译、实验和简单功能改造。",
      "prerequisites": "C++编程基础，3D打印基本概念",
      "evidenceIds": [
        "E1"
      ],
      "score": 85,
      "references": [
        {
          "evidenceIds": [
            "E1"
          ],
          "url": "https://github.com/slic3r/slic3r",
          "name": "slic3r/slic3r"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    },
    {
      "candidateId": "E3",
      "name": "Mainsail 打印机 Web 控制面板",
      "description": "基于Web的3D打印机控制界面，支持多打印机集群管理、G-code文件编辑与上传，提供文件管理器等功能。",
      "difficulty": "入门",
      "difficultyReason": "主要是前端配置和使用，用户有基础编程经验可快速上手",
      "vibeCodingReason": "Web前端技术栈（HTML/JS）适合AI辅助开发，可快速定制界面和功能。",
      "recommendReason": "聚焦打印控制，无需深入C++，可快速搭建远程管理环境。通过定制Mainsail，用户能学习打印机通信协议（如Klipper）和Web技术融合，三个月内可部署并实现个性化控制面板。",
      "prerequisites": "基本的Linux和Web知识，Klipper固件概念",
      "evidenceIds": [
        "E3"
      ],
      "score": 90,
      "references": [
        {
          "evidenceIds": [
            "E3"
          ],
          "url": "https://github.com/mainsail-crew/mainsail",
          "name": "mainsail-crew/mainsail"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    }
  ],
  "learning": {
    "steps": [
      "第一步：搭建3D打印实验环境（安装Klipper/Mainsail或Slic3r依赖）。",
      "第二步：熟悉目标项目（Slic3r编译与切片测试，Mainsail配置多打印机）。",
      "第三步：定制开发（修改切片参数或Web界面功能，利用AI辅助生成代码）。"
    ],
    "timeline": "三个月，前两个月学习与配置，第三个月定制开发。",
    "tips": "注意硬件兼容性，选用常见主控板；利用AI辅助阅读代码和生成修改。"
  }
}
```

## printing_explore / 第 2 轮 / B

耗时 67.78s；检索 1.13s；按标价估算 ¥0.1058。

### 输入

{"topics": ["3D打印"], "purpose": "探索技术兴趣", "experience": "有基础", "languages": [], "timeCommitment": "三个月内", "customPurpose": "想了解开源切片或打印控制项目，说明硬件依赖", "useRag": true, "usePersona": false, "preferBeginnerProjects": false, "preferCareerProjects": false}

### 实际 Prompt 证据

[candidateId=E1;evidenceId=E1;repo=bencbartlett/3d-printed-mirror-array] facts=证据:因为这个项目带给我很多乐趣，而且我热爱开源代码，我决定公开我用来生成3D打印模型的所有源代码，并写一篇设计说明。; 修改代码来生成一个可3D打印的镜面阵列，将任意图像投射到任意焦平面上，是很容易的。

[candidateId=E2;evidenceId=E2;repo=mainsail-crew/mainsail] facts=证据:打印机集群： 支持多台 3D 打印机; 文件管理器： 删除、重命名和上传你的 G-Code 及配置文件; 文件编辑器： 在浏览器中编辑 G-Code 和配置文件，支持语法高亮

[candidateId=E3;evidenceId=E3;repo=iroslink/iblowy;mode=RAW_FALLBACK] facts=3D Printing All parts print without supports. Setting Value --------- ------- Material PETG Infill 20% Layer height 0.2mm Supports None All STL files are in the objs/ directory. Source CAD (Fusion 360 .f3d + .step ) is in cad/ . ---

### 最终内容

```json
{
  "analysis": {
    "trend": "3D打印本周热度持平，20个热门仓库均星7.1K，成熟度适中，头部效应未完全形成，仍有差异化空间。",
    "prospect": "开源切片与打印控制需求稳定，兼具趣味性与硬件落地的生成建模项目值得探索。",
    "demand": "用户希望了解开源切片或打印控制项目，并说明硬件依赖，适合既有源码可学又能实际打印验证的方向。",
    "summary": "3D打印热度稳定，拥挤适中；推荐打印控制与生成建模项目，适合有基础者3个月探索。"
  },
  "recommendations": [
    {
      "candidateId": "E2",
      "name": "Mainsail 3D打印控制台",
      "description": "开源的3D打印机Web控制界面，支持多台打印机集群管理，可删除、重命名和上传G-Code与配置文件，并在浏览器中高亮编辑G-Code。",
      "difficulty": "中等",
      "difficultyReason": "项目涉及Web界面与打印文件管理逻辑，但功能边界清晰，有基础者可边读源码边修改。",
      "vibeCodingReason": "控制台模块和文件操作逻辑清晰，适合用AI辅助梳理代码结构并扩展界面或快捷操作。",
      "recommendReason": "你希望了解打印控制项目，E2覆盖多机集群、G-Code文件管理和浏览器编辑，能直观学习打印控制链路；3个月足够跑通并扩展一个小功能，适合有基础者深入。",
      "prerequisites": "基础编程、Web前端基础、G-code文件格式基础、3D打印控制流程概念",
      "evidenceIds": [
        "E2"
      ],
      "score": 90,
      "references": [
        {
          "evidenceIds": [
            "E2"
          ],
          "url": "https://github.com/mainsail-crew/mainsail",
          "name": "mainsail-crew/mainsail"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    },
    {
      "candidateId": "E1",
      "name": "3D打印镜面阵列生成器",
      "description": "开源生成可3D打印镜面阵列模型的源代码，并附带设计说明；修改代码即可将任意图像投射到任意焦平面，适合探索光学与3D打印结合。",
      "difficulty": "进阶",
      "difficultyReason": "涉及参数化模型生成与光学投影逻辑，数学与调试量较大，但源码完整且有设计说明，适合有基础者挑战。",
      "vibeCodingReason": "开源代码和设计说明清晰，AI可辅助解读参数、修改投影逻辑并生成测试模型。",
      "recommendReason": "它虽不是切片或控制项目，但能让你在3D打印中验证光学设计；修改代码并实际打印可同时锻炼编程、参数化建模和硬件调试，匹配你的技术兴趣与时间投入。",
      "prerequisites": "基础编程、几何/坐标变换基础、3D打印与切片流程了解",
      "evidenceIds": [
        "E1"
      ],
      "score": 85,
      "references": [
        {
          "evidenceIds": [
            "E1"
          ],
          "url": "https://github.com/bencbartlett/3d-printed-mirror-array",
          "name": "bencbartlett/3d-printed-mirror-array"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    }
  ],
  "learning": {
    "steps": [
      "第1步：运行并阅读 Mainsail 的文件管理与编辑器代码，理解G-code上传与多机管理流程。",
      "第2步：克隆镜面阵列项目，按设计说明在本机修改参数生成小尺寸模型。",
      "第3步：使用FDM打印机和PETG等常见参数做切片打印验证，并尝试给控制台增加一个便捷操作功能。"
    ],
    "timeline": "预计周期：第1个月熟悉打印控制；第2个月完成生成模型修改与首版打印；第3个月联调与扩展小功能。",
    "tips": "建议：优先保证安全，调打印控制时先小尺寸空跑；AI辅助读代码可行，但G-code、温度与硬件参数需人工核对。"
  }
}
```

## rag_career / 第 1 轮 / A

耗时 55.03s；检索 0.62s；按标价估算 ¥0.0884。

### 输入

{"topics": ["RAG", "知识库"], "purpose": "求职", "experience": "有一年基础", "languages": ["Java"], "timeCommitment": "半年内", "customPurpose": "希望做一个能写进简历的知识库问答项目", "useRag": true, "usePersona": false, "preferBeginnerProjects": false, "preferCareerProjects": true}

### 实际 Prompt 证据

[candidateId=E1;evidenceId=E1;repo=chatchat-space/langchain-chatchat;mode=RAW_FALLBACK] facts=Wolfram对话 ❌ ✅ 文生图 ❌ ✅ 本地知识库管理 ✅ ✅

[candidateId=E2;evidenceId=E2;repo=langgenius/dify] facts=摘要:Dify 是一个用 TypeScript/React 构建的可协作 AI 应用开发平台，支持 Agentic 工作流、RAG 管道，可在云/VPC/私有化部署，帮助团队从原型快速走到生产。;能力:熟悉大型全栈项目中前后端解耦与 API 设计 掌握基于工作流的 Agent、RAG 等核心 AI 应用模式 具备私有化部…

[candidateId=E3;evidenceId=E3;repo=triliumnext/trilium] facts=证据:Trilium Notes 是一款免费开源、跨平台的层级式笔记应用，专注于构建大型个人知识库。

### 最终内容

```json
{
  "analysis": {
    "trend": "RAG检索增强热度高但拥挤，本周综合评分46/100，通用方案竞争充分，更适合从细分场景或垂直领域切入。",
    "prospect": "企业私有化知识库问答需求持续增长，Dify、Langchain-Chatchat等平台加速AI应用落地，是简历中可工程化展示的方向。",
    "demand": "企业知识库、客服与内部问答需求明确，但竞争者多，差异点在于垂直行业适配、私有化部署与端到端落地能力。",
    "summary": "聚焦知识库问答，主推Dify与Langchain-Chatchat，半年内完成从部署、集成到简历项目包装。"
  },
  "recommendations": [
    {
      "candidateId": "C:218",
      "name": "Dify 可协作 AI 应用平台",
      "description": "可协作的 AI 应用开发平台，支持 Agentic 工作流、RAG 管道，可在云/VPC/私有化部署，帮助团队从原型快速走到生产。",
      "difficulty": "进阶",
      "difficultyReason": "用户仅有一年 Java 基础，Dify 涉及 TypeScript/React 全栈、工作流与私有化部署，技术面较广，但可借助平台能力降低部分门槛。",
      "vibeCodingReason": "平台提供可视化工作流与 API，重复代码少，适合用 AI 辅助生成集成代码、排查部署问题并快速搭建 RAG 管道。",
      "recommendReason": "Dify 能直接体现求职者理解复杂 AI 应用平台、前后端协作、多模型集成和私有化部署的能力，面试中可结合企业知识库问答场景展示从原型到生产的落地经验。",
      "prerequisites": "了解 RAG 基本流程与向量检索，熟悉 Docker 部署，具备 API 调用能力，能阅读 TypeScript/React 代码更佳。",
      "evidenceIds": [
        "C:218"
      ],
      "score": 90,
      "references": [
        {
          "evidenceIds": [
            "C:218"
          ],
          "url": "https://github.com/langgenius/dify",
          "name": "langgenius/dify"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    },
    {
      "candidateId": "E1",
      "name": "Langchain-Chatchat 本地知识库问答",
      "description": "围绕本地知识库管理构建的 AI 应用，支持本地知识库管理能力，可作为知识库问答系统的源码级学习与二次开发基础。",
      "difficulty": "中等",
      "difficultyReason": "用户有 Java 基础，逻辑与工程习惯可迁移，但项目以 Python 生态为主，需要补充 Python、向量数据库与检索链路知识。",
      "vibeCodingReason": "RAG 链路模块清晰，AI 可辅助阅读源码、补全文档解析、向量化与问答接口逻辑，加速二次开发与问题定位。",
      "recommendReason": "可以通过源码学习和改造完成一个知识库问答 Demo，理解文档加载、分块、召回与回答生成全链路，并结合 Java 服务端封装形成完整求职项目。",
      "prerequisites": "Python 基础、向量数据库基本概念、REST API 设计基础。",
      "evidenceIds": [
        "E1"
      ],
      "score": 84,
      "references": [
        {
          "evidenceIds": [
            "E1"
          ],
          "url": "https://github.com/chatchat-space/langchain-chatchat",
          "name": "chatchat-space/langchain-chatchat"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    }
  ],
  "learning": {
    "steps": [
      "第1步：学习 RAG 核心链路，理解文档解析、分块、嵌入、召回与生成。",
      "第2步：本地部署 Dify 或 Langchain-Chatchat，跑通知识库问答闭环。",
      "第3步：结合 Java 服务端调用平台 API，完成垂直场景 Demo，如企业文档问答、引用溯源与权限隔离。",
      "第4步：补充评估指标、私有化部署方案、架构图与文档，整理成简历项目。"
    ],
    "timeline": "约 6 个月，每周投入 10-15 小时。",
    "tips": "聚焦一个细分行业或场景，突出工程落地与量化效果，避免只做通用 Demo。"
  }
}
```

## rag_career / 第 1 轮 / B

耗时 83.76s；检索 1.45s；按标价估算 ¥0.1299。

### 输入

{"topics": ["RAG", "知识库"], "purpose": "求职", "experience": "有一年基础", "languages": ["Java"], "timeCommitment": "半年内", "customPurpose": "希望做一个能写进简历的知识库问答项目", "useRag": true, "usePersona": false, "preferBeginnerProjects": false, "preferCareerProjects": true}

### 实际 Prompt 证据

[candidateId=E1;evidenceId=E1;repo=pathwaycom/llm-app] facts=证据:本仓库提供的应用模板可扩展至 数百万页文档 。; 问答 RAG 应用 基础端到端 RAG 应用。; 一个问答流水线，使用所选的 GPT 模型，根据实时连接的数据源（文件、Google Drive、Sharepoint 等）中的文档（PDF、DOCX 等）为查询提供答案。

[candidateId=E2;evidenceId=E2;repo=quivrhq/quivr] facts=证据:基础 RAG; 创建如上所示的基础 RAG 工作流很简单，步骤如下：; 自定义 RAG ：Quivr 允许你自定义 RAG，添加互联网搜索、集成工具等。

[candidateId=E3;evidenceId=E3;repo=hkuds/deeptutor;mode=RAW_FALLBACK] facts=Each chapter compiles into editable typed blocks — text, callouts, quizzes, flash cards, timelines, code, figures, interactive HTML, animations, concept graphs, deep dives, and user notes — with its own Page Chat. Insert, move, regenerate, rewrite, or switch a block type; selected p…

### 最终内容

```json
{
  "analysis": {
    "trend": "RAG检索增强方向本周热度高但拥挤，85%热门项目与AI辅助编程直接相关，竞争充分，建议从细分场景切入。",
    "prospect": "企业知识库问答、私有化RAG需求持续存在，端到端RAG与可扩展文档处理能力是简历中的有效亮点。",
    "demand": "求职者需要可演示的知识库问答项目，体现RAG链路、多数据源接入与自定义扩展能力。",
    "summary": "RAG方向拥挤但需求真实，建议以端到端知识库问答项目切入，突出可扩展与自定义能力。"
  },
  "recommendations": [
    {
      "candidateId": "E1",
      "name": "端到端问答RAG应用模板",
      "description": "该问答RAG应用模板可基于所选GPT模型，从文件、Google Drive、SharePoint等实时数据源读取PDF、DOCX等文档回答查询，端到端RAG流水线可扩展至数百万页文档。",
      "difficulty": "中等",
      "difficultyReason": "用户有一年Java基础，具备工程实现能力，但RAG涉及大模型API、文档加载与检索链路，需要额外学习，整体为中等难度。",
      "vibeCodingReason": "模板端到端结构清晰，RAG热门项目85%与AI辅助编程相关，适合借助AI快速定制数据源、提示词与部署流程。",
      "recommendReason": "该项目直接对应知识库问答岗位需求：端到端RAG流水线、多数据源接入和百万页扩展能力能成为简历亮点。结合一年Java基础，可通过集成服务端与API完成可演示项目，并在面试中说明文档问答链路。",
      "prerequisites": "建议了解大模型API调用、文档处理概念与基础服务端集成；Java基础可迁移到后端对接。",
      "evidenceIds": [
        "E1"
      ],
      "score": 90,
      "references": [
        {
          "evidenceIds": [
            "E1"
          ],
          "url": "https://github.com/pathwaycom/llm-app",
          "name": "pathwaycom/llm-app"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    },
    {
      "candidateId": "E2",
      "name": "Quivr自定义RAG知识库",
      "description": "Quivr提供基础RAG工作流创建能力，并支持自定义RAG以添加互联网搜索、集成工具等，可构建个性化知识库问答场景。",
      "difficulty": "中等",
      "difficultyReason": "基础RAG工作流创建简单，但自定义RAG与工具集成需要一定工程能力；用户有一年Java基础，可在理解链路后逐步扩展。",
      "vibeCodingReason": "Quivr支持自定义RAG，AI辅助可实现互联网搜索与工具集成等扩展，降低开发门槛，适合快速迭代。",
      "recommendReason": "Quivr自定义RAG能力可展示你从基础RAG到工具集成的进阶实践，适合作为第二个项目补充知识库问答的技术广度，证明你能根据业务需求扩展检索和工具能力。",
      "prerequisites": "建议先理解基础RAG工作流，再学习工具集成与自定义检索逻辑；具备Java基础有助于服务端改造。",
      "evidenceIds": [
        "E2"
      ],
      "score": 85,
      "references": [
        {
          "evidenceIds": [
            "E2"
          ],
          "url": "https://github.com/quivrhq/quivr",
          "name": "quivrhq/quivr"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    }
  ],
  "learning": {
    "steps": [
      "运行E1模板并接入大模型API，跑通PDF/云盘文档问答，理解端到端RAG数据流。",
      "学习文档加载、切分、向量检索和生成链路，改造数据源与提示词，并扩展更多文档格式。",
      "基于E2自定义RAG添加互联网搜索或工具集成，整理项目架构、效果与部署方式，写入简历并准备面试讲解。"
    ],
    "timeline": "建议4-6个月：第1-2个月跑通E1，第3-4个月深入RAG链路与优化，第5-6个月完成E2扩展和简历打磨。",
    "tips": "用Java基础对接API与后端服务，核心RAG部分可借助AI辅助生成；面试重点说明架构选型、检索质量与扩展性，而非只展示界面。"
  }
}
```

## rag_career / 第 2 轮 / A

耗时 34.92s；检索 0.44s；按标价估算 ¥0.0524。

### 输入

{"topics": ["RAG", "知识库"], "purpose": "求职", "experience": "有一年基础", "languages": ["Java"], "timeCommitment": "半年内", "customPurpose": "希望做一个能写进简历的知识库问答项目", "useRag": true, "usePersona": false, "preferBeginnerProjects": false, "preferCareerProjects": true}

### 实际 Prompt 证据

[candidateId=E1;evidenceId=E1;repo=chatchat-space/langchain-chatchat;mode=RAW_FALLBACK] facts=Wolfram对话 ❌ ✅ 文生图 ❌ ✅ 本地知识库管理 ✅ ✅

[candidateId=E2;evidenceId=E2;repo=langgenius/dify] facts=摘要:Dify 是一个用 TypeScript/React 构建的可协作 AI 应用开发平台，支持 Agentic 工作流、RAG 管道，可在云/VPC/私有化部署，帮助团队从原型快速走到生产。;能力:熟悉大型全栈项目中前后端解耦与 API 设计 掌握基于工作流的 Agent、RAG 等核心 AI 应用模式 具备私有化部…

[candidateId=E3;evidenceId=E3;repo=triliumnext/trilium] facts=证据:Trilium Notes 是一款免费开源、跨平台的层级式笔记应用，专注于构建大型个人知识库。

### 最终内容

```json
{
  "analysis": {
    "trend": "RAG 检索增强方向仍保持高热度但头部项目竞争饱和，细分领域的可私有化部署与质量保障工具关注度上升。",
    "prospect": "半年内构建可落地的知识库问答系统在求职市场具备强竞争力，尤其是能体现端到端工程化、可测试、可部署的完整方案。",
    "demand": "企业需求从单纯的模型调用转向可协作、可评估、可私有化的 AI 应用平台，验证与测试能力成为新加分项。",
    "summary": "聚焦可私有化部署的知识库问答平台与质量保障，打造扎实的项目经验。"
  },
  "recommendations": [
    {
      "candidateId": "C:218",
      "name": "Dify AI 应用开发平台",
      "description": "可协作的 AI 应用开发平台，内置 Agentic 工作流与 RAG 管道，支持私有化部署，快速构建知识库问答等应用。",
      "difficulty": "进阶",
      "difficultyReason": "项目涉及前后端解耦、多模型集成、工作流编排，需要同时理解 Python 后端与 TypeScript/React 前端，对架构设计和工程化要求较高。",
      "vibeCodingReason": "Dify 提供可视化编排界面与清晰的 API 设计，配合 AI 辅助可快速生成 RAG 管道组件、测试用例和部署脚本，降低集成复杂度。",
      "recommendReason": "直接命中 RAG 与知识库方向，可完整展示从知识库管理到问答应用搭建的全流程。在简历中能体现 AI 全栈能力、私有化部署经验及协作平台设计思维，对求职 AI 应用与 Agent 岗位极具说服力。",
      "prerequisites": "Python 基础、React 或 Vue 前端概念、容器化部署知识",
      "evidenceIds": [
        "C:218"
      ],
      "score": 91,
      "references": [
        {
          "evidenceIds": [
            "C:218"
          ],
          "url": "https://github.com/langgenius/dify",
          "name": "langgenius/dify"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    },
    {
      "candidateId": "C:224",
      "name": "Promptfoo AI 测试框架",
      "description": "专业的提示词测试与安全检测工具，支持 CI/CD 集成，可评估 RAG 应用的回答质量、事实一致性与安全性。",
      "difficulty": "中等",
      "difficultyReason": "项目核心是用 Node.js 编写测试用例与配置，逻辑直不涉及全栈开发，但需理解提示词工程和自动化测试原理。",
      "vibeCodingReason": "AI 可自动生成针对 RAG 的测试用例模板、安全扫描规则并实时分析回归结果，轻松搭建可扩展的测试体系。",
      "recommendReason": "作为知识库问答项目的质量保障补充，能够证明候选人具备 AI 应用测试、安全评估与 CI/CD 集成的工程思维，让简历中的项目更显专业与可信赖。",
      "prerequisites": "JavaScript/Node.js 基础、提示词基础、CI 流程概念",
      "evidenceIds": [
        "C:224"
      ],
      "score": 87,
      "references": [
        {
          "evidenceIds": [
            "C:224"
          ],
          "url": "https://github.com/promptfoo/promptfoo",
          "name": "promptfoo/promptfoo"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    }
  ],
  "learning": {
    "steps": [
      "第一步：基于 Dify 部署私有化环境，导入本地文档构建知识库，并开发一个 REST API 的问答服务，熟悉 RAG 管道配置。",
      "第二步：使用 Dify 的工作流编排自定义 Agent，增加多轮对话、工具调用等功能，提升问答体验。",
      "第三步：引入 Promptfoo 为知识库问答编写评估用例，设置事实一致性、毒性检测等测试，并集成到 GitHub Actions 实现持续测试。",
      "第四步：优化私有化部署方案，编写部署文档与使用说明，完善项目 README 并录制演示视频。"
    ],
    "timeline": "预计 4-6 个月，前 3 个月专注 Dify 搭建与功能完善，后 2 个月加入测试体系与简历包装。",
    "tips": "建议在项目 README 中突出‘私有化 RAG 知识库问答系统’的完整链路，面试时重点讲解 RAG 召回优化与测试驱动的质量保障流程。"
  }
}
```

## rag_career / 第 2 轮 / B

耗时 99.31s；检索 1.37s；按标价估算 ¥0.1717。

### 输入

{"topics": ["RAG", "知识库"], "purpose": "求职", "experience": "有一年基础", "languages": ["Java"], "timeCommitment": "半年内", "customPurpose": "希望做一个能写进简历的知识库问答项目", "useRag": true, "usePersona": false, "preferBeginnerProjects": false, "preferCareerProjects": true}

### 实际 Prompt 证据

[candidateId=E1;evidenceId=E1;repo=pathwaycom/llm-app] facts=证据:本仓库提供的应用模板可扩展至 数百万页文档 。; 问答 RAG 应用 基础端到端 RAG 应用。; 一个问答流水线，使用所选的 GPT 模型，根据实时连接的数据源（文件、Google Drive、Sharepoint 等）中的文档（PDF、DOCX 等）为查询提供答案。

[candidateId=E2;evidenceId=E2;repo=quivrhq/quivr] facts=证据:基础 RAG; 创建如上所示的基础 RAG 工作流很简单，步骤如下：; 自定义 RAG ：Quivr 允许你自定义 RAG，添加互联网搜索、集成工具等。

[candidateId=E3;evidenceId=E3;repo=hkuds/deeptutor;mode=RAW_FALLBACK] facts=Each chapter compiles into editable typed blocks — text, callouts, quizzes, flash cards, timelines, code, figures, interactive HTML, animations, concept graphs, deep dives, and user notes — with its own Page Chat. Insert, move, regenerate, rewrite, or switch a block type; selected p…

### 最终内容

```json
{
  "analysis": {
    "trend": "RAG检索增强热度高，85%热门项目与AI辅助编程相关，但赛道拥挤，适合从知识库问答等细分场景切入。",
    "prospect": "知识库问答是企业AI落地核心场景，实时数据接入、私有化部署与可扩展RAG能力将成为求职差异化优势。",
    "demand": "市场需要能搭建端到端RAG应用、理解平台架构与多模型集成的候选人，尤其欢迎有可演示作品的求职者。",
    "summary": "RAG热度高但拥挤，建议聚焦知识库问答与可扩展/私有化能力，选择端到端RAG模板与开源平台项目积累简历素材。"
  },
  "recommendations": [
    {
      "candidateId": "E1",
      "name": "Pathway 实时文档问答 RAG 应用",
      "description": "基于Pathway的端到端问答RAG模板，支持从PDF、DOCX、Google Drive等实时数据源检索并提供答案，可扩展至数百万页文档。",
      "difficulty": "中等",
      "difficultyReason": "项目链路清晰，但涉及Python、向量检索与LLM调用，对一年Java基础用户略跨栈，属中等难度。",
      "vibeCodingReason": "模板化程度高，AI可辅助生成数据源连接、检索链路与提示词修改，降低跨语言实现成本。",
      "recommendReason": "直接对标知识库问答求职方向：完成后可展示端到端RAG系统、多数据源接入与百万页扩展能力，弥补Java背景在AI应用上的不足，适合写进简历。",
      "prerequisites": "Python基础、RAG基本概念、OpenAI API或同类LLM调用、向量数据库基础",
      "evidenceIds": [
        "E1"
      ],
      "score": 88,
      "references": [
        {
          "evidenceIds": [
            "E1"
          ],
          "url": "https://github.com/pathwaycom/llm-app",
          "name": "pathwaycom/llm-app"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    },
    {
      "candidateId": "C:218",
      "name": "Dify 开源 AI 应用与 Agent 平台",
      "description": "开源AI应用与Agent平台，能展现复杂平台架构、前后端协作、多模型集成与私有化部署能力，可作为知识库问答服务的工程基座。",
      "difficulty": "中等",
      "difficultyReason": "平台降低底层复杂度，一年Java基础可低代码上手；二次开发与私有化部署需补Python、Docker，属中等难度。",
      "vibeCodingReason": "平台可视化编排与模板丰富，AI可辅助生成知识库检索策略、Prompt与API集成代码，快速形成作品集。",
      "recommendReason": "求职时可展示基于开源平台构建企业级知识库问答系统的工程能力，涵盖多模型集成、前后端协作与私有化部署，与AI应用与Agent岗位高度匹配。",
      "prerequisites": "Docker基础、REST API、大模型基础概念、Python或Java调用示例",
      "evidenceIds": [
        "C:218"
      ],
      "score": 91,
      "references": [
        {
          "evidenceIds": [
            "C:218"
          ],
          "url": "https://github.com/langgenius/dify",
          "name": "langgenius/dify"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    }
  ],
  "learning": {
    "steps": [
      "用2周补Python基础与LLM API调用，复现E1基础RAG问答链路。",
      "用2个月扩展E1多数据源与检索优化，并基于C:218搭建知识库问答应用，完成私有化部署。",
      "用2-3个月完善项目文档与面试话术，补充前后端协作与多模型集成，整理可演示MVP。"
    ],
    "timeline": "约5-6个月",
    "tips": "优先做可演示MVP，把扩展性、私有化部署作为简历亮点；Java基础可迁移到API服务化，不必放弃Java。"
  }
}
```

## rag_python / 第 1 轮 / A

耗时 36.63s；检索 0.50s；按标价估算 ¥0.0548。

### 输入

{"topics": ["RAG"], "purpose": "学习", "experience": "有基础", "languages": ["Python"], "timeCommitment": "两个月内", "customPurpose": "中文提问，检索英文技术文档，说明部署依赖", "useRag": true, "usePersona": false, "preferBeginnerProjects": true, "preferCareerProjects": false}

### 实际 Prompt 证据

[candidateId=E1;evidenceId=E1;repo=langgenius/dify;mode=RAW_FALLBACK] facts=ures such as text-to-speech to a chat-based app. 4. RAG Pipeline : Extensive RAG capabilities that cover everything from document ingestion to retrieval, with out-of-box support for text extraction from PDFs, PPTs, and other common document formats.

[candidateId=E2;evidenceId=E2;repo=xinhuangcs/agentmaker;mode=RAW_FALLBACK] facts=and remember I'm vegetarian.").final output) Every argument past llm is optional, so you add capabilities one at a time, and the same pattern reaches the rest of the framework: RAG retrieval as another sources= entry, MCP servers and sub-agents ( AgentTool ) as tools= , a Smar…

[candidateId=E3;evidenceId=E3;repo=pathwaycom/llm-app] facts=证据:问答 RAG 应用 基础端到端 RAG 应用。; 一个问答流水线，使用所选的 GPT 模型，根据实时连接的数据源（文件、Google Drive、Sharepoint 等）中的文档（PDF、DOCX 等）为查询提供答案。

### 最终内容

```json
{
  "analysis": {
    "trend": "RAG方向拥挤，但AI辅助编程渗透率高，可从垂直场景（如跨语言检索）切入。",
    "prospect": "RAG是LLM落地的核心范式，学习其管道搭建极具长期价值。",
    "demand": "中文提问英文文档的需求明确，适合以RAG构建双语知识检索工具。",
    "summary": "RAG方向成熟且竞争激烈，建议从基础端到端应用入手，再深入全功能平台实战。"
  },
  "recommendations": [
    {
      "candidateId": "E3",
      "name": "Pathway LLM-App 基础问答RAG",
      "description": "基础端到端RAG问答应用，可连接实时数据源（文件、Google Drive等），使用GPT模型回答用户问题。",
      "difficulty": "中等",
      "difficultyReason": "管道清晰、代码量适中，但需理解数据源连接和嵌入检索流程，适合有Python基础的学习者。",
      "vibeCodingReason": "项目结构简单，AI可辅助快速修改检索逻辑、切换数据源或扩展多语言支持。",
      "recommendReason": "作为RAG入门首选，能在两周内跑通端到端流程，快速建立对文档索引、检索和生成的核心认知，后续可无缝扩展到中文提问英文文档的场景。",
      "prerequisites": "Python、OpenAI API 基础",
      "evidenceIds": [
        "E3"
      ],
      "score": 85,
      "references": [
        {
          "evidenceIds": [
            "E3"
          ],
          "url": "https://github.com/pathwaycom/llm-app",
          "name": "pathwaycom/llm-app"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    },
    {
      "candidateId": "E1",
      "name": "Dify 全功能RAG与LLM应用平台",
      "description": "开源LLM应用开发平台，提供完整RAG管道，涵盖文档摄取、检索，开箱支持PDF、PPT等格式的文本提取。",
      "difficulty": "进阶",
      "difficultyReason": "平台功能全面，涉及前端、后端、向量数据库等组件，需深度学习其RAG管道实现与部署。",
      "vibeCodingReason": "源码使用Python，AI可辅助阅读RAG管道代码、解析文档处理逻辑，并帮助定制检索策略。",
      "recommendReason": "在掌握基础RAG后，通过剖析Dify的工业级管道，能深入理解生产环境中的文档解析、分段、嵌入和检索细节，并可直接部署为个人知识库服务。",
      "prerequisites": "Python、RAG基本原理、Docker 基础",
      "evidenceIds": [
        "E1"
      ],
      "score": 88,
      "references": [
        {
          "evidenceIds": [
            "E1"
          ],
          "url": "https://github.com/langgenius/dify",
          "name": "langgenius/dify"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    }
  ],
  "learning": {
    "steps": [
      "第一步：克隆Pathway LLM-App，配置OpenAI密钥，跑通“文件上传→提问→回答”的端到端流程，理解文档加载器与检索链。",
      "第二步：修改数据源为本地英文PDF，调整检索参数，实现中文提问英文文档的问答，并记录部署依赖。",
      "第三步：转向Dify，本地部署社区版，逐一拆解其RAG管道组件（文档解析、分段、向量化、检索），参照源码自定义一个文档处理步骤。"
    ],
    "timeline": "预计8周：前3周Pathway基础实践，中间3周扩展跨语言检索，后2周Dify深度解析与自定义。",
    "tips": "优先用AI助手解释Pathway和Dify的关键代码，遇到部署问题先查官方文档，中文提问时注意在Prompt中明确语言映射要求。"
  }
}
```

## rag_python / 第 1 轮 / B

耗时 96.42s；检索 1.12s；按标价估算 ¥0.1544。

### 输入

{"topics": ["RAG"], "purpose": "学习", "experience": "有基础", "languages": ["Python"], "timeCommitment": "两个月内", "customPurpose": "中文提问，检索英文技术文档，说明部署依赖", "useRag": true, "usePersona": false, "preferBeginnerProjects": true, "preferCareerProjects": false}

### 实际 Prompt 证据

[candidateId=E1;evidenceId=E1;repo=quivrhq/quivr;mode=RAW_FALLBACK] facts=基础 RAG 创建如上所示的基础 RAG 工作流很简单，步骤如下： 1. 将 API 密钥添加到环境变量中 python import os os.environ["OPENAI API KEY"] = "myopenai apikey" Quivr 支持 Anthropic、OpenAI 和 Mistral 的 API，也支持通过 Ollama 使用本地模型。 2. 创建 YAML 文件 basic rag workflow.yaml 并将以下内容复制进去 yaml workflow config: name: "standard RAG" nodes: nam…

### 最终内容

```json
{
  "analysis": {
    "trend": "RAG方向热度高、头部集中，拥挤度拥挤且难度标注为进阶；85%热门项目与AI辅助编程直接相关，适合从细分场景或垂直领域切入。",
    "prospect": "RAG是LLM应用落地的关键路径，多模型接入与本地部署需求增长；基础RAG工作流和LLM API集成经验对学习与求职都较实用。",
    "demand": "用户以Python为基础，两个月内学习RAG，希望中文提问、检索英文技术文档并说明部署依赖，需要可配置、易复现的入门项目。",
    "summary": "RAG热度高但拥挤，建议从基础RAG工作流与LLM API集成切入，结合Python和AI辅助开发学习。"
  },
  "recommendations": [
    {
      "candidateId": "E1",
      "name": "Quivr 基础 RAG 工作流",
      "description": "Quivr 支持通过 YAML 配置基础 RAG 工作流，可接入 Anthropic、OpenAI、Mistral API，或通过 Ollama 使用本地模型，适合快速搭建检索增强生成流程。",
      "difficulty": "中等",
      "difficultyReason": "需理解RAG流程、环境变量与YAML配置，但配置模板简单；有Python基础可较快复现，整体属于中等难度。",
      "vibeCodingReason": "工作流以YAML配置为主，节点清晰，适合AI辅助生成节点配置、排查API接入与本地模型部署问题。",
      "recommendReason": "该项目直接匹配RAG学习目标，帮助理解基础RAG工作流、多模型API接入和本地模型部署，适合用中文提问、检索英文文档并梳理部署依赖。",
      "prerequisites": "Python基础、环境变量配置、了解RAG基本概念与YAML格式。",
      "evidenceIds": [
        "E1"
      ],
      "score": 85,
      "references": [
        {
          "evidenceIds": [
            "E1"
          ],
          "url": "https://github.com/quivrhq/quivr",
          "name": "quivrhq/quivr"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    },
    {
      "candidateId": "B:26",
      "name": "LLM-Hub 统一LLM接口工具",
      "description": "通过调用不同 LLM 的接口构建统一工具，适合锻炼 TypeScript 开发与 API 集成能力，可集中管理与调用多个模型。",
      "difficulty": "入门",
      "difficultyReason": "用户有Python基础但缺少TypeScript经验，需先熟悉语法和npm使用；项目本身聚焦接口调用，入门压力较小。",
      "vibeCodingReason": "多个LLM接口调用模式相似，适合AI辅助生成各模型客户端、统一错误处理和请求配置。",
      "recommendReason": "虽不直接学习RAG，但通过统一LLM接口调用可积累模型API集成经验，为后续RAG检索链路中的模型调用打基础；同时TypeScript入门压力小。",
      "prerequisites": "TypeScript/JavaScript基础、Node.js、REST API概念。",
      "evidenceIds": [
        "B:26"
      ],
      "score": 91,
      "references": [
        {
          "evidenceIds": [
            "B:26"
          ],
          "url": "https://github.com/timmyy123/llm-hub",
          "name": "timmyy123/llm-hub"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    }
  ],
  "learning": {
    "steps": [
      "第一步：复现 Quivr 基础 RAG 工作流，配置 OpenAI 或 Ollama 本地模型，跑通 YAML 模板。",
      "第二步：阅读英文文档，梳理 Python 依赖、API 密钥和模型服务部署要求，记录踩坑。",
      "第三步：用 LLM-Hub 练习统一调用不同 LLM 接口，理解请求/响应结构。",
      "第四步：把基础 RAG 拆成检索与生成节点，尝试替换模型或调整提示，完成一个小项目。"
    ],
    "timeline": "预计周期：2个月（前3周基础RAG复现；第4-6周LLM API集成；最后2周综合小项目）。",
    "tips": "建议中文提问定位问题，用英文文档核验依赖版本；不要跳过最小流程直接做复杂架构；让AI辅助生成YAML和TypeScript代码并逐步理解。"
  }
}
```

## rag_python / 第 2 轮 / A

耗时 86.64s；检索 0.48s；按标价估算 ¥0.1473。

### 输入

{"topics": ["RAG"], "purpose": "学习", "experience": "有基础", "languages": ["Python"], "timeCommitment": "两个月内", "customPurpose": "中文提问，检索英文技术文档，说明部署依赖", "useRag": true, "usePersona": false, "preferBeginnerProjects": true, "preferCareerProjects": false}

### 实际 Prompt 证据

[candidateId=E1;evidenceId=E1;repo=langgenius/dify;mode=RAW_FALLBACK] facts=ures such as text-to-speech to a chat-based app. 4. RAG Pipeline : Extensive RAG capabilities that cover everything from document ingestion to retrieval, with out-of-box support for text extraction from PDFs, PPTs, and other common document formats.

[candidateId=E2;evidenceId=E2;repo=xinhuangcs/agentmaker;mode=RAW_FALLBACK] facts=and remember I'm vegetarian.").final output) Every argument past llm is optional, so you add capabilities one at a time, and the same pattern reaches the rest of the framework: RAG retrieval as another sources= entry, MCP servers and sub-agents ( AgentTool ) as tools= , a Smar…

[candidateId=E3;evidenceId=E3;repo=pathwaycom/llm-app] facts=证据:问答 RAG 应用 基础端到端 RAG 应用。; 一个问答流水线，使用所选的 GPT 模型，根据实时连接的数据源（文件、Google Drive、Sharepoint 等）中的文档（PDF、DOCX 等）为查询提供答案。

### 最终内容

```json
{
  "analysis": {
    "trend": "RAG方向热度高且拥挤，本周热门项目增长3%，85%项目与AI辅助编程相关，适合从垂直场景切入。",
    "prospect": "端到端RAG应用与生产级RAG平台需求明确，RAG与Agent、工具调用集成的框架化趋势值得关注。",
    "demand": "学习者需要先跑通基础问答流水线，再理解企业级文档摄入、解析与检索链路；部署依赖验证是常见门槛。",
    "summary": "RAG热度高但拥挤，建议先复现基础端到端RAG，再进阶生产级平台实践。"
  },
  "recommendations": [
    {
      "candidateId": "E3",
      "name": "Pathway LLM App 基础RAG问答应用",
      "description": "基础端到端RAG问答应用，可基于所选GPT模型对文件、Google Drive、SharePoint等实时数据源中的PDF、DOCX等文档进行问答，适合理解RAG入门流水线。",
      "difficulty": "入门",
      "difficultyReason": "项目定位为基础端到端RAG应用，链路直观，用户具备Python基础可较快跑通。",
      "vibeCodingReason": "RAG流水线步骤清晰，适合用AI辅助生成和调试Python问答代码，加速理解检索与生成各环节。",
      "recommendReason": "面向RAG入门学习，能快速建立从文档到答案的端到端认知，为后续进阶学习生产级RAG和求职项目打基础；可用中文提问定位问题，并用英文技术文档核对部署与依赖。",
      "prerequisites": "Python基础；了解命令行与API密钥配置。",
      "evidenceIds": [
        "E3"
      ],
      "score": 90,
      "references": [
        {
          "evidenceIds": [
            "E3"
          ],
          "url": "https://github.com/pathwaycom/llm-app",
          "name": "pathwaycom/llm-app"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    },
    {
      "candidateId": "E1",
      "name": "Dify 生产级RAG平台",
      "description": "提供从文档摄入到检索的完整RAG能力，开箱支持PDF、PPT等常见文档格式的文本提取，适合学习生产级RAG管道与工程化实践。",
      "difficulty": "进阶",
      "difficultyReason": "涵盖文档摄入到检索的完整RAG能力，工程化程度高，需投入更多时间理解与部署。",
      "vibeCodingReason": "项目结构复杂，适合用AI辅助梳理RAG管道、文档解析流程和部署配置，降低英文技术文档检索成本。",
      "recommendReason": "适合在掌握基础RAG后学习生产级平台的设计与部署，重点了解PDF/PPT等文档解析和完整检索链路，对求职中展示企业级RAG经验有帮助；建议结合英文技术文档验证部署依赖。",
      "prerequisites": "Python基础；建议先完成一个基础RAG示例；能阅读英文技术文档。",
      "evidenceIds": [
        "E1"
      ],
      "score": 84,
      "references": [
        {
          "evidenceIds": [
            "E1"
          ],
          "url": "https://github.com/langgenius/dify",
          "name": "langgenius/dify"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    }
  ],
  "learning": {
    "steps": [
      "第一步：基于E3复现基础RAG问答，跑通文档加载、检索到生成的完整流程。",
      "第二步：学习E1的生产级RAG能力，重点理解文档摄入、PDF/PPT解析与检索管道。",
      "第三步：结合英文文档验证部署依赖，完成一个垂直领域RAG Demo并记录关键配置。"
    ],
    "timeline": "两个月：前2周E3入门，第3-6周E1学习与部署，第7-8周整合Demo。",
    "tips": "中文提问定位问题，英文技术文档核对细节；不要跳过部署依赖验证；用AI辅助代码生成但需核对关键配置与项目事实。"
  }
}
```

## rag_python / 第 2 轮 / B

耗时 87.48s；检索 1.39s；按标价估算 ¥0.1377。

### 输入

{"topics": ["RAG"], "purpose": "学习", "experience": "有基础", "languages": ["Python"], "timeCommitment": "两个月内", "customPurpose": "中文提问，检索英文技术文档，说明部署依赖", "useRag": true, "usePersona": false, "preferBeginnerProjects": true, "preferCareerProjects": false}

### 实际 Prompt 证据

[candidateId=E1;evidenceId=E1;repo=quivrhq/quivr;mode=RAW_FALLBACK] facts=基础 RAG 创建如上所示的基础 RAG 工作流很简单，步骤如下： 1. 将 API 密钥添加到环境变量中 python import os os.environ["OPENAI API KEY"] = "myopenai apikey" Quivr 支持 Anthropic、OpenAI 和 Mistral 的 API，也支持通过 Ollama 使用本地模型。 2. 创建 YAML 文件 basic rag workflow.yaml 并将以下内容复制进去 yaml workflow config: name: "standard RAG" nodes: nam…

### 最终内容

```json
{
  "analysis": {
    "trend": "RAG方向热度高且拥挤，85%热门仓库与AI辅助编程相关，头部项目集中，适合从基础工作流和细分场景切入。",
    "prospect": "RAG仍是学习热点，多模型API接入与本地部署能力可形成差异，实践需求稳定。",
    "demand": "用户有Python基础、两个月时间，需要以基础RAG工作流上手，并结合中文提问检索英文文档和梳理部署依赖。",
    "summary": "RAG热但拥挤，宜以Quivr基础工作流上手，辅以LLM接口练习，两个月完成部署与验证。"
  },
  "recommendations": [
    {
      "candidateId": "E1",
      "name": "Quivr 基础 RAG 工作流",
      "description": "提供基础 RAG 工作流配置与运行方式，支持用 Python 配置 API 密钥、用 YAML 定义流程，可接入 OpenAI、Anthropic、Mistral 及 Ollama 本地模型。",
      "difficulty": "中等",
      "difficultyReason": "用户有Python基础，能处理环境变量和YAML配置；多模型API接入与Ollama本地部署需要一定调试，难度中等。",
      "vibeCodingReason": "可用自然语言描述工作流，让AI辅助生成或修改YAML节点、环境变量与部署命令，并辅助检索英文技术文档。",
      "recommendReason": "项目与RAG学习目标高度匹配：可先跑通基础RAG流程，再用中文提问检索英文技术文档，同时记录API与Ollama部署依赖，符合两个月学习周期；多模型支持也为后续扩展留出空间。",
      "prerequisites": "Python基础、环境变量与YAML配置、基础LLM API概念",
      "evidenceIds": [
        "E1"
      ],
      "score": 90,
      "references": [
        {
          "evidenceIds": [
            "E1"
          ],
          "url": "https://github.com/quivrhq/quivr",
          "name": "quivrhq/quivr"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    },
    {
      "candidateId": "B:26",
      "name": "LLM-Hub 统一大模型接口工具",
      "description": "通过调用不同大模型接口构建统一工具，集中处理请求与响应，适合学习 API 集成与多模型调用。",
      "difficulty": "入门",
      "difficultyReason": "候选标记为入门，但以TypeScript为主；用户当前只掌握Python，需先补齐TypeScript基础，总体前期成本略高。",
      "vibeCodingReason": "适合让AI辅助生成统一接口层、请求响应处理与错误处理代码，按API文档逐步形成多模型调用模板。",
      "recommendReason": "作为入门辅助项目，可帮助理解不同LLM接口差异，为RAG流程中的模型调用打基础；但因使用TypeScript，与当前Python背景不完全一致，建议学有余力时使用。",
      "prerequisites": "TypeScript/JavaScript基础、Node.js环境、REST API概念",
      "evidenceIds": [
        "B:26"
      ],
      "score": 72,
      "references": [
        {
          "evidenceIds": [
            "B:26"
          ],
          "url": "https://github.com/timmyy123/llm-hub",
          "name": "timmyy123/llm-hub"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    }
  ],
  "learning": {
    "steps": [
      "用 Quivr 配置 API 密钥与基础 YAML 工作流，跑通英文文档问答。",
      "改用 Ollama 本地模型，记录部署依赖与中英文检索差异。",
      "用 LLM-Hub（可选）练习多模型 API 接入，对比不同模型响应。"
    ],
    "timeline": "4-6周完成Quivr基础与本地模型部署，剩余时间用于多模型接口练习与总结。",
    "tips": "中文提问时把英文文档片段一并输入，让AI辅助总结依赖与报错；优先使用Ollama降低API成本。"
  }
}
```

## spring_beginner / 第 1 轮 / A

耗时 36.63s；检索 1.13s；按标价估算 ¥0.0606。

### 输入

{"topics": ["Spring Boot"], "purpose": "学习", "experience": "刚学", "languages": ["Java"], "timeCommitment": "60天内", "customPurpose": "只推荐 Java 项目，从能运行的小功能开始", "useRag": true, "usePersona": false, "preferBeginnerProjects": true, "preferCareerProjects": false}

### 实际 Prompt 证据

[candidateId=E1;evidenceId=E1;repo=mshumer/claude-of-duty;mode=RAW_FALLBACK] facts=D, PBD ragdolls, multi-layer bullet penetration player Movement state machine, slide/mantle/lean, camera feel weapons Procedural weapon geometry, viewmodel rig, ADS, spring recoil, procedural reloads, ballistics with travel time and drop fx GPU particles, decals, tracers, muz…

[candidateId=E2;evidenceId=E2;repo=alibaba/nacos;mode=RAW_FALLBACK] facts=or Nacos, such as SDK, synchronization tool, etc. spring-cloud-alibaba provides the one-stop solution for application development over Alibaba middleware which includes Nacos. Contact

[candidateId=E3;evidenceId=E3;repo=embabel/embabel-agent-examples;mode=RAW_FALLBACK] facts=elf-critique capabilities. What It Teaches: 🧠 Multi-model consensus (GPT-4 + Claude working together) 🔍 Self-improvement loops with critique and retry ⚙️ Configuration-driven behavior with Spring Boot properties 🌊 Parallel processing of research tasks 📝 Quality co…

### 最终内容

```json
{
  "analysis": {
    "trend": "Spring Boot仍是Java企业级开发主流，配置驱动与AI代理示例正在增多，微服务配置中心需求稳定。",
    "prospect": "Java后端与Spring Cloud Alibaba生态在云原生场景中前景稳健，适合作为长期学习方向。",
    "demand": "企业招聘中Spring Boot基础能力为常见要求，Nacos配置中心在微服务项目中也有持续需求。",
    "summary": "优先用E3上手Spring Boot小功能，再以E2了解微服务配置；两者均基于Java生态，匹配60天学习。"
  },
  "recommendations": [
    {
      "candidateId": "E3",
      "name": "Embabel Agent 示例",
      "description": "Spring Boot驱动的多模型AI代理示例，展示多模型共识、自我批判与重试、并行研究与配置化行为。",
      "difficulty": "中等",
      "difficultyReason": "项目用Spring Boot properties配置行为，对刚学Java可先跑通基础配置，但多模型与并行处理需额外理解。",
      "vibeCodingReason": "配置驱动且流程清晰，适合让AI生成示例配置、解释多模型协作流程并辅助调试。",
      "recommendReason": "该示例把Spring Boot配置驱动、并行任务和外部AI模型结合起来，能帮你在60天内从可运行的小示例入手，逐步理解Bean、配置属性与异步处理。",
      "prerequisites": "Java基础、Spring Boot入门、了解properties配置。",
      "evidenceIds": [
        "E3"
      ],
      "score": 85,
      "references": [
        {
          "evidenceIds": [
            "E3"
          ],
          "url": "https://github.com/embabel/embabel-agent-examples",
          "name": "embabel/embabel-agent-examples"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    },
    {
      "candidateId": "E2",
      "name": "Nacos / Spring Cloud Alibaba",
      "description": "围绕Nacos与Spring Cloud Alibaba的分布式配置与服务方案，提供SDK、同步工具等集成能力。",
      "difficulty": "进阶",
      "difficultyReason": "涉及分布式配置中心与服务发现，对刚学Java和Spring Boot的用户偏难，但可作为后续学习方向。",
      "vibeCodingReason": "可借助AI梳理Nacos与Spring Cloud Alibaba的集成配置、启动步骤和排错。",
      "recommendReason": "如果希望在掌握基础Spring Boot后了解微服务配置中心，该项目是较真实的企业级方向；建议先跑通官方示例，再让AI解释Nacos集成依赖与配置项。",
      "prerequisites": "Spring Boot基础、Maven、基本微服务概念。",
      "evidenceIds": [
        "E2"
      ],
      "score": 78,
      "references": [
        {
          "evidenceIds": [
            "E2"
          ],
          "url": "https://github.com/alibaba/nacos",
          "name": "alibaba/nacos"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    }
  ],
  "learning": {
    "steps": [
      "第一步：搭建Java与Maven环境，运行一个最小Spring Boot应用。",
      "第二步：用E3的配置驱动示例练习properties、Bean和异步任务。",
      "第三步：尝试E2的Nacos集成，理解配置中心与服务注册。"
    ],
    "timeline": "预计60天：前20天基础，中间25天E3，后15天E2。",
    "tips": "建议先跑通再修改；使用AI解释报错；不追求一次理解微服务全部概念。"
  }
}
```

## spring_beginner / 第 1 轮 / B

耗时 69.74s；检索 2.82s；按标价估算 ¥0.1078。

### 输入

{"topics": ["Spring Boot"], "purpose": "学习", "experience": "刚学", "languages": ["Java"], "timeCommitment": "60天内", "customPurpose": "只推荐 Java 项目，从能运行的小功能开始", "useRag": true, "usePersona": false, "preferBeginnerProjects": true, "preferCareerProjects": false}

### 实际 Prompt 证据

[candidateId=E1;evidenceId=E1;repo=jargin-j/earthquake-warning] facts=证据:Java 17 运行时环境; 5 应用框架; Java-WebSocket 1.

[candidateId=E2;evidenceId=E2;repo=practical-tutorials/project-based-learning;mode=RAW_FALLBACK] facts=Java: Build an Interpreter (Chapter 4-13 is written in Java) Build a Simple HTTP Server with Java Build an Android Flashlight App (video) Build a Spring Boot App with User Authentication

[candidateId=E3;evidenceId=E3;repo=embabel/embabel-agent-examples;mode=RAW_FALLBACK] facts=🆕 Spring Boot Integration Architecture

### 最终内容

```json
{
  "analysis": {
    "trend": "Java 与 Spring Boot 在后端开发中保持主流，项目制学习和 AI 辅助编码降低了入门门槛。",
    "prospect": "Spring Boot 生态成熟，适合作为 Java 后端入门与求职基础，掌握认证等核心模块更具竞争力。",
    "demand": "企业招聘中 Java/Spring Boot 初级岗位数量稳定，能完成用户认证、接口开发等模块是常见加分项。",
    "summary": "推荐以 Spring Boot 用户认证为主线，从能运行的简单 HTTP 服务开始，逐步了解集成架构，匹配 60 天 Java 学习路径。"
  },
  "recommendations": [
    {
      "candidateId": "E2",
      "name": "项目制 Java 学习路径（含 Spring Boot 用户认证）",
      "description": "项目制学习仓库，提供多个 Java 练习，包含构建简单 HTTP 服务器与 Spring Boot 用户认证应用。适合从能运行的小功能开始，逐步完成登录、注册等认证流程，积累 Spring Boot 项目经验。",
      "difficulty": "入门",
      "difficultyReason": "用户刚学 Java，该路径从简单 HTTP 服务开始，逐步过渡到 Spring Boot 认证，适合 60 天内入门。",
      "vibeCodingReason": "仓库提供分步项目目标，适合让 AI 生成可运行骨架并逐步解释代码，便于边跑边学。",
      "recommendReason": "该候选包含 Spring Boot 用户认证应用，贴近你的 Spring Boot 兴趣和 Java 学习目标；从简单 HTTP 服务开始能快速看到运行结果，避免刚学就陷入复杂配置。完成认证模块可为后续求职积累后端项目经验。",
      "prerequisites": "Java 基础语法、HTTP 基本概念、IDE 使用",
      "evidenceIds": [
        "E2"
      ],
      "score": 85,
      "references": [
        {
          "evidenceIds": [
            "E2"
          ],
          "url": "https://github.com/practical-tutorials/project-based-learning",
          "name": "practical-tutorials/project-based-learning"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    },
    {
      "candidateId": "E3",
      "name": "Spring Boot 集成架构示例",
      "description": "该示例展示 Spring Boot 集成架构，可了解组件组织、模块协作和配置方式。适合在完成基础功能后，作为架构参考和扩展练习，体会真实项目的分层与集成思路。",
      "difficulty": "进阶",
      "difficultyReason": "用户刚学 Java，直接阅读集成架构可能信息量大；建议在跑通基础功能后再接触，避免初期负担过重。",
      "vibeCodingReason": "架构示例可与 AI 辅助阅读，让 AI 解释组件关系与配置，快速理解集成方式，而不需要手动逐行排查。",
      "recommendReason": "作为扩展学习，帮助你了解 Spring Boot 项目如何组织与集成，形成从功能实现到架构认知的闭环；但需在完成基础项目后使用，避免挫败感，符合 60 天后期目标。",
      "prerequisites": "已完成 Spring Boot 基础功能、了解 Maven/Gradle 构建、基本 Bean/依赖注入概念",
      "evidenceIds": [
        "E3"
      ],
      "score": 70,
      "references": [
        {
          "evidenceIds": [
            "E3"
          ],
          "url": "https://github.com/embabel/embabel-agent-examples",
          "name": "embabel/embabel-agent-examples"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    }
  ],
  "learning": {
    "steps": [
      "花 1-2 周巩固 Java 基础语法并了解 HTTP 请求/响应与 JSON。",
      "用 E2 从简单 HTTP 服务器开始跑通可运行程序，再完成 Spring Boot 用户认证应用。",
      "用 E3 阅读 Spring Boot 集成架构，理解组件组织，尝试修改或扩展小模块。"
    ],
    "timeline": "预计 60 天：前 2 周基础，第 3-6 周完成 Spring Boot 认证，最后 2 周学习集成架构。",
    "tips": "每天保持可运行状态；让 AI 解释不熟的注解和配置；遇到问题时逐步缩小范围，不要一次性堆叠功能。"
  }
}
```

## spring_beginner / 第 2 轮 / A

耗时 30.96s；检索 0.55s；按标价估算 ¥0.0432。

### 输入

{"topics": ["Spring Boot"], "purpose": "学习", "experience": "刚学", "languages": ["Java"], "timeCommitment": "60天内", "customPurpose": "只推荐 Java 项目，从能运行的小功能开始", "useRag": true, "usePersona": false, "preferBeginnerProjects": true, "preferCareerProjects": false}

### 实际 Prompt 证据

[candidateId=E1;evidenceId=E1;repo=mshumer/claude-of-duty;mode=RAW_FALLBACK] facts=D, PBD ragdolls, multi-layer bullet penetration player Movement state machine, slide/mantle/lean, camera feel weapons Procedural weapon geometry, viewmodel rig, ADS, spring recoil, procedural reloads, ballistics with travel time and drop fx GPU particles, decals, tracers, muz…

[candidateId=E2;evidenceId=E2;repo=alibaba/nacos;mode=RAW_FALLBACK] facts=or Nacos, such as SDK, synchronization tool, etc. spring-cloud-alibaba provides the one-stop solution for application development over Alibaba middleware which includes Nacos. Contact

[candidateId=E3;evidenceId=E3;repo=embabel/embabel-agent-examples;mode=RAW_FALLBACK] facts=elf-critique capabilities. What It Teaches: 🧠 Multi-model consensus (GPT-4 + Claude working together) 🔍 Self-improvement loops with critique and retry ⚙️ Configuration-driven behavior with Spring Boot properties 🌊 Parallel processing of research tasks 📝 Quality co…

### 最终内容

```json
{
  "analysis": {
    "trend": "Spring Boot 持续主导 Java 后端开发，并在微服务、AI 集成中广泛应用",
    "prospect": "掌握 Spring Boot 基础后可快速进入企业级后端、微服务或 AI 应用开发",
    "demand": "初学者需要简单可运行的项目，从配置驱动和核心概念入手",
    "summary": "推荐 Nacos 服务发现与 AI 智能体示例，帮助初学者从配置和集成开始学习 Spring Boot"
  },
  "recommendations": [
    {
      "candidateId": "E2",
      "name": "Nacos 服务发现与配置管理入门",
      "description": "基于 Spring Boot 集成 Nacos，实现服务注册、发现和动态配置管理，理解微服务基础设施的核心概念。",
      "difficulty": "中等",
      "difficultyReason": "涉及服务端启动、配置和网络概念，但可简化为单机运行，适合刚学用户逐步理解",
      "vibeCodingReason": "AI 可快速生成 Spring Boot 启动类和 Nacos 配置代码，减少手动编写样板代码",
      "recommendReason": "项目帮助学习 Spring Boot 如何与微服务中间件集成，为将来开发分布式系统打下基础，需求明确且社区资料丰富。",
      "prerequisites": "Java 基础、Spring Boot 入门、YAML 配置、HTTP 基本概念",
      "evidenceIds": [
        "E2"
      ],
      "score": 85,
      "references": [
        {
          "evidenceIds": [
            "E2"
          ],
          "url": "https://github.com/alibaba/nacos",
          "name": "alibaba/nacos"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    },
    {
      "candidateId": "E3",
      "name": "AI 智能体示例：多模型协作与配置驱动",
      "description": "通过 Spring Boot 属性驱动的 AI 智能体示例，学习如何用配置控制多模型共识、自我改进和并行处理流程。",
      "difficulty": "中等",
      "difficultyReason": "涉及 AI 模型调用和并行任务，但示例已提供可运行的配置，可专注 Spring Boot 配置部分",
      "vibeCodingReason": "AI 可以帮助解释配置项并生成适配的 Spring Boot 属性，快速试验不同行为",
      "recommendReason": "将 Spring Boot 与前沿 AI 应用结合，既能巩固配置驱动设计，又能体验 AI 工程化，提升综合能力。",
      "prerequisites": "Spring Boot 配置、了解 AI 模型调用概念（如 REST API）",
      "evidenceIds": [
        "E3"
      ],
      "score": 80,
      "references": [
        {
          "evidenceIds": [
            "E3"
          ],
          "url": "https://github.com/embabel/embabel-agent-examples",
          "name": "embabel/embabel-agent-examples"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    }
  ],
  "learning": {
    "steps": [
      "第一步：搭建 Spring Boot 基础项目，引入相关依赖（spring-cloud-starter-alibaba-nacos-discovery/config 或 AI 示例依赖）",
      "第二步：运行项目提供的示例入口，观察日志和行为",
      "第三步：逐步修改配置和核心类，扩展小功能（如添加一个简单的 Controller）"
    ],
    "timeline": "60天内，前两周搭建并运行，之后每周扩展一个功能点",
    "tips": "遇到错误先看控制台输出，结合官方文档和 AI 给出的解释，多动手改配置来理解效果"
  }
}
```

## spring_beginner / 第 2 轮 / B

耗时 30.10s；检索 1.13s；按标价估算 ¥0.0384。

### 输入

{"topics": ["Spring Boot"], "purpose": "学习", "experience": "刚学", "languages": ["Java"], "timeCommitment": "60天内", "customPurpose": "只推荐 Java 项目，从能运行的小功能开始", "useRag": true, "usePersona": false, "preferBeginnerProjects": true, "preferCareerProjects": false}

### 实际 Prompt 证据

[candidateId=E1;evidenceId=E1;repo=jargin-j/earthquake-warning] facts=证据:Java 17 运行时环境; 5 应用框架; Java-WebSocket 1.

[candidateId=E2;evidenceId=E2;repo=practical-tutorials/project-based-learning;mode=RAW_FALLBACK] facts=Java: Build an Interpreter (Chapter 4-13 is written in Java) Build a Simple HTTP Server with Java Build an Android Flashlight App (video) Build a Spring Boot App with User Authentication

[candidateId=E3;evidenceId=E3;repo=embabel/embabel-agent-examples;mode=RAW_FALLBACK] facts=🆕 Spring Boot Integration Architecture

### 最终内容

```json
{
  "analysis": {
    "trend": "Spring Boot 依然是 Java 后端学习的主流框架，生态成熟，适合新手快速构建可运行的应用。",
    "prospect": "掌握 Spring Boot 可为后续学习微服务、云原生开发打下坚实基础，就业市场需求持续旺盛。",
    "demand": "初学者需要从能跑通的最小功能开始，逐步理解控制反转、依赖注入和自动配置，避免一开始就陷入复杂架构。",
    "summary": "聚焦 Spring Boot 入门项目，通过用户认证和集成示例快速建立全栈开发信心。"
  },
  "recommendations": [
    {
      "candidateId": "E2",
      "name": "Spring Boot 用户认证应用",
      "description": "基于 Spring Boot 构建带用户登录、注册功能的 Web 应用，涵盖表单验证、会话管理和基础安全配置，适合作为第一个全栈项目。",
      "difficulty": "入门",
      "difficultyReason": "功能路径清晰，仅涉及 Controller、Service、Repository 基础分层，配合模板引擎即可跑通，无需额外中间件。",
      "vibeCodingReason": "Spring Boot 的自动配置与起步依赖让环境搭建几乎无代码，AI 可直接生成控制器、实体和认证逻辑骨架，开发者专注业务理解。",
      "recommendReason": "该项目来自实践教程集合，完全基于 Spring Boot 实现用户认证，能让您在 60 天内动手领悟 IoC、依赖注入和 HTTP 请求处理，为日后学习 RESTful API 和 Spring Security 打下扎实基础。",
      "prerequisites": "Java 基础语法、Maven 或 Gradle 的基本使用、HTML 表单概念",
      "evidenceIds": [
        "E2"
      ],
      "score": 90,
      "references": [
        {
          "evidenceIds": [
            "E2"
          ],
          "url": "https://github.com/practical-tutorials/project-based-learning",
          "name": "practical-tutorials/project-based-learning"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    },
    {
      "candidateId": "E3",
      "name": "Spring Boot 集成架构示例",
      "description": "展示 Spring Boot 如何与外部代理服务集成，实现消息通信或任务调度，初步了解微服务架构中组件的协作方式。",
      "difficulty": "中等",
      "difficultyReason": "需要理解跨服务通信和异步处理，涉及配置外部代理，对刚接触 Spring Boot 的学习者有一定挑战。",
      "vibeCodingReason": "AI 可快速生成集成适配器和配置类，隐藏底层连接细节，让您专注于集成逻辑和业务场景的设计。",
      "recommendReason": "在完成基础应用后，此示例能引导您探索 Spring Boot 在生产环境中的集成模式，理解如何与消息队列、调度器等协作，为进阶学习 Spring Cloud 和分布式系统提供思路。",
      "prerequisites": "已掌握 Spring Boot 基本应用搭建、了解 REST 通信或消息模型的基本概念",
      "evidenceIds": [
        "E3"
      ],
      "score": 80,
      "references": [
        {
          "evidenceIds": [
            "E3"
          ],
          "url": "https://github.com/embabel/embabel-agent-examples",
          "name": "embabel/embabel-agent-examples"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    }
  ],
  "learning": {
    "steps": [
      "配置 Java 17+ 和 Maven/Gradle，创建 Spring Boot 工程，熟悉 @SpringBootApplication 和 application.properties。",
      "跟着推荐项目 E2 实现 User 实体、Repository、Service 和 Controller，结合 Thymeleaf 模板完成注册登录页面。",
      "理解 Bean 生命周期和依赖注入后，尝试调整项目 E3 的集成配置，观察不同组件如何通过 Spring 启动并交互。"
    ],
    "timeline": "前 30 天完成 E2 的搭建与调试，后 30 天研究 E3 的集成模式并尝试组合自己的小功能。",
    "tips": "不要跳过手写配置的过程，每完成一个功能就用 Postman 或浏览器验证一次；遇到困惑时可让 AI 解释自动配置原理，而不是直接照搬代码。"
  }
}
```

## unavailable / 第 1 轮 / A

耗时 7.34s；检索 0.59s；按标价估算 ¥0.0106。

### 输入

{"topics": ["量子纠错芯片流片"], "purpose": "探索技术兴趣", "experience": "刚学", "languages": ["Java"], "timeCommitment": "两天内", "customPurpose": "只能推荐能核实符合条件的现有仓库，找不到请说明，不编造", "useRag": true, "usePersona": false, "preferBeginnerProjects": false, "preferCareerProjects": false}

### 实际 Prompt 证据



### 最终内容

```json
{
  "analysis": {
    "trend": "量子纠错与芯片流片属于前沿交叉领域，硬件相关性强，初学者难以直接介入。",
    "prospect": "量子计算硬件门槛极高，需物理、电子工程背景，短期内不适合纯软件入门。",
    "demand": "流片依赖代工厂与专业EDA工具，开源项目极少，难以找到适合新手的仓库。",
    "summary": "量子纠错芯片流片无现有新手可参与项目，推荐不足。"
  },
  "recommendations": [],
  "learning": {
    "steps": [
      "先巩固Java基础，理解数据结构和算法",
      "学习量子计算基础概念（如量子比特、纠错码）",
      "尝试模拟量子纠错的经典仿真项目（如Qiskit）"
    ],
    "timeline": "无法在两天内完成量子纠错芯片流片相关项目，建议先转向量子仿真学习。",
    "tips": "量子芯片流片属于硬件与理论物理方向，新手可先通过软件模拟入门。"
  }
}
```

## unavailable / 第 1 轮 / B

耗时 14.02s；检索 1.30s；按标价估算 ¥0.0195。

### 输入

{"topics": ["量子纠错芯片流片"], "purpose": "探索技术兴趣", "experience": "刚学", "languages": ["Java"], "timeCommitment": "两天内", "customPurpose": "只能推荐能核实符合条件的现有仓库，找不到请说明，不编造", "useRag": true, "usePersona": false, "preferBeginnerProjects": false, "preferCareerProjects": false}

### 实际 Prompt 证据



### 最终内容

```json
{
  "analysis": {
    "trend": "量子纠错芯片流片属于量子计算硬件前沿方向，研究热度持续上升，但工程实现门槛高。",
    "prospect": "长期前景较好，主要面向科研机构、量子计算企业与先进半导体团队，商业化周期较长。",
    "demand": "当前需求集中在学术研究、量子硬件研发和底层纠错算法验证，对初学者入门岗位较少。",
    "summary": "暂无符合条件且可核实的开源候选项目，建议先补齐量子计算与芯片基础知识。"
  },
  "recommendations": [],
  "learning": {
    "steps": [
      "学习量子比特、量子门与量子纠错基本概念",
      "了解芯片设计与流片流程，熟悉EDA工具基础",
      "用开源量子计算框架进行小规模纠错电路模拟"
    ],
    "timeline": "两天内可完成概念入门与小规模模拟练习",
    "tips": "先聚焦量子纠错原理和现有模拟工具，不急于进入真实流片环节"
  }
}
```

## unavailable / 第 2 轮 / A

耗时 15.05s；检索 0.67s；按标价估算 ¥0.0240。

### 输入

{"topics": ["量子纠错芯片流片"], "purpose": "探索技术兴趣", "experience": "刚学", "languages": ["Java"], "timeCommitment": "两天内", "customPurpose": "只能推荐能核实符合条件的现有仓库，找不到请说明，不编造", "useRag": true, "usePersona": false, "preferBeginnerProjects": false, "preferCareerProjects": false}

### 实际 Prompt 证据



### 最终内容

```json
{
  "analysis": {
    "trend": "量子纠错芯片流片属于前沿交叉方向，开源社区中直接可核实的完整流片项目极少。",
    "prospect": "该方向长期具有科研与产业价值，但门槛高，需量子物理、芯片设计与流片经验。",
    "demand": "初学者需求集中在量子计算基础、芯片设计工具链与仿真实践，而非直接流片。",
    "summary": "未发现可核实的候选项目，无法推荐现有仓库。"
  },
  "recommendations": [],
  "learning": {
    "steps": [
      "先学习量子计算与量子纠错基础概念",
      "学习芯片设计基础与开源EDA工具使用",
      "寻找量子芯片仿真或相关开源项目进行实践"
    ],
    "timeline": "建议从数周基础学习开始，两天内仅能完成科普与工具环境搭建",
    "tips": "不要急于流片，先补量子信息与数字电路基础；Java经验可迁移到部分工具脚本开发。"
  }
}
```

## unavailable / 第 2 轮 / B

耗时 22.26s；检索 1.21s；按标价估算 ¥0.0318。

### 输入

{"topics": ["量子纠错芯片流片"], "purpose": "探索技术兴趣", "experience": "刚学", "languages": ["Java"], "timeCommitment": "两天内", "customPurpose": "只能推荐能核实符合条件的现有仓库，找不到请说明，不编造", "useRag": true, "usePersona": false, "preferBeginnerProjects": false, "preferCareerProjects": false}

### 实际 Prompt 证据



### 最终内容

```json
{
  "analysis": {
    "trend": "本次未提供带candidateId的候选或事实卡片，无法评估量子纠错芯片流片相关仓库趋势。",
    "prospect": "缺少可核实的候选仓库信息，无法给出具体前景判断。",
    "demand": "用户希望探索量子纠错芯片流片方向，但当前消息中未提供可推荐的真实候选。",
    "summary": "未发现可核实的候选项目，故不推荐任何仓库。"
  },
  "recommendations": [],
  "learning": {
    "steps": [
      "先了解量子比特、退相干与噪声基础概念",
      "学习量子纠错入门：位翻转、相位翻转、表面码",
      "了解芯片流片基本流程与硬件描述语言基础"
    ],
    "timeline": "预计2天可完成概念入门，不包含具体项目实践",
    "tips": "当前仅提供通用概念学习路线，不指向特定仓库；后续可提供带candidateId的候选再筛选。"
  }
}
```

## vue_learning / 第 1 轮 / A

耗时 42.00s；检索 0.50s；按标价估算 ¥0.0594。

### 输入

{"topics": ["Vue"], "purpose": "学习", "experience": "刚学", "languages": ["JavaScript"], "timeCommitment": "一个月内", "customPurpose": "做一个能演示的前端项目，优先容易运行的例子", "useRag": true, "usePersona": false, "preferBeginnerProjects": true, "preferCareerProjects": false}

### 实际 Prompt 证据

[candidateId=E1;evidenceId=E1;repo=davidhdev/canvas-ui] facts=技术:TypeScript,React,Next.js;证据:部分组件依赖实验性API（HTML-in-canvas），某些浏览器可能降级；视觉效果可能影响性能。; TypeScript,React,Next.; ["animation", "canvas", "component-library", "comp…

[candidateId=E2;evidenceId=E2;repo=justarchinet/asf-webconfiggenerator] facts=摘要:一款基于Vue.js的ArchiSteamFarm配置文件在线生成器。;技术:JavaScript,Vue;证据:以表单驱动的配置生成工具，可练习Vue组件开发和数据配置逻辑，门槛较低。; JavaScript,Vue; ["archisteamfarm", "css", "…

[candidateId=E3;evidenceId=E3;repo=pocket-stack/pocketjs] facts=摘要:浏览器外的高性能 JSX UI 框架，支持硬件渲染、Vue Vapor 和 Solid 标准、Tailwind 样式引擎，在 8 MB 内存下实现 60 FPS 动画。;技术:TypeScript,Vue;证据:新手友好项目; 浏览器外的高性能 JSX UI 框架，支持硬件渲染、Vue Vapor 和…

### 最终内容

```json
{
  "analysis": {
    "trend": "Vue.js 在国内前端岗位需求持续旺盛，是新手入门前端的优选框架之一。",
    "prospect": "Vue 3 生态日趋完善，搭配 TypeScript 将成为未来项目标配，具备良好的长期发展前景。",
    "demand": "企业普遍要求前端开发者拥有可演示的 Vue 项目经验，尤其看重组件开发与状态管理能力。",
    "summary": "推荐从入门级 Vue 表单项目入手，逐步进阶至 TypeScript 项目，以演示作品提升求职竞争力。"
  },
  "recommendations": [
    {
      "candidateId": "B:36",
      "name": "ASF 配置生成器",
      "description": "一款基于 Vue.js 的 ArchiSteamFarm 配置文件在线生成工具，通过表单驱动配置生成，聚焦 Vue 组件开发与数据绑定逻辑，门槛低、易上手。",
      "difficulty": "入门",
      "difficultyReason": "项目纯 JavaScript + Vue，不涉及复杂状态管理或构建配置，匹配用户的 JavaScript 基础与一个月学习周期。",
      "vibeCodingReason": "表单组件结构固定、逻辑清晰，AI 可快速生成模板、校验规则与事件处理代码，降低手写成本。",
      "recommendReason": "项目能快速产出可演示的配置工具，帮助初学者理解 Vue 的核心概念（组件、指令、双向绑定），为后续学习 TypeScript 和复杂应用打下基础，适合作为第一个个人作品。",
      "prerequisites": "JavaScript 基础，Vue 基本语法（模板、data、methods）",
      "evidenceIds": [
        "B:36"
      ],
      "score": 91,
      "references": [
        {
          "evidenceIds": [
            "B:36"
          ],
          "url": "https://github.com/justarchinet/asf-webconfiggenerator",
          "name": "justarchinet/asf-webconfiggenerator"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    },
    {
      "candidateId": "B:46",
      "name": "协议启动器",
      "description": "基于 Vue + TypeScript 的协议处理工具，实现自定义 URL 协议注册与跨应用通信，强化类型安全与前端高级交互能力。",
      "difficulty": "中等",
      "difficultyReason": "项目使用 TypeScript 严格类型检查，涉及系统级协议交互，对用户的抽象思维与调试能力有更高要求，适合入门后的进阶学习。",
      "vibeCodingReason": "类型定义、协议解析逻辑重复性高，AI 可辅助生成完整的类型声明、接口封装与错误处理模板，降低 TS 编写门槛。",
      "recommendReason": "在掌握基础 Vue 后，此项目能帮助用户深入理解 TypeScript 集成与浏览器 API 调用，产出具备技术深度的作品，显著提升求职简历的竞争力。",
      "prerequisites": "JavaScript、Vue 组件开发基础，TypeScript 基本类型与接口",
      "evidenceIds": [
        "B:46"
      ],
      "score": 95,
      "references": [
        {
          "evidenceIds": [
            "B:46"
          ],
          "url": "https://github.com/zhensherlock/protocol-launcher",
          "name": "zhensherlock/protocol-launcher"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    }
  ],
  "learning": {
    "steps": [
      "搭建 Vue 3 + Vite 开发环境，快速学习组件、模板语法与数据绑定。",
      "完成 ASF 配置生成器项目，通过表单交互掌握事件处理与状态管理基础。",
      "学习 TypeScript 基础（类型注解、接口、泛型），并重构或启动协议启动器项目，实践类型安全编程。"
    ],
    "timeline": "前 2 周完成基础学习与表单项目，后 2 周攻克 TypeScript 与协议启动器项目，预留 3-5 天整体调试与优化。",
    "tips": "善用 AI 助手生成重复性的组件代码与类型定义，重点理解逻辑而非死记语法；定期提交 Git 记录学习路径。"
  }
}
```

## vue_learning / 第 1 轮 / B

耗时 48.88s；检索 1.16s；按标价估算 ¥0.0652。

### 输入

{"topics": ["Vue"], "purpose": "学习", "experience": "刚学", "languages": ["JavaScript"], "timeCommitment": "一个月内", "customPurpose": "做一个能演示的前端项目，优先容易运行的例子", "useRag": true, "usePersona": false, "preferBeginnerProjects": true, "preferCareerProjects": false}

### 实际 Prompt 证据

[candidateId=E1;evidenceId=E1;repo=zhensherlock/protocol-launcher;mode=RAW_FALLBACK] facts=技术栈 TypeScript,Vue 适合新手的原因 聚焦于类型安全和协议处理，前端使用 Vue，适合学习 TypeScript 高级特性和跨应用通信。

[candidateId=E2;evidenceId=E2;repo=justarchinet/asf-webconfiggenerator] facts=技术:JavaScript,Vue;证据:JavaScript,Vue; 以表单驱动的配置生成工具，可练习Vue组件开发和数据配置逻辑，门槛较低。

[candidateId=E3;evidenceId=E3;repo=pocket-stack/pocketjs;mode=RAW_FALLBACK] facts=技术栈 TypeScript,Vue

### 最终内容

```json
{
  "analysis": {
    "trend": "Vue 生态组件化开发需求旺盛，配置驱动型工具与跨应用交互场景增多。",
    "prospect": "表单配置与协议处理类项目能快速积累组件设计经验，适合作为前端演示作品。",
    "demand": "新手偏好低门槛、可快速运行的前端项目，以巩固 Vue 核心概念并产出可展示成果。",
    "summary": "侧重 Vue 组件开发与数据配置，项目均容易运行，可在短期内完成演示。"
  },
  "recommendations": [
    {
      "candidateId": "B:36",
      "name": "ASF 网页配置生成器",
      "description": "表单驱动的配置文件生成工具，通过 Vue 组件构建表单、处理用户输入并生成 JSON 配置，适合练习组件通信与数据逻辑。",
      "difficulty": "入门",
      "difficultyReason": "仅需 JavaScript 和 Vue 基础，无复杂状态管理或 TypeScript，适合刚学前端的新手。",
      "vibeCodingReason": "组件结构清晰、数据流简单，AI 可辅助生成表单模板、校验逻辑与配置导出代码。",
      "recommendReason": "项目结构直观，运行即用；通过完成表单配置功能，可深入理解 Vue 组件化开发，适合作为首个演示项目，为简历增加前端实践经历。",
      "prerequisites": "HTML、CSS、JavaScript 基础，Vue 基本语法与组件概念",
      "evidenceIds": [
        "B:36"
      ],
      "score": 91,
      "references": [
        {
          "evidenceIds": [
            "B:36"
          ],
          "url": "https://github.com/justarchinet/asf-webconfiggenerator",
          "name": "justarchinet/asf-webconfiggenerator"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    },
    {
      "candidateId": "B:46",
      "name": "Protocol Launcher 协议启动器",
      "description": "基于 TypeScript 和 Vue 的跨应用协议处理工具，展示类型安全与自定义协议交互，可练习 TS 高级特性与 Vue 结合。",
      "difficulty": "中等",
      "difficultyReason": "需要理解 TypeScript 基础与 Vue 集成，对刚学 JavaScript 的用户略具挑战，但在一个月内可通过边做边学掌握。",
      "vibeCodingReason": "类型定义、协议通信逻辑明确，AI 可辅助生成接口定义和异常处理代码，加速开发与学习。",
      "recommendReason": "项目涉及前端与操作系统交互，能体现解决复杂问题的能力；完成后可深入理解 TS 类型系统，增强技术深度，有利于求职时展示工程化思维。",
      "prerequisites": "JavaScript 基础，了解 TypeScript 基本类型与接口，Vue 基础",
      "evidenceIds": [
        "B:46"
      ],
      "score": 95,
      "references": [
        {
          "evidenceIds": [
            "B:46"
          ],
          "url": "https://github.com/zhensherlock/protocol-launcher",
          "name": "zhensherlock/protocol-launcher"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    }
  ],
  "learning": {
    "steps": [
      "复习 Vue 组件、指令和双向绑定基础",
      "克隆推荐项目，本地运行并理解现有结构",
      "扩展或修改核心功能（如表单字段、协议注册）",
      "优化界面与交互细节，打包成可演示产物"
    ],
    "timeline": "约 4 周，每周投入 8-10 小时",
    "tips": "先运行原版，逐步修改子组件；利用 Vue Devtools 调试数据流；遇到类型问题可借助 AI 给出类型定义参考。"
  }
}
```

## vue_learning / 第 2 轮 / A

耗时 42.78s；检索 0.50s；按标价估算 ¥0.0729。

### 输入

{"topics": ["Vue"], "purpose": "学习", "experience": "刚学", "languages": ["JavaScript"], "timeCommitment": "一个月内", "customPurpose": "做一个能演示的前端项目，优先容易运行的例子", "useRag": true, "usePersona": false, "preferBeginnerProjects": true, "preferCareerProjects": false}

### 实际 Prompt 证据

[candidateId=E1;evidenceId=E1;repo=davidhdev/canvas-ui] facts=技术:TypeScript,React,Next.js;证据:部分组件依赖实验性API（HTML-in-canvas），某些浏览器可能降级；视觉效果可能影响性能。; TypeScript,React,Next.; ["animation", "canvas", "component-library", "comp…

[candidateId=E2;evidenceId=E2;repo=justarchinet/asf-webconfiggenerator] facts=摘要:一款基于Vue.js的ArchiSteamFarm配置文件在线生成器。;技术:JavaScript,Vue;证据:以表单驱动的配置生成工具，可练习Vue组件开发和数据配置逻辑，门槛较低。; JavaScript,Vue; ["archisteamfarm", "css", "…

[candidateId=E3;evidenceId=E3;repo=pocket-stack/pocketjs] facts=摘要:浏览器外的高性能 JSX UI 框架，支持硬件渲染、Vue Vapor 和 Solid 标准、Tailwind 样式引擎，在 8 MB 内存下实现 60 FPS 动画。;技术:TypeScript,Vue;证据:新手友好项目; 浏览器外的高性能 JSX UI 框架，支持硬件渲染、Vue Vapor 和…

### 最终内容

```json
{
  "analysis": {
    "trend": "低门槛Vue表单类项目适合新手积累组件化与配置驱动开发经验",
    "prospect": "可扩展为通用配置生成器或后台工具，具备演示与二次开发价值",
    "demand": "前端岗位对Vue组件化、表单处理和数据绑定能力有持续需求",
    "summary": "推荐Vue配置生成器作为主项目，避开React/TS高阶候选，匹配刚学JavaScript的演示需求"
  },
  "recommendations": [
    {
      "candidateId": "B:36",
      "name": "ASF Web 配置生成器",
      "description": "基于Vue.js的ArchiSteamFarm配置文件在线生成器，以表单驱动方式生成配置，可练习Vue组件开发与数据配置逻辑。",
      "difficulty": "入门",
      "difficultyReason": "项目使用JavaScript和Vue，以表单交互为主，符合用户刚学JavaScript的基础，无需深入算法或复杂架构。",
      "vibeCodingReason": "表单、配置项和生成逻辑清晰，适合AI辅助拆分为Vue组件并快速迭代。",
      "recommendReason": "与用户Vue学习目标一致，运行简单、功能可见，能在一个月内完成可演示项目；同时覆盖组件开发、表单绑定和配置导出，适合作为学习作品。",
      "prerequisites": "JavaScript基础、HTML/CSS基础、Vue基础语法",
      "evidenceIds": [
        "B:36"
      ],
      "score": 90,
      "references": [
        {
          "evidenceIds": [
            "B:36"
          ],
          "url": "https://github.com/justarchinet/asf-webconfiggenerator",
          "name": "justarchinet/asf-webconfiggenerator"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    }
  ],
  "learning": {
    "steps": [
      "第一步：克隆项目并安装依赖，在本地启动Vue开发服务器，查看表单页面",
      "第二步：通读主要Vue组件，理解表单数据模型与配置生成逻辑",
      "第三步：仿照现有模块新增一个配置区块或导出格式，完成自己的演示改动"
    ],
    "timeline": "3-4周",
    "tips": "先运行起来再改；遇到复杂配置逻辑时用console或Vue DevTools观察数据流。"
  }
}
```

## vue_learning / 第 2 轮 / B

耗时 60.96s；检索 1.33s；按标价估算 ¥0.0998。

### 输入

{"topics": ["Vue"], "purpose": "学习", "experience": "刚学", "languages": ["JavaScript"], "timeCommitment": "一个月内", "customPurpose": "做一个能演示的前端项目，优先容易运行的例子", "useRag": true, "usePersona": false, "preferBeginnerProjects": true, "preferCareerProjects": false}

### 实际 Prompt 证据

[candidateId=E1;evidenceId=E1;repo=zhensherlock/protocol-launcher;mode=RAW_FALLBACK] facts=技术栈 TypeScript,Vue 适合新手的原因 聚焦于类型安全和协议处理，前端使用 Vue，适合学习 TypeScript 高级特性和跨应用通信。

[candidateId=E2;evidenceId=E2;repo=justarchinet/asf-webconfiggenerator] facts=技术:JavaScript,Vue;证据:JavaScript,Vue; 以表单驱动的配置生成工具，可练习Vue组件开发和数据配置逻辑，门槛较低。

[candidateId=E3;evidenceId=E3;repo=pocket-stack/pocketjs;mode=RAW_FALLBACK] facts=技术栈 TypeScript,Vue

### 最终内容

```json
{
  "analysis": {
    "trend": "Vue仍是前端主流框架，组件化与TypeScript结合趋势明显，适合新手构建可演示项目。",
    "prospect": "掌握Vue及配置/工具类小项目，有助于积累组件开发与工程化经验，为后续求职或进阶打基础。",
    "demand": "用户为刚学JavaScript的Vue学习者，需要门槛低、运行简单、能快速做出可演示前端项目。",
    "summary": "Vue学习需求明确，优先易运行、可演示项目，结合表单与类型安全两个方向，可在一个月内完成并展示。"
  },
  "recommendations": [
    {
      "candidateId": "B:36",
      "name": "ASF-WebConfigGenerator 配置生成器",
      "description": "基于Vue的配置生成工具，通过表单驱动生成ASF配置文件，适合练习Vue组件开发、双向数据绑定与配置逻辑，项目运行门槛低，便于快速搭建演示页面。",
      "difficulty": "入门",
      "difficultyReason": "项目使用JavaScript与Vue，无复杂构建或后端依赖，刚学JavaScript的用户可快速上手并专注于组件与表单逻辑。",
      "vibeCodingReason": "表单驱动、组件边界清晰，AI可快速生成配置项组件与校验逻辑，用户只需关注Vue数据流和交互调整。",
      "recommendReason": "该工具完全符合你一个月内做可演示前端项目的目标：门槛低、易运行，且以表单和配置生成为核心，能快速做出实际可操作的页面。通过练习Vue组件开发和数据绑定，可扎实掌握Vue基础，适合作为第一个学习项目。",
      "prerequisites": "HTML/CSS基础、JavaScript基础、Vue指令与组件基础",
      "evidenceIds": [
        "B:36"
      ],
      "score": 91,
      "references": [
        {
          "evidenceIds": [
            "B:36"
          ],
          "url": "https://github.com/justarchinet/asf-webconfiggenerator",
          "name": "justarchinet/asf-webconfiggenerator"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    },
    {
      "candidateId": "B:46",
      "name": "Protocol Launcher 协议启动器",
      "description": "采用TypeScript与Vue的协议启动器，聚焦类型安全和协议处理，适合学习TypeScript高级特性以及跨应用通信，可作为稍复杂但完整的前端演示项目。",
      "difficulty": "入门",
      "difficultyReason": "虽使用TypeScript，但候选难度为入门，且有明确协议处理场景，适合在学会基础JS后边做边补类型知识。",
      "vibeCodingReason": "类型安全使AI辅助生成代码时更易约束接口与数据结构，协议处理逻辑清晰，适合通过AI补全后阅读理解。",
      "recommendReason": "在完成基础表单项目后，可尝试这个更偏工程化的Vue+TypeScript项目。它能展示类型约束和跨应用通信能力，让你的演示项目不止于增删改查，更有技术亮点，适合写入学习作品集并为后续求职加分。",
      "prerequisites": "JavaScript基础、Vue基础、了解TypeScript基本类型",
      "evidenceIds": [
        "B:46"
      ],
      "score": 95,
      "references": [
        {
          "evidenceIds": [
            "B:46"
          ],
          "url": "https://github.com/zhensherlock/protocol-launcher",
          "name": "zhensherlock/protocol-launcher"
        }
      ],
      "evidenceStatus": "VERIFIED_CANDIDATE",
      "unsupportedReferenceCount": 0
    }
  ],
  "learning": {
    "steps": [
      "第1周：复习JavaScript与Vue基础，跑通ASF-WebConfigGenerator，理解表单绑定与组件拆分",
      "第2-3周：模仿并扩展配置项与表单校验，做出自己的配置生成工具演示",
      "第4周：尝试阅读protocol-launcher的TypeScript与协议处理部分，提取可演示亮点并整理项目说明"
    ],
    "timeline": "预计4周，每周投入约8-10小时",
    "tips": "保持小步提交，先跑通官方示例再修改；利用AI生成样板代码时手动核对数据流；重点记录Vue组件通信和表单校验的实践心得。"
  }
}
```
