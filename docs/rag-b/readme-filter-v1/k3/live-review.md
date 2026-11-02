# K3 两次真实验证与原文选择

仅作接口、选择校验和失败回落验证；不是全量分类或独立质量评估。
首次无效响应未保留具体字段，不能断言具体错误来源；之后已补诊断码，未追加真实重试。

## alphacep/vosk-api

请求状态：{'REJECTED_OR_FAILED': 1}。

没有采用模型选择，复核原文保留。

## konvajs/konva

请求状态：{'MODEL_VALIDATED': 1, 'BUDGET_EXHAUSTED': 3}。

### L5–L5 · DROP

类型提示 OTHER；范围提示 UNKNOWN；原因 NOISE。
合并后决定 DROP；原文引用 UTF-16 [103, 133)。

~~~~~~~~~~~~text
<h1 align="center">Konva</h1>
~~~~~~~~~~~~

### L18–L18 · KEEP

类型提示 PURPOSE；范围提示 CORE；原因 KNOWLEDGE。
合并后决定 KEEP；原文引用 UTF-16 [1212, 1478)。

~~~~~~~~~~~~text
You can draw things onto the stage, add event listeners to them, move them, scale them, and rotate them independently from other shapes to support high performance animations, even if your application uses thousands of shapes. Served hot with a side of awesomeness.
~~~~~~~~~~~~

### L20–L20 · KEEP

类型提示 MAINTENANCE；范围提示 CORE；原因 KNOWLEDGE。
合并后决定 KEEP；原文引用 UTF-16 [1479, 1588)。

~~~~~~~~~~~~text
This repository began as a GitHub fork of [ericdrowell/KineticJS](https://github.com/ericdrowell/KineticJS).
~~~~~~~~~~~~

### L69–L69 · KEEP

类型提示 LIMITATION；范围提示 PLATFORM_SPECIFIC；原因 KNOWLEDGE。
合并后决定 KEEP；原文引用 UTF-16 [3246, 3436)。

~~~~~~~~~~~~text
Konva works in all modern mobile and desktop browsers. A browser need to be capable to run javascript code from ES2015 spec. For older browsers you may need polyfills for missing functions.
~~~~~~~~~~~~

