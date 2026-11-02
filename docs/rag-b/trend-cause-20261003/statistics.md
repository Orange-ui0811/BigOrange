# 成本与耗时归因原始统计

统一公开标价估算，不是账单。嵌套阶段不可相加。

| 分组 | 成功/尝试 | 模型秒 | 输入 Token | 输出 Token | 思考 Token | 非思考 Token | 均次估算元 |
|---|---:|---:|---:|---:|---:|---:|---:|
|A|12/12|42.5|797.3|2481.0|1926.9|554.1|0.06911|
|B|11/12|40.3|629.6|2127.5|1626.3|501.2|0.05861|
|noKnowledge|8/8|27.3|575.8|1434.6|1003.2|431.4|0.04134|

## 逐题重复均值

| 分组 | 成功/尝试 | 模型秒 | 输入 Token | 输出 Token | 思考 Token | 非思考 Token | 均次估算元 |
|---|---:|---:|---:|---:|---:|---:|---:|
|spring_beginner / A|3/3|62.0|714.0|3899.0|3294.7|604.3|0.10214|
|spring_beginner / B|3/3|59.1|570.3|3203.0|2656.7|546.3|0.08372|
|spring_beginner / noKnowledge|2/2|13.0|420.5|556.0|358.0|198.0|0.01839|
|rag_python / A|3/3|51.5|1042.0|3061.3|2157.7|903.7|0.08598|
|rag_python / B|3/3|39.4|868.3|2223.0|1606.0|617.0|0.06377|
|rag_python / noKnowledge|2/2|44.9|773.0|2671.5|2113.5|558.0|0.07339|
|data_analysis / A|3/3|40.1|963.3|2118.0|1681.7|436.3|0.06239|
|data_analysis / B|2/3|44.1|679.0|2047.5|1378.5|669.0|0.05729|
|data_analysis / noKnowledge|2/2|37.1|679.0|1842.0|1120.5|721.5|0.05236|
|unavailable / A|3/3|16.2|470.0|845.7|573.7|272.0|0.02594|
|unavailable / B|3/3|19.9|417.3|1009.7|781.3|228.3|0.02924|
|unavailable / noKnowledge|2/2|14.1|430.5|669.0|421.0|248.0|0.02122|

## 检索阶段均值（毫秒，12 次/版）

|阶段|A|B|
|---|---:|---:|
|A.embeddingAndVector|492.860|0.000|
|A.lexical|2.427|0.000|
|A.renderEvidence|0.720|0.000|
|A.restoreMetadata|18.008|0.000|
|A.selectProjects|0.208|0.000|
|A.topicGateAndFusion|3.368|0.000|
|B.activeGeneration|0.000|1.862|
|B.adapterTopicGateAndFusion|0.000|2.875|
|B.databaseMetadataAndFilters|0.000|225.246|
|B.dedupAndSelect|0.000|21.170|
|B.embeddingAndVector|0.000|1495.716|
|B.indexStore|0.000|5.431|
|B.lexical|0.000|9.356|
|B.names|0.000|4.137|
|B.native.total|0.000|1787.191|
|B.nativeFusion|0.000|23.233|
|B.relevanceAndPolicy|0.000|0.301|
|B.renderEvidence|0.000|3.201|
|embedding.http.bge-m3|0.000|1454.478|
|embedding.http.bge-small-zh-v1.5|480.473|0.000|
|retrieval.total|517.820|1793.464|

## 完整链路阶段均值（毫秒，4 次/版）

|阶段|A|B|
|---|---:|---:|
|A.embeddingAndVector|608.578|0.000|
|A.lexical|2.376|0.000|
|A.renderEvidence|0.489|0.000|
|A.restoreMetadata|14.355|0.000|
|A.selectProjects|0.102|0.000|
|A.topicGateAndFusion|2.908|0.000|
|B.activeGeneration|0.000|2.000|
|B.adapterTopicGateAndFusion|0.000|0.740|
|B.databaseMetadataAndFilters|0.000|224.350|
|B.dedupAndSelect|0.000|25.095|
|B.embeddingAndVector|0.000|864.145|
|B.indexStore|0.000|0.011|
|B.lexical|0.000|9.711|
|B.names|0.000|4.351|
|B.native.total|0.000|1157.311|
|B.nativeFusion|0.000|26.922|
|B.relevanceAndPolicy|0.000|0.287|
|B.renderEvidence|0.000|0.313|
|business.beginner|8.542|6.461|
|business.career|5.676|13.690|
|embedding.http.bge-m3|0.000|822.590|
|embedding.http.bge-small-zh-v1.5|598.611|0.000|
|graph.database|118.798|130.562|
|model.http|53341.874|48546.266|
|output.bindSources|0.085|2.106|
|output.parseAndAssemble|0.449|8.003|
|prompt.compose|0.119|0.374|
|prompt.protocol|0.006|0.029|
|retrieval.total|628.911|1158.488|

## 同返回版本配对子集

配对数：4，没有过滤失败后的替补调用。

| 分组 | 成功/尝试 | 模型秒 | 输入 Token | 输出 Token | 思考 Token | 非思考 Token | 均次估算元 |
|---|---:|---:|---:|---:|---:|---:|---:|
|matched A|4/4|40.5|790.8|2272.5|1709.0|563.5|0.06403|
|matched B|4/4|56.6|673.2|3333.2|2805.0|528.2|0.08808|

## 冻结与公共无证据对照

```json
{
  "spring_beginner": {
    "hashes": {
      "A": "bca5c863ef056742f179e5c4dc546d993a1cc181e0012389b6b73f05cc901930",
      "B": "bca5c863ef056742f179e5c4dc546d993a1cc181e0012389b6b73f05cc901930"
    },
    "common": true
  },
  "rag_python": {
    "hashes": {
      "A": "b027d06223e7aa16b1d510db7b93be83073e1205a9842c6e84587f34cfeb820b",
      "B": "b027d06223e7aa16b1d510db7b93be83073e1205a9842c6e84587f34cfeb820b"
    },
    "common": true
  },
  "data_analysis": {
    "hashes": {
      "A": "2473685affdd6bb157530f8e838eeec68818d9dd24aeda3bae5da3535b844613",
      "B": "2473685affdd6bb157530f8e838eeec68818d9dd24aeda3bae5da3535b844613"
    },
    "common": true
  },
  "unavailable": {
    "hashes": {
      "A": "9845134f2739e4506d3e0b3c2e1b036bda4a605468cbb63e91bda3b165f47941",
      "B": "9845134f2739e4506d3e0b3c2e1b036bda4a605468cbb63e91bda3b165f47941"
    },
    "common": true
  }
}
```

## 相同请求波动范围

```json
{
  "spring_beginner": {
    "A": {
      "modelMs": [
        37759.162278,
        75637.918789
      ],
      "completionTokens": [
        2356,
        4691
      ],
      "reasoningTokens": [
        1926,
        4029
      ]
    },
    "B": {
      "modelMs": [
        45090.451597,
        87106.85327
      ],
      "completionTokens": [
        2099,
        5388
      ],
      "reasoningTokens": [
        1392,
        4909
      ]
    }
  },
  "rag_python": {
    "A": {
      "modelMs": [
        41614.782385,
        60257.158354
      ],
      "completionTokens": [
        2302,
        3768
      ],
      "reasoningTokens": [
        1427,
        2764
      ]
    },
    "B": {
      "modelMs": [
        22064.893741,
        65098.508156
      ],
      "completionTokens": [
        983,
        4258
      ],
      "reasoningTokens": [
        484,
        3489
      ]
    }
  },
  "data_analysis": {
    "A": {
      "modelMs": [
        26170.131899,
        66657.130389
      ],
      "completionTokens": [
        1161,
        4011
      ],
      "reasoningTokens": [
        870,
        3272
      ]
    },
    "B": {
      "modelMs": [
        43088.694228,
        45013.69801
      ],
      "completionTokens": [
        1951,
        2144
      ],
      "reasoningTokens": [
        1320,
        1437
      ]
    }
  },
  "unavailable": {
    "A": {
      "modelMs": [
        11644.664207,
        18597.425513
      ],
      "completionTokens": [
        699,
        936
      ],
      "reasoningTokens": [
        448,
        696
      ]
    },
    "B": {
      "modelMs": [
        12276.696354,
        31089.743431
      ],
      "completionTokens": [
        526,
        1736
      ],
      "reasoningTokens": [
        289,
        1502
      ]
    }
  }
}
```

## 与模型耗时的相关系数（观察相关，不能单独证明因果）

```json
{
  "completionTokens": 0.9857041062757017,
  "reasoningTokens": 0.9667090703271892,
  "visibleTokens": 0.7100953748278352,
  "promptTokens": 0.5366355320082928
}
```
