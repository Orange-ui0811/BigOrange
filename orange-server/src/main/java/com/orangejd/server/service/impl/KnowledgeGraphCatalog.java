package com.orangejd.server.service.impl;

import java.util.LinkedHashMap;
import java.util.Map;

final class KnowledgeGraphCatalog {
static final String[] VC_KEYWORDS = {
        "vibe coding", "vibe-coding", "vibecoding",
        "ai coding", "ai-coding", "ai-coding",
        "cursor", "claude code", "claude-code",
        "copilot", "co-pilot",
        "ai-assisted", "ai assisted", "ai-powered",
        "llm", "agent", "agents", "ai agent",
        "code generation", "codegen",
        "ai ide", "ai editor",
        "prompt", "prompting",
        "chatgpt", "openai", "gpt-4", "gpt4",
        "deepseek", "anthropic",
        "vscode extension", "ai tool",
        "autonomous coding", "ai developer",
        "superpowers", "codex"
    };
static final Map<String, String> TOPIC_NAMES = new LinkedHashMap<>();
    static final Map<String, String> TOPIC_GROUPS = new LinkedHashMap<>();
    static {
        String[][] data = {
            {"creative-coding",    "创意编程",   "创意技术"},
            {"game-development",   "游戏开发",   "创意技术"},
            {"WebGL",              "WebGL",     "创意技术"},
            {"Canvas",             "Canvas",    "创意技术"},
            {"Web-Audio",          "Web音频",    "创意技术"},
            {"ai-agents",          "AI智能体",   "AI 智能"},
            {"RAG",                "RAG检索增强","AI 智能"},
            {"self-hosted",        "自托管",     "自托管"},
            {"home-assistant",     "家庭助理",   "自托管"},
            {"ESP32",              "ESP32",     "硬件/IoT"},
            {"raspberry-pi",       "树莓派",     "硬件/IoT"},
            {"home-automation",    "家居自动化", "硬件/IoT"},
            {"3d-printing",        "3D打印",    "硬件/IoT"},
            {"other",              "其他",      "其他"},
        };
        for (String[] d : data) {
            TOPIC_NAMES.put(d[0], d[1]);
            TOPIC_GROUPS.put(d[0], d[2]);
        }
    }

static final Map<String, String> TOPIC_SUMMARIES = new LinkedHashMap<>();
    static {
        TOPIC_SUMMARIES.put("creative-coding", "利用代码生成视觉艺术、交互装置和动画的开源工具，涵盖生成艺术、着色器编程、音频可视化等创意技术方向。");
        TOPIC_SUMMARIES.put("game-development", "2D/3D 游戏引擎、图形渲染框架和游戏开发工具集，包括跨平台引擎、物理模拟、着色语言等。");
        TOPIC_SUMMARIES.put("WebGL", "基于浏览器的 3D 图形渲染库和框架，涵盖 WebGL 封装、科学可视化、地理信息展示和 GPU 加速计算。");
        TOPIC_SUMMARIES.put("Canvas", "HTML5 Canvas 2D 绘图库、图表可视化工具和数据驱动的图形渲染引擎，主要用于数据展示和交互式图形。");
        TOPIC_SUMMARIES.put("Web-Audio", "浏览器端音频处理库和音频可视化工具，支持音频播放、合成、效果处理和实时频谱分析。");
        TOPIC_SUMMARIES.put("ai-agents", "AI 智能体框架和工具链，利用大语言模型实现自动化任务执行、代码生成、多代理协作和复杂工作流编排。");
        TOPIC_SUMMARIES.put("RAG", "检索增强生成技术栈，将外部知识库与大语言模型结合，实现更准确的问答、文档理解和智能搜索。");
        TOPIC_SUMMARIES.put("self-hosted", "可私有部署的开源应用和服务，包括网络存储、媒体服务器、密码管理器、监控工具等，注重数据隐私和自主可控。");
        TOPIC_SUMMARIES.put("home-assistant", "智能家居开源平台及生态插件，支持多品牌设备接入、自动化场景编排和语音控制。");
        TOPIC_SUMMARIES.put("ESP32", "基于乐鑫 ESP32 芯片的嵌入式开发项目，涵盖物联网终端、传感器网络、智能家居控制和边缘 AI 推理。");
        TOPIC_SUMMARIES.put("raspberry-pi", "树莓派单板计算机相关的开源项目，包括操作系统、NAS 存储、GPIO 控制、家庭服务器和物联网网关。");
        TOPIC_SUMMARIES.put("home-automation", "家居自动化系统和协议实现，涵盖设备互联、场景联动、能源管理和安防监控等开源方案。");
        TOPIC_SUMMARIES.put("3d-printing", "3D 打印固件、切片软件和建模工具的开源实现，服务于从桌面级到工业级的增材制造需求。");
        TOPIC_SUMMARIES.put("other", "涵盖其他领域的热门开源项目，包括开发辅助工具、文档生成、知识管理和社区资源等。");
    }

    private KnowledgeGraphCatalog() {
    }
}

