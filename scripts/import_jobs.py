"""批量导入岗位 MD 文件——完整版（公司/元信息存 content，display_content 剥离敏感信息）"""
import os, re, urllib.request, json

MD_DIR = r"C:\Users\l\Desktop\各公司实习要求"
API = "http://localhost:8080/admin/job/import"
TOKEN_URL = "http://localhost:8080/admin/auth/login"
SKIP_FILES = {"飞书招聘链接汇总.md", "按技术方向分类.md"}

COMPANIES = ["拓竹", "字节跳动", "腾讯", "阿里巴巴", "百度", "美团", "华为", "网易", "米哈游", "小米", "DJI",
             "Bambu", "ByteDance", "Tencent", "Alibaba", "Meituan", "Huawei"]
META_PATTERNS = [
    r'-\s*\*\*类型\*\*.*',
    r'-\s*\*\*地点\*\*.*',
    r'-\s*\*\*部门\*\*.*',
    r'-\s*\*\*学历\*\*.*',
    r'-\s*\*\*面向\*\*.*',
    r'-\s*\*\*面试城市\*\*.*',
    r'-\s*\*\*职位 ID\*\*.*',
    r'-\s*\*\*来源\*\*.*',
    r'-\s*\*\*笔试\*\*.*',
]

DIRECTION_MAP = {
    "Agent": "AI Agent", "RAG": "RAG", "Infra": "AI Infra", "可观测": "AI Infra",
    "大模型算法": "LLM 算法", "大模型应用": "LLM 应用", "算法开发": "LLM 应用",
    "算法研究员": "LLM 研究", "AI全栈": "AI 全栈", "后端开发": "后端开发",
    "AI效能": "AI 效能工具", "AI研发": "LLM 应用", "AI应用研发": "LLM 应用",
    "AI产品": "AI 产品", "产品经理": "AI 产品", "AI模型工程师": "LLM 算法",
    "测试开发": "测试开发", "后台开发": "后端开发", "技术美术": "创意技术",
    "AI解决方案架构师": "AI 架构", "AI原生工程师": "AI 全栈",
    "质量保障": "AI Infra", "性能优化": "AI Infra", "产品运营": "AI 产品",
    "开发者社区": "AI 产品", "Post-training": "LLM 研究",
}

def login():
    data = json.dumps({"username":"admin","password":"123456"}).encode()
    req = urllib.request.Request(TOKEN_URL, data=data, headers={"Content-Type":"application/json"})
    return json.loads(urllib.request.urlopen(req).read())["data"]["token"]

def strip_display(text):
    """剥离公司名和元信息行"""
    for c in COMPANIES:
        text = text.replace(c, "该公司")
    for pat in META_PATTERNS:
        text = re.sub(pat, '', text, flags=re.MULTILINE)
    text = re.sub(r'\n{3,}', '\n\n', text)
    return text.strip()

def parse_md(filepath):
    with open(filepath, "r", encoding="utf-8") as f:
        text = f.read()

    basename = os.path.basename(filepath).replace(".md", "")
    parts = basename.split("--", 1)
    company = parts[0].strip() if len(parts) > 0 else ""
    position = parts[1].strip() if len(parts) > 1 else basename

    # display_content：剥离敏感信息后的公开版
    display = strip_display(text)

    # 方向推断
    direction = ""
    for kw, d in DIRECTION_MAP.items():
        if kw in position or kw in basename:
            direction = d; break

    return company, position, direction, text, display

def main():
    token = login()
    files = sorted([f for f in os.listdir(MD_DIR) if f.endswith(".md") and f not in SKIP_FILES])
    total, success, skip = len(files), 0, 0

    for i, fname in enumerate(files):
        filepath = os.path.join(MD_DIR, fname)
        try:
            company, position, direction, content, display = parse_md(filepath)
            body = json.dumps({"company":company,"position":position,"direction":direction,
                "content":content,"displayContent":display}, ensure_ascii=False).encode("utf-8")
            req = urllib.request.Request(API, data=body,
                headers={"Content-Type":"application/json; charset=utf-8","Admin-Token":token})
            resp = json.loads(urllib.request.urlopen(req).read())
            if resp["code"] == 1: success += 1; print(f"[{i+1}/{total}] OK  {company} - {position}")
            elif "已存在" in str(resp.get("msg","")): skip += 1; print(f"[{i+1}/{total}] SKIP {company} - {position}")
            else: print(f"[{i+1}/{total}] FAIL {company} - {position}: {resp.get('msg','')}")
        except Exception as e:
            print(f"[{i+1}/{total}] ERR {fname}: {e}")

    print(f"\n完成: {success} 成功, {skip} 跳过, 共 {total}")

if __name__ == "__main__":
    main()
