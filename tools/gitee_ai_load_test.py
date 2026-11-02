import argparse
import asyncio
import json
import os
import statistics
import time
import urllib.error
import urllib.request
import hashlib
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path


REALISTIC_TREND_PROMPT = """
你是 OrangeJD 平台的 AI 技术趋势分析师。请根据以下用户画像生成一份可执行的职业与开源项目趋势分析。

用户画像：
- 当前岗位：Java 后端开发，3 年经验
- 技术栈：Java、Spring Boot、MySQL、Redis、Vue、Docker
- 目标方向：AI 应用工程、RAG、Agent、开源协作
- 每周可投入时间：10 小时
- 期望周期：未来 12 周
- 约束：不脱产、预算有限、希望形成可展示的 GitHub/Gitee 作品

分析要求：
1. 判断未来 6—12 个月值得关注的 3 个技术方向，并说明机会、拥挤度、学习门槛和风险。
2. 为每个方向给出一个适合该用户的项目方案，必须包含目标用户、核心功能、技术架构、数据来源、
   难点、最小可行版本和可量化验收指标。
3. 给出 12 周行动计划，按周列出学习、开发、测试和开源协作任务。
4. 给出岗位能力差距和优先补齐顺序。
5. 不要虚构实时数据；缺少数据时明确标注为经验判断。

请只返回严格 JSON，不要 Markdown。结构如下：
{
  "summary": "...",
  "directions": [
    {
      "name": "...",
      "opportunity": "...",
      "crowdedness": "...",
      "difficulty": "...",
      "risks": ["..."],
      "project": {
        "name": "...",
        "targetUsers": ["..."],
        "coreFeatures": ["..."],
        "architecture": ["..."],
        "dataSources": ["..."],
        "challenges": ["..."],
        "mvp": ["..."],
        "metrics": ["..."]
      }
    }
  ],
  "weeklyPlan": [{"week": 1, "goals": ["..."], "deliverables": ["..."]}],
  "skillGaps": [{"skill": "...", "priority": 1, "action": "..."}],
  "risks": ["..."]
}
""".strip()


def load_env(path: Path) -> None:
    if not path.exists():
        return
    for raw in path.read_text(encoding="utf-8-sig").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        name, value = line.split("=", 1)
        os.environ[name.strip()] = value.strip().strip("\"'")


def percentile(values: list[float], pct: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, int(len(ordered) * pct + 0.999999) - 1))
    return ordered[index]


def call_once(
    url: str,
    key: str,
    model: str,
    max_tokens: int,
    timeout: int,
    failover: str,
    prompt: str,
) -> dict:
    payload = json.dumps({
        "model": model,
        "messages": [{"role": "user", "content": prompt}],
        "temperature": 0,
        "max_tokens": max_tokens,
        "stream": False,
    }, ensure_ascii=False).encode("utf-8")
    headers = {
        "Authorization": f"Bearer {key}",
        "Content-Type": "application/json",
    }
    if failover != "default":
        headers["X-Failover-Enabled"] = failover
    request = urllib.request.Request(
        url,
        data=payload,
        method="POST",
        headers=headers,
    )
    started = time.perf_counter()
    status = 0
    error = ""
    usage = {}
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            body = json.loads(response.read().decode("utf-8"))
            status = response.status
            usage = body.get("usage") or {}
    except urllib.error.HTTPError as exc:
        status = exc.code
        try:
            error = exc.read().decode("utf-8")[:500]
        except Exception:
            error = str(exc)
    except Exception as exc:
        status = 0
        error = f"{type(exc).__name__}: {exc}"
    return {
        "status": status,
        "elapsed": time.perf_counter() - started,
        "error": error,
        "prompt_tokens": int(usage.get("prompt_tokens") or 0),
        "completion_tokens": int(usage.get("completion_tokens") or 0),
        "total_tokens": int(usage.get("total_tokens") or 0),
    }


async def run_stage(concurrency: int, requests_count: int, keys: list[str], **kwargs) -> dict:
    semaphore = asyncio.Semaphore(concurrency)

    async def bounded_call(index: int) -> dict:
        async with semaphore:
            return await asyncio.to_thread(call_once, key=keys[index % len(keys)], **kwargs)

    started = time.perf_counter()
    results = await asyncio.gather(*(bounded_call(index) for index in range(requests_count)))
    duration = time.perf_counter() - started
    latencies = [item["elapsed"] for item in results]
    successes = sum(1 for item in results if 200 <= item["status"] < 300)
    status_counts = {}
    for item in results:
        key = str(item["status"]) if item["status"] else "transport_error"
        status_counts[key] = status_counts.get(key, 0) + 1
    return {
        "concurrency": concurrency,
        "requests": requests_count,
        "successes": successes,
        "errors": requests_count - successes,
        "success_rate": successes / requests_count if requests_count else 0,
        "duration_seconds": round(duration, 3),
        "throughput_rpm": round(requests_count / duration * 60, 2),
        "latency_avg_seconds": round(statistics.mean(latencies), 3),
        "latency_p50_seconds": round(percentile(latencies, 0.50), 3),
        "latency_p95_seconds": round(percentile(latencies, 0.95), 3),
        "latency_p99_seconds": round(percentile(latencies, 0.99), 3),
        "total_tokens": sum(item["total_tokens"] for item in results),
        "status_counts": status_counts,
        "sample_errors": list(dict.fromkeys(
            item["error"] for item in results if item["error"]
        ))[:3],
        "results": results,
    }


async def run_duration_stage(
    concurrency: int,
    duration_seconds: int,
    max_total_tokens: int,
    keys: list[str],
    **kwargs,
) -> dict:
    deadline = time.perf_counter() + duration_seconds
    results = []
    lock = asyncio.Lock()
    stop = asyncio.Event()

    async def worker(index: int) -> None:
        while time.perf_counter() < deadline and not stop.is_set():
            result = await asyncio.to_thread(
                call_once, key=keys[index % len(keys)], **kwargs
            )
            async with lock:
                results.append(result)
                used = sum(item["total_tokens"] for item in results)
                errors = sum(1 for item in results if not 200 <= item["status"] < 300)
                if used >= max_total_tokens:
                    stop.set()
                if result["status"] == 429 or (len(results) >= 10 and errors / len(results) > 0.01):
                    stop.set()

    started = time.perf_counter()
    await asyncio.gather(*(worker(index) for index in range(concurrency)))
    elapsed = time.perf_counter() - started
    latencies = [item["elapsed"] for item in results]
    successes = sum(1 for item in results if 200 <= item["status"] < 300)
    status_counts = {}
    for item in results:
        key = str(item["status"]) if item["status"] else "transport_error"
        status_counts[key] = status_counts.get(key, 0) + 1
    return {
        "concurrency": concurrency,
        "target_duration_seconds": duration_seconds,
        "actual_duration_seconds": round(elapsed, 3),
        "requests": len(results),
        "successes": successes,
        "errors": len(results) - successes,
        "success_rate": successes / len(results) if results else 0,
        "throughput_rpm": round(len(results) / elapsed * 60, 2) if elapsed else 0,
        "latency_avg_seconds": round(statistics.mean(latencies), 3) if latencies else 0,
        "latency_p50_seconds": round(percentile(latencies, 0.50), 3),
        "latency_p95_seconds": round(percentile(latencies, 0.95), 3),
        "latency_p99_seconds": round(percentile(latencies, 0.99), 3),
        "prompt_tokens": sum(item["prompt_tokens"] for item in results),
        "completion_tokens": sum(item["completion_tokens"] for item in results),
        "total_tokens": sum(item["total_tokens"] for item in results),
        "status_counts": status_counts,
        "sample_errors": list(dict.fromkeys(
            item["error"] for item in results if item["error"]
        ))[:3],
        "results": results,
    }


async def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--env-file", default=".env.local")
    parser.add_argument("--levels", default="1,2,4")
    parser.add_argument("--requests-per-level", type=int, default=4)
    parser.add_argument("--max-tokens", type=int, default=8)
    parser.add_argument("--timeout", type=int, default=120)
    parser.add_argument("--failover", choices=("default", "true", "false"), default="default")
    parser.add_argument("--output", default="build/gitee-ai-load-test.json")
    parser.add_argument("--profile", choices=("tiny", "realistic-trend"), default="tiny")
    parser.add_argument("--duration-levels", default="")
    parser.add_argument("--max-total-tokens", type=int, default=100000)
    parser.add_argument("--key-names", default="AI_TREND_KEY")
    parser.add_argument("--endpoint", default="")
    parser.add_argument("--model", default="")
    args = parser.parse_args()

    load_env(Path(args.env_file))
    endpoint = (args.endpoint or os.getenv("AI_TREND_ENDPOINT", "")).rstrip("/")
    key_names = [name.strip() for name in args.key_names.split(",") if name.strip()]
    keys = [os.getenv(name, "") for name in key_names]
    model = args.model or os.getenv("AI_TREND_MODEL", "")
    if not endpoint or not keys or any(not key for key in keys) or not model:
        raise SystemExit("Endpoint, all requested API keys, and model are required")

    url = endpoint + "/chat/completions"
    prompt = REALISTIC_TREND_PROMPT if args.profile == "realistic-trend" else "只回复一个词：收到"
    report = {
        "endpoint": endpoint,
        "model": model,
        "max_tokens": args.max_tokens,
        "failover": args.failover,
        "profile": args.profile,
        "key_fingerprints": [
            hashlib.sha256(key.encode("utf-8")).hexdigest()[:12].upper()
            for key in keys
        ],
        "started_at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "stages": [],
    }
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)

    duration_levels = []
    if args.duration_levels:
        duration_levels = [
            tuple(int(part) for part in item.split(":", 1))
            for item in args.duration_levels.split(",") if item.strip()
        ]
    levels = duration_levels or [
        (int(value), 0) for value in args.levels.split(",") if value.strip()
    ]
    max_concurrency = max(level for level, _ in levels)
    asyncio.get_running_loop().set_default_executor(
        ThreadPoolExecutor(max_workers=max_concurrency)
    )
    remaining_tokens = args.max_total_tokens
    for level, duration in levels:
        common = dict(
            url=url, model=model, max_tokens=args.max_tokens,
            timeout=args.timeout, failover=args.failover, prompt=prompt,
        )
        if duration:
            stage = await run_duration_stage(
                level, duration, remaining_tokens, keys=keys, **common
            )
            remaining_tokens -= stage["total_tokens"]
        else:
            stage = await run_stage(
                level, args.requests_per_level, keys=keys, **common
            )
        report["stages"].append(stage)
        output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        summary = {key: value for key, value in stage.items() if key not in ("results", "sample_errors")}
        print(json.dumps(summary, ensure_ascii=False))
        if stage["success_rate"] < 0.99 or stage["status_counts"].get("429", 0):
            print("STOP: safety threshold reached")
            break
        if remaining_tokens <= 0:
            print("STOP: token budget reached")
            break

    report["finished_at"] = time.strftime("%Y-%m-%dT%H:%M:%S%z")
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))
