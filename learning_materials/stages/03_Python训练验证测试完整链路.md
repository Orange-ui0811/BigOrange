先给你一个总定义：

> 一次规范的 Python 机器学习训练—验证—测试流程，是“先冻结问题与数据边界，再隔离测试集，只用训练数据学习参数，用验证数据选择方案，最后让测试集对冻结后的最终方案做一次独立评估，并把同一套预处理与模型一起交付”的完整链路。

它不是简单地调用 `model.fit()`，也不是把数据随机分成三份就结束。完整流程还包括：

- 明确任务、标签和评价指标；
- 防止数据泄漏；
- 选择正确的切分策略；
- 让预处理只在训练数据上拟合；
- 建立基线；
- 用验证集或交叉验证选择模型和超参数；
- 选择分类阈值；
- 冻结方案后只评估一次测试集；
- 保存数据版本、模型、预处理和配置；
- 用单元测试和实验记录保证可复现；
- 上线后监控数据漂移和效果衰减。

最关键的边界是：

> OrangeVC 当前没有机器学习模型训练模块。本阶段是为了补齐 AI Agent 岗位要求中的 Python 与机器学习实验能力，不能包装成 OrangeVC 已经实现的功能。

---

# 一、先建立全局视角：训练、验证、测试各自负责什么

很多面试者会说：

```text
训练集用来训练
验证集用来调参
测试集用来测试
```

这句话没错，但深度不够。真正应该理解的是：

```text
训练集：
允许算法从中学习模型参数

验证集：
允许开发者根据结果做决策

测试集：
在所有决策冻结后，估计最终方案对未知数据的泛化能力
```

换一种说法：

- 训练集影响模型参数；
- 验证集影响人的选择；
- 测试集既不能影响模型参数，也不能影响人的选择。

## 1. 什么叫模型参数

参数是训练算法从数据中学出来的值，例如：

- 线性回归的权重；
- 逻辑回归的系数；
- 决策树的分裂点；
- 神经网络的权重和偏置；
- K-Means 的聚类中心。

它们通常由 `fit()`、梯度下降或其他训练过程得到。

## 2. 什么叫超参数

超参数是训练前由开发者或搜索程序设定的值，例如：

- 学习率；
- 树的最大深度；
- 正则化系数；
- Batch Size；
- 网络层数；
- Dropout；
- KNN 的 K；
- Early Stopping 的 patience。

超参数不能根据测试集表现反复调整，否则测试集就参与了模型选择。

## 3. 什么叫模型选择

模型选择不仅是选择算法，也包括：

- 特征集合；
- 缺失值处理方式；
- 标准化方法；
- 超参数；
- 类别不平衡策略；
- 分类阈值；
- 是否做数据增强；
- Early Stopping 的 epoch；
- 后处理规则。

只要你看了某份数据上的结果，然后据此修改方案，这份数据就在承担验证集的角色。

## 4. 完整实验生命周期

```text
定义问题和指标
→ 获取并检查原始数据
→ 按业务实体或时间建立数据边界
→ 先隔离测试集
→ 只在训练数据上做探索和拟合预处理
→ 建立简单基线
→ 训练候选模型
→ 用验证集或交叉验证选择方案
→ 固定超参数、特征和阈值
→ 在 train + validation 上重训最终模型
→ 测试集只评估一次
→ 保存模型、预处理、数据版本和报告
→ 部署推理
→ 监控漂移并决定何时重训
```

---

# 二、第一步不是切数据，而是定义问题

## 1. 先确定机器学习任务

常见任务：

- 二分类：是否会发生故障；
- 多分类：故障属于哪一类；
- 回归：预测打印误差、剩余时间或质量分数；
- 排序：给候选项目或解决方案排序；
- 异常检测：识别未知故障；
- 时间序列：预测未来传感器状态。

如果问题定义不清，后面的指标和切分都会错。

例如：

```text
业务问题：
提前发现 3D 打印失败

可能的建模目标 A：
预测未来 10 分钟内是否失败

可能的建模目标 B：
判断当前打印是否已经异常

可能的建模目标 C：
预测失败类型
```

三者需要不同的标签、时间窗口和评价方式。

## 2. 明确一条样本是什么

一条样本可能是：

- 一次完整打印任务；
- 某台打印机一分钟的传感器窗口；
- 一张打印过程图像；
- 一个用户的一次操作；
- 一个项目与岗位的匹配对。

需要明确：

```text
X：模型可以看到的输入
y：模型需要预测的目标
entity：样本属于哪台设备、哪个用户或哪个项目
timestamp：这个输入在什么时候可获得
```

timestamp 很重要，因为一个特征如果在预测时刻还不可获得，就是未来信息泄漏。

## 3. 先定义成功指标

不能只说“准确率越高越好”。

要同时定义：

- 离线指标；
- 业务代价；
- 推理延迟；
- 模型大小；
- 可解释性；
- 允许的误报和漏报；
- 上线阈值。

例如故障检测：

```text
主要指标：
Recall ≥ 0.90

约束指标：
Precision ≥ 0.60
P95 推理延迟 < 100 ms

业务原因：
漏掉一次严重打印失败的代价
高于多报一次人工检查
```

指标必须由业务错误成本决定，而不是因为某个指标最常见。

---

# 三、选择正确的数据切分策略

## 1. 普通 IID 随机切分

如果样本独立同分布，可以使用：

```text
训练集 60%
验证集 20%
测试集 20%
```

或：

```text
训练集 70%
验证集 15%
测试集 15%
```

比例不是固定答案，取决于：

- 数据量；
- 标签稀缺程度；
- 模型复杂度；
- 是否使用交叉验证；
- 测试集是否需要覆盖多个子群体。

## 2. 为什么应该先切出测试集

推荐顺序：

```python
X_dev, X_test, y_dev, y_test = train_test_split(
    X,
    y,
    test_size=0.20,
    stratify=y,
    random_state=42,
)

X_train, X_val, y_train, y_val = train_test_split(
    X_dev,
    y_dev,
    test_size=0.25,
    stratify=y_dev,
    random_state=42,
)
```

第二次从 80% 的开发集取 25%，得到总体 20% 验证集。

最终是：

```text
Train = 60%
Validation = 20%
Test = 20%
```

先隔离 Test 的目的，是让后续探索和调参尽量不看到它。

## 3. 分类为什么常用 stratify

如果正样本只占 5%，普通随机切分可能导致：

- 验证集正样本过少；
- 测试集类别比例严重偏离；
- 指标方差很大。

`stratify=y` 尽量保持各集合的标签比例。

但 stratify 不能解决：

- 同一用户跨集合；
- 同一设备跨集合；
- 重复样本跨集合；
- 时间泄漏。

## 4. Group Split

如果同一实体产生多条高度相关样本，必须按实体分组。

例如：

- 同一台打印机的多个时间窗口；
- 同一个用户的多次行为；
- 同一患者的多张图像；
- 同一个 GitHub 仓库的多个 Chunk；
- 同一次打印任务切出的多个传感器片段。

错误切法：

```text
打印任务 A 的窗口 1 → Train
打印任务 A 的窗口 2 → Validation
打印任务 A 的窗口 3 → Test
```

模型可能记住任务 A 的特征，导致测试成绩虚高。

正确做法：

```text
整个打印任务 A 只能属于一个集合
```

常用工具：

- `GroupShuffleSplit`；
- `GroupKFold`；
- `StratifiedGroupKFold`。

## 5. 时间序列切分

预测未来时，不能随机打乱。

正确顺序：

```text
较早数据 → Train
中间数据 → Validation
最新数据 → Test
```

例如：

```text
2024 年 1—8 月：Train
2024 年 9—10 月：Validation
2024 年 11—12 月：Test
```

交叉验证可使用滚动窗口：

```text
Fold 1：
Train [1,2,3] → Validate [4]

Fold 2：
Train [1,2,3,4] → Validate [5]

Fold 3：
Train [1,2,3,4,5] → Validate [6]
```

不能让未来数据参与过去的特征统计或训练。

## 6. 分布外测试集

如果产品会面对新设备、新地区或新用户，仅做随机测试可能不够。

可以额外建立：

- 新打印机型号测试；
- 新材料测试；
- 新用户测试；
- 不同采集环境测试；
- 不同时间段测试。

随机测试回答“同分布泛化”，分布外测试回答“环境变化后是否还能工作”。

---

# 四、最重要的原则：先切分，再拟合预处理

## 1. 什么是数据泄漏

数据泄漏是：

> 训练过程使用了真实预测时不应该知道的信息，导致离线指标虚高。

泄漏不一定是把标签列直接放进 X。更常见的是预处理顺序错误。

## 2. 标准化泄漏

错误：

```python
scaler = StandardScaler()
X_scaled = scaler.fit_transform(X)

X_train, X_test = train_test_split(X_scaled)
```

`scaler.fit_transform(X)` 已经使用了测试集的均值和方差。

正确：

```python
X_train, X_test = train_test_split(X)

scaler = StandardScaler()
X_train_scaled = scaler.fit_transform(X_train)
X_test_scaled = scaler.transform(X_test)
```

测试集只能 transform，不能 fit。

## 3. 其他常见预处理泄漏

必须只在训练数据上拟合：

- 缺失值中位数；
- StandardScaler；
- MinMaxScaler；
- OneHotEncoder 的类别表；
- PCA；
- 特征选择；
- 文本词表；
- TF-IDF；
- 目标编码；
- 异常值阈值；
- SMOTE；
- 降维；
- 聚类特征；
- 数据驱动的特征筛选。

## 4. 为什么应该使用 Pipeline

`sklearn.pipeline.Pipeline` 能把预处理和模型绑定：

```python
pipeline = Pipeline([
    ("scaler", StandardScaler()),
    ("model", LogisticRegression()),
])
```

调用：

```python
pipeline.fit(X_train, y_train)
pipeline.predict(X_val)
```

Pipeline 会保证：

- scaler 只在 fit 输入上学习；
- 验证和测试只调用 transform；
- 交叉验证的每一折都重新拟合预处理；
- 部署时不会忘记相同预处理；
- 保存模型时可以整体保存。

## 5. ColumnTransformer

结构化数据经常同时包含数值和类别字段：

```python
numeric_pipeline = Pipeline([
    ("imputer", SimpleImputer(strategy="median")),
    ("scaler", StandardScaler()),
])

category_pipeline = Pipeline([
    ("imputer", SimpleImputer(strategy="most_frequent")),
    ("one_hot", OneHotEncoder(handle_unknown="ignore")),
])

preprocessor = ColumnTransformer([
    ("num", numeric_pipeline, numeric_columns),
    ("cat", category_pipeline, category_columns),
])

model = Pipeline([
    ("features", preprocessor),
    ("classifier", LogisticRegression()),
])
```

这比在 DataFrame 上手工修改列更安全、更容易复现。

---

# 五、建立基线再训练复杂模型

## 1. 为什么需要 baseline

没有基线，就不知道复杂模型是否真的有价值。

分类基线可以是：

- 永远预测多数类；
- 按类别先验随机预测；
- 简单逻辑回归；
- 一条业务规则。

回归基线可以是：

- 永远预测训练集均值；
- 永远预测训练集中位数；
- 使用上一个时间点；
- 简单线性回归。

## 2. 一个准确率陷阱

如果 1000 个样本中只有 20 个故障：

```text
永远预测“正常”
Accuracy = 98%
故障 Recall = 0%
```

98% Accuracy 看起来很好，但模型没有业务价值。

所以必须让复杂模型和业务基线在相同验证集上比较。

## 3. 训练误差和验证误差

典型判断：

| 现象 | 可能原因 |
|---|---|
| 训练差、验证也差 | 欠拟合、特征不足、标签噪声 |
| 训练好、验证差 | 过拟合、泄漏、分布差异 |
| 训练和验证都好 | 可能有效，也要排查泄漏 |
| 验证好、测试突然差 | 对验证集过拟合、切分不同、数据漂移 |

---

# 六、验证集真正负责哪些决策

## 1. 选择算法

例如比较：

- Logistic Regression；
- Random Forest；
- XGBoost；
- HistGradientBoosting；
- 神经网络。

只能根据训练和验证结果选择，不能看测试集后再选。

## 2. 选择超参数

例如：

```text
Logistic Regression：
C = 0.01 / 0.1 / 1 / 10

Random Forest：
max_depth = 5 / 10 / None
min_samples_leaf = 1 / 5 / 20
```

每组超参数：

```text
在 Train 上 fit
→ 在 Validation 上计算指标
→ 保存结果
→ 选择最符合主要指标和约束的方案
```

## 3. 选择特征

如果你根据验证结果决定：

- 删除某列；
- 增加交互特征；
- 改时间窗口；
- 改图像尺寸；
- 改文本清洗；

这同样属于模型选择。

## 4. 选择分类阈值

二分类模型通常输出概率：

```text
P(y=1 | x) = 0.63
```

默认阈值 0.5 不一定符合业务。

如果漏报代价高，可以降低阈值提高 Recall；如果误报代价高，可以提高阈值提高 Precision。

阈值必须在验证集上选择：

```python
thresholds = np.linspace(0.20, 0.80, 61)

best_threshold = max(
    thresholds,
    key=lambda threshold: f1_score(
        y_val,
        val_probability >= threshold,
    ),
)
```

不能在测试集上找最优阈值。

---

# 七、固定验证集与交叉验证

## 1. 固定验证集

优点：

- 简单；
- 速度快；
- 容易解释；
- 适合数据量大；
- 适合深度学习每个 epoch 验证。

缺点：

- 结果依赖一次切分；
- 数据少时方差大；
- 某次切分可能恰好容易或困难。

## 2. K-Fold Cross Validation

把开发数据分成 K 份：

```text
Fold 1：1 份验证，其他训练
Fold 2：换 1 份验证，其他训练
...
Fold K
```

最终计算：

```text
mean score
standard deviation
```

分类通常使用 `StratifiedKFold`，保持各折标签比例。

## 3. 交叉验证是否还需要测试集

需要。

```text
Train/Validation 或 CV：
选择方案

Test：
评估已经选定的最终方案
```

交叉验证提高模型选择稳定性，不能替代独立测试集。

## 4. 数据少时的推荐做法

```text
先保留独立 Test
剩余 Train + Validation
→ 用 K-Fold CV 调参
→ 固定方案
→ 用全部 Train + Validation 重训
→ Test 评估一次
```

## 5. Nested Cross Validation

如果数据非常少，又需要更可靠估计：

- 外层 Fold：模拟测试；
- 内层 Fold：调超参数；
- 每个外层验证集都不参与内部调参。

Nested CV 能减少调参造成的乐观偏差，但计算成本高。

普通课程和大多数实习项目可以：

- 独立 Test；
- 开发集 K-Fold；
- 清楚说明限制。

---

# 八、一次完整 Python 训练—验证—测试过程

以“预测设备是否发生故障”的二分类任务为例。

## 第 0 步：固定实验配置

```python
RANDOM_STATE = 42
TEST_SIZE = 0.20
VALIDATION_SIZE_IN_DEV = 0.25
PRIMARY_METRIC = "f1"
```

同时记录：

- 数据版本；
- 特征版本；
- 代码提交；
- Python 版本；
- 依赖版本；
- 随机种子；
- 实验时间；
- 机器环境。

## 第 1 步：加载并验证数据

检查：

- 行数和列数；
- 标签是否合法；
- 主键是否重复；
- 缺失率；
- 类别比例；
- 时间范围；
- 组 ID；
- 是否存在未来字段；
- 是否包含推理时不可获得的列。

不要一开始就删除所有“看起来没用”的数据。先建立数据报告。

## 第 2 步：隔离测试集

```python
X_dev, X_test, y_dev, y_test = train_test_split(
    X,
    y,
    test_size=0.20,
    stratify=y,
    random_state=42,
)
```

从这一步开始，把 Test 当作封存数据。

## 第 3 步：建立训练集和验证集

```python
X_train, X_val, y_train, y_val = train_test_split(
    X_dev,
    y_dev,
    test_size=0.25,
    stratify=y_dev,
    random_state=42,
)
```

验证三个集合：

- 索引互不相交；
- 标签比例合理；
- Group 没有交叉；
- 时间顺序正确；
- 无重复样本跨集合。

## 第 4 步：只在训练集做数据探索

可以查看：

- 训练集分布；
- 训练集缺失；
- 训练集相关性；
- 训练集异常值；
- 训练集类别比例。

验证集用于评估，不应该因为看了验证集某些具体样本后手工设计规则。

现实中开发者不可避免会多次看验证指标，所以要保留独立 Test 作为最终防线。

## 第 5 步：建立 Pipeline

```python
pipeline = Pipeline([
    ("scale", StandardScaler()),
    (
        "model",
        LogisticRegression(
            max_iter=2000,
            random_state=42,
        ),
    ),
])
```

任何数据驱动预处理都放入 Pipeline。

## 第 6 步：计算 baseline

```python
baseline = DummyClassifier(strategy="most_frequent")
baseline.fit(X_train, y_train)

baseline_prediction = baseline.predict(X_val)
baseline_f1 = f1_score(y_val, baseline_prediction)
```

后续模型必须在相同验证集上超过这个基线，并满足业务约束。

## 第 7 步：训练候选方案

```python
candidate_configs = [
    {"model__C": 0.01, "model__class_weight": None},
    {"model__C": 0.10, "model__class_weight": None},
    {"model__C": 1.00, "model__class_weight": None},
    {"model__C": 1.00, "model__class_weight": "balanced"},
]
```

对每个配置：

1. 克隆 Pipeline；
2. 只在 Train 上 fit；
3. 在 Validation 上预测；
4. 记录 F1、Precision、Recall、ROC-AUC；
5. 记录训练时间和推理时间；
6. 选择满足业务约束的最简单方案。

## 第 8 步：选择阈值

使用最佳候选模型在 Validation 上的概率：

```python
val_probability = best_model.predict_proba(X_val)[:, 1]
```

遍历阈值，选择满足：

```text
Recall ≥ 业务下限
并且
F1 最大或业务成本最小
```

保存 bestThreshold。

## 第 9 步：做错误分析

不要只看一个分数。

检查：

- False Positive；
- False Negative；
- 哪些设备或用户群体失败更多；
- 哪个时间段性能差；
- 是否因为缺失值；
- 是否有标签错误；
- 是否有分布偏移；
- 预测概率是否校准。

只能根据 Validation 错误分析修改方案。修改后重新训练和验证。

## 第 10 步：冻结实验方案

冻结：

- 特征列表；
- 预处理；
- 模型类型；
- 超参数；
- 分类阈值；
- 依赖版本；
- 随机种子；
- 评价指标；
- 数据切分；
- 后处理规则。

从这里开始，不再根据 Test 调整。

## 第 11 步：在 Train + Validation 上重训

```python
X_train_final = np.concatenate([X_train, X_val])
y_train_final = np.concatenate([y_train, y_val])

final_model = clone(best_model)
final_model.fit(X_train_final, y_train_final)
```

为什么重训：

- 验证阶段已经完成；
- 最终模型可以利用更多已标注数据；
- 超参数和阈值已经固定。

对于阈值特别敏感的任务，更严格的做法是使用 Train+Validation 的 out-of-fold 预测选择阈值，再在全部开发数据上重训。

## 第 12 步：测试集只评估一次

```python
test_probability = final_model.predict_proba(X_test)[:, 1]
test_prediction = test_probability >= best_threshold

test_metrics = {
    "precision": precision_score(y_test, test_prediction),
    "recall": recall_score(y_test, test_prediction),
    "f1": f1_score(y_test, test_prediction),
    "roc_auc": roc_auc_score(y_test, test_probability),
}
```

这组结果才是最终泛化报告。

如果 Test 很差，不能继续反复调参后还把同一 Test 称为独立测试集。

正确做法：

- 承认测试不通过；
- 重新回到开发阶段；
- 后续需要新的独立测试集、外部数据或下一时间窗口；
- 记录第一次 Test 已经参与决策。

## 第 13 步：保存完整 Artifact

不能只保存模型权重。

应该保存：

```python
artifact = {
    "pipeline": final_model,
    "threshold": best_threshold,
    "feature_names": feature_names,
    "label_mapping": label_mapping,
    "data_version": data_version,
    "code_version": code_version,
    "metrics": test_metrics,
}

joblib.dump(artifact, "model.joblib")
```

Pipeline 中应包含预处理，避免训练和推理不一致。

## 第 14 步：加载并做推理一致性测试

```python
loaded = joblib.load("model.joblib")

before = final_model.predict_proba(X_test[:10])[:, 1]
after = loaded["pipeline"].predict_proba(X_test[:10])[:, 1]

np.testing.assert_allclose(before, after)
```

还应检查：

- 输入列顺序；
- dtype；
- 缺失值；
- 未知类别；
- 输出 shape；
- 阈值；
- 模型版本。

## 第 15 步：上线监控

上线后监控：

- 输入特征漂移；
- 预测概率分布；
- 正类比例；
- 缺失率；
- 未知类别；
- 推理延迟；
- 错误率；
- 有延迟标签时的真实 Precision/Recall；
- 不同用户或设备子群体表现。

测试集只代表过去采样到的未知数据，不能保证未来永远有效。

---

# 九、一份可复用的完整 Python 模板

下面是固定验证集版本。重点不是模型有多复杂，而是边界正确、代码模块清晰。

```python
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any

import joblib
import numpy as np
from sklearn.base import clone
from sklearn.datasets import make_classification
from sklearn.dummy import DummyClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    f1_score,
    precision_score,
    recall_score,
    roc_auc_score,
)
from sklearn.model_selection import ParameterGrid, train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler


@dataclass(frozen=True)
class ExperimentConfig:
    random_state: int = 42
    test_size: float = 0.20
    validation_size_in_dev: float = 0.25
    minimum_recall: float = 0.70
    artifact_path: Path = Path("artifacts/fault_model.joblib")


def load_data(config: ExperimentConfig) -> tuple[np.ndarray, np.ndarray]:
    features, labels = make_classification(
        n_samples=3000,
        n_features=20,
        n_informative=10,
        n_redundant=4,
        weights=[0.85, 0.15],
        class_sep=1.2,
        random_state=config.random_state,
    )
    return features, labels


def split_data(
    features: np.ndarray,
    labels: np.ndarray,
    config: ExperimentConfig,
) -> tuple[np.ndarray, ...]:
    x_dev, x_test, y_dev, y_test = train_test_split(
        features,
        labels,
        test_size=config.test_size,
        stratify=labels,
        random_state=config.random_state,
    )
    x_train, x_val, y_train, y_val = train_test_split(
        x_dev,
        y_dev,
        test_size=config.validation_size_in_dev,
        stratify=y_dev,
        random_state=config.random_state,
    )
    return x_train, x_val, x_test, y_train, y_val, y_test


def build_pipeline(config: ExperimentConfig) -> Pipeline:
    return Pipeline([
        ("scale", StandardScaler()),
        (
            "model",
            LogisticRegression(
                max_iter=2000,
                random_state=config.random_state,
            ),
        ),
    ])


def classification_metrics(
    labels: np.ndarray,
    probability: np.ndarray,
    threshold: float,
) -> dict[str, float]:
    prediction = probability >= threshold
    return {
        "precision": precision_score(labels, prediction, zero_division=0),
        "recall": recall_score(labels, prediction, zero_division=0),
        "f1": f1_score(labels, prediction, zero_division=0),
        "roc_auc": roc_auc_score(labels, probability),
    }


def select_model(
    pipeline: Pipeline,
    x_train: np.ndarray,
    y_train: np.ndarray,
    x_val: np.ndarray,
    y_val: np.ndarray,
) -> tuple[Pipeline, dict[str, Any]]:
    search_space = {
        "model__C": [0.01, 0.1, 1.0, 10.0],
        "model__class_weight": [None, "balanced"],
    }

    best_model: Pipeline | None = None
    best_record: dict[str, Any] | None = None

    for parameters in ParameterGrid(search_space):
        candidate = clone(pipeline).set_params(**parameters)
        candidate.fit(x_train, y_train)

        probability = candidate.predict_proba(x_val)[:, 1]
        metrics = classification_metrics(y_val, probability, threshold=0.5)
        record = {
            "parameters": parameters,
            "metrics": metrics,
        }

        if best_record is None or metrics["f1"] > best_record["metrics"]["f1"]:
            best_model = candidate
            best_record = record

    if best_model is None or best_record is None:
        raise RuntimeError("No candidate model was trained")

    return best_model, best_record


def select_threshold(
    model: Pipeline,
    x_val: np.ndarray,
    y_val: np.ndarray,
    minimum_recall: float,
) -> tuple[float, dict[str, float]]:
    probability = model.predict_proba(x_val)[:, 1]
    candidates: list[tuple[float, dict[str, float]]] = []

    for threshold in np.linspace(0.20, 0.80, 61):
        metrics = classification_metrics(y_val, probability, float(threshold))
        if metrics["recall"] >= minimum_recall:
            candidates.append((float(threshold), metrics))

    if not candidates:
        raise RuntimeError("No threshold satisfies the recall requirement")

    return max(candidates, key=lambda item: item[1]["f1"])


def main() -> None:
    config = ExperimentConfig()
    features, labels = load_data(config)

    (
        x_train,
        x_val,
        x_test,
        y_train,
        y_val,
        y_test,
    ) = split_data(features, labels, config)

    baseline = DummyClassifier(strategy="most_frequent")
    baseline.fit(x_train, y_train)
    baseline_f1 = f1_score(y_val, baseline.predict(x_val), zero_division=0)

    pipeline = build_pipeline(config)
    selected_model, selection = select_model(
        pipeline,
        x_train,
        y_train,
        x_val,
        y_val,
    )

    threshold, validation_metrics = select_threshold(
        selected_model,
        x_val,
        y_val,
        config.minimum_recall,
    )

    final_model = clone(selected_model)
    final_model.fit(
        np.concatenate([x_train, x_val]),
        np.concatenate([y_train, y_val]),
    )

    # Test is evaluated only after the complete scheme is frozen.
    test_probability = final_model.predict_proba(x_test)[:, 1]
    test_metrics = classification_metrics(
        y_test,
        test_probability,
        threshold,
    )

    artifact = {
        "pipeline": final_model,
        "threshold": threshold,
        "baseline_f1": baseline_f1,
        "selected_parameters": selection["parameters"],
        "validation_metrics": validation_metrics,
        "test_metrics": test_metrics,
        "random_state": config.random_state,
    }

    config.artifact_path.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(artifact, config.artifact_path)

    loaded = joblib.load(config.artifact_path)
    np.testing.assert_allclose(
        final_model.predict_proba(x_test[:10]),
        loaded["pipeline"].predict_proba(x_test[:10]),
    )

    print({
        "baseline_f1": baseline_f1,
        "validation": validation_metrics,
        "test": test_metrics,
        "threshold": threshold,
        "artifact": str(config.artifact_path),
    })


if __name__ == "__main__":
    main()
```

这段代码展示：

- 配置对象；
- 类型标注；
- 功能拆分；
- 分层切分；
- Pipeline；
- baseline；
- 候选搜索；
- 验证集阈值选择；
- train+validation 重训；
- test 一次评估；
- Artifact 保存；
- 加载一致性测试。

真实项目还应把各函数拆到独立模块，并加入日志、配置文件和测试。

---

# 十、Python 项目怎样写得像工程而不是 Notebook

推荐结构：

```text
ml_project/
├── pyproject.toml
├── README.md
├── configs/
│   └── baseline.yaml
├── src/
│   └── ml_project/
│       ├── config.py
│       ├── data.py
│       ├── features.py
│       ├── models.py
│       ├── metrics.py
│       ├── train.py
│       ├── evaluate.py
│       └── inference.py
├── tests/
│   ├── test_data_split.py
│   ├── test_features.py
│   ├── test_training.py
│   └── test_artifact.py
├── artifacts/
└── reports/
```

## 1. config.py

负责：

- 随机种子；
- 数据路径；
- 切分比例；
- 特征列表；
- 搜索空间；
- 指标；
- Artifact 路径。

## 2. data.py

负责：

- 加载原始数据；
- Schema 校验；
- 去重；
- 标签合法性；
- Group/时间切分；
- 返回明确的数据对象。

## 3. features.py

负责：

- 构造 sklearn Pipeline；
- 数值、类别和文本预处理；
- 不在模块全局 fit；
- 不读取测试标签。

## 4. train.py

负责：

- 训练候选模型；
- 交叉验证；
- 超参数搜索；
- 保存实验记录；
- 不负责线上 HTTP 接口。

## 5. evaluate.py

负责：

- 统一指标；
- 混淆矩阵；
- 子群体评估；
- 阈值分析；
- 最终测试报告。

## 6. inference.py

负责：

- 加载 Artifact；
- 校验输入 Schema；
- transform + predict；
- 应用阈值；
- 返回模型版本。

## 7. tests

至少验证：

- 三个数据集索引不相交；
- Group 不跨集合；
- 时间顺序正确；
- Pipeline 能处理缺失值和未知类别；
- 输出 shape 正确；
- 概率在 0 到 1；
- 保存前后预测一致；
- 固定种子时切分一致；
- Test 不参与候选选择函数。

这部分正好对应岗位要求中的：

> Python 代码规范、逻辑清晰、能够独立完成功能模块开发。

---

# 十一、指标应该怎样选择

## 1. 分类指标

### Accuracy

```text
预测正确数量 / 总数量
```

适合类别相对均衡且错误代价相近的场景。

### Precision

```text
TP / (TP + FP)
```

预测为正的样本中，有多少是真的。

适合误报代价高的场景。

### Recall

```text
TP / (TP + FN)
```

所有真实正样本中，有多少被找出来。

适合漏报代价高的场景。

### F1

```text
2 × Precision × Recall / (Precision + Recall)
```

用于平衡 Precision 和 Recall，但它不包含 True Negative。

### ROC-AUC

衡量不同阈值下正负样本排序能力。

类别极不平衡时，ROC-AUC 可能看起来较高，应同时查看 PR-AUC。

### PR-AUC

关注 Precision-Recall，适合正样本稀少的故障、欺诈和异常任务。

### Log Loss

评价概率是否把正确类别赋予高概率，对过度自信错误惩罚较大。

## 2. 回归指标

### MAE

```text
平均绝对误差
```

直观、对极端值比 RMSE 更稳健。

### RMSE

```text
均方误差开根号
```

大误差惩罚更强。

### R²

表示模型相对均值基线解释的方差比例。负数表示可能比均值基线还差。

### MAPE

对接近 0 的真实值不稳定，不能盲目使用。

## 3. 不能只报告平均分

还应报告：

- 标准差；
- 置信区间；
- 子群体指标；
- 混淆矩阵；
- 错误样本；
- 推理延迟；
- 模型大小；
- 不同阈值的业务代价。

---

# 十二、类别不平衡如何处理

可以使用：

- `class_weight="balanced"`；
- 欠采样；
- 过采样；
- SMOTE；
- Focal Loss；
- 阈值调整；
- 收集更多正样本；
- 使用 PR-AUC/F1/Recall。

最重要的边界：

> 重采样只能在训练 Fold 内进行。

错误：

```text
先对全量数据 SMOTE
→ 再切 Train/Test
```

合成样本可能把测试信息带进训练。

正确做法是使用 imbalanced-learn Pipeline，让每个训练 Fold 单独重采样。

---

# 十三、深度学习中的 Train、Validation、Test

PyTorch 训练循环：

```python
for epoch in range(max_epochs):
    model.train()

    for inputs, labels in train_loader:
        optimizer.zero_grad()
        logits = model(inputs)
        loss = criterion(logits, labels)
        loss.backward()
        optimizer.step()

    model.eval()

    with torch.no_grad():
        validation_metrics = evaluate(model, validation_loader)

    if validation_metrics["f1"] > best_f1:
        best_f1 = validation_metrics["f1"]
        torch.save(model.state_dict(), "best.pt")
        patience_counter = 0
    else:
        patience_counter += 1

    if patience_counter >= patience:
        break
```

关键区别：

- `model.train()`：启用训练模式，Dropout 和 BatchNorm 按训练行为运行；
- `model.eval()`：切换评估模式；
- `torch.no_grad()`：不构建梯度图，减少内存；
- Validation：选 epoch、阈值和超参数；
- Test：加载 best checkpoint 后最终评估一次。

不能用最后一个 epoch 自动作为最佳模型。应该保存 Validation 最优 checkpoint。

---

# 十四、失败场景如何处理

| 问题 | 表现 | 正确处理 |
|---|---|---|
| 先标准化再切分 | Test 指标虚高 | 先切分，用 Pipeline 在 Train 上 fit |
| 同一实体跨集合 | Validation/Test 异常好 | Group Split |
| 时间序列随机切分 | 使用未来信息预测过去 | 按时间切分或滚动验证 |
| 用 Test 调超参数 | Test 越调越高 | Test 封存，必要时建立新 Test |
| 类别极不平衡只看 Accuracy | 少数类完全识别失败 | 看 Recall、F1、PR-AUC |
| 只报告最佳 Fold | 结果过度乐观 | 报告均值和标准差 |
| 在全数据上做 SMOTE | 合成样本泄漏 | 只在训练 Fold 内重采样 |
| 随机种子未固定 | 每次结果差异大 | 固定切分和模型随机种子 |
| 只保存模型权重 | 推理结果不一致 | 保存完整 Pipeline、阈值和 Schema |
| 验证很好、测试很差 | 对 Validation 过拟合或分布变化 | 检查切分，承认失败，获取新数据 |
| 训练好、验证差 | 过拟合 | 正则化、简化模型、增加数据 |
| 训练和验证都差 | 欠拟合或标签问题 | 改特征、模型、标签和数据质量 |
| 上线效果下降 | 数据或概念漂移 | 监控、重新标注、触发重训 |
| 依赖版本变化 | Artifact 无法加载 | 锁定依赖并记录运行环境 |

这体现三个原则：

1. **测试集必须独立**：它不是第二个验证集。
2. **所有数据驱动步骤都可能泄漏**：不仅是模型 fit。
3. **离线高分不等于线上稳定**：还需要数据契约、可复现和监控。

---

# 十五、这个流程的优点

面试可以重点讲：

- 先定义业务目标和指标；
- 测试集提前隔离；
- 按标签、Group 或时间选择切分方式；
- Pipeline 防止预处理泄漏；
- baseline 证明复杂模型是否有价值；
- Validation 负责超参数、特征和阈值；
- Cross Validation 降低单次切分偶然性；
- Train+Validation 重训利用更多数据；
- Test 对最终方案做独立评估；
- Artifact 保存完整预处理与模型；
- 数据版本、代码版本和依赖可追踪；
- 单元测试验证数据边界；
- 上线监控闭环；
- Python 模块职责清晰。

---

# 十六、需要主动承认的不足和现实约束

1. **一次随机切分不一定代表真实场景**

   如果存在设备、用户、时间或地域差异，必须使用 Group、Time 或 OOD Test。

2. **Validation 也会被过拟合**

   调参次数越多，开发者越可能适应验证集噪声。需要交叉验证、Nested CV 或新的验证数据。

3. **Test 指标仍有统计误差**

   测试集样本有限，应该报告置信区间和子群体结果。

4. **固定随机种子不等于完全可复现**

   GPU 算子、线程、底层库和硬件仍可能产生差异。

5. **离线标签可能不等于业务价值**

   标签延迟、标注错误和代理指标都会让高分模型在线失效。

6. **Train+Validation 重训后模型发生变化**

   最终模型参数不再等于验证阶段模型。对阈值和概率校准敏感时，应使用 out-of-fold 预测或额外校准集。

7. **超参数搜索会消耗大量计算**

   Grid Search 简单但组合爆炸；Random Search 或 Optuna 更高效，但仍不能使用 Test 反馈。

8. **模型保存不保证长期可加载**

   pickle/joblib 依赖 Python 和库版本，需要锁定依赖并考虑安全来源。

9. **监控输入漂移不能直接证明效果下降**

   真正的概念漂移需要后续真实标签验证。

10. **本阶段是独立学习能力**

    可以在简历中写独立完成训练验证测试实验，前提是你实际运行代码、保存报告并解释结果；不能把它写成 OrangeVC 已有模型训练平台。

---

# 十七、高频面试问题与回答要点

## 1. 为什么要分 Train、Validation、Test

> Train 用于学习模型参数，Validation 用于选择特征、算法、超参数和阈值，Test 在方案冻结后估计未知数据泛化能力。如果根据 Test 反复调整，Test 就失去独立性。

## 2. 为什么不能只分 Train 和 Test

> 如果使用 Test 选择模型或调参，最终 Test 指标会对开发决策产生偏置。Validation 提供开发反馈，Test 保留为最终独立检查。

## 3. 为什么 Test 只能最终使用一次

> 每看一次 Test 并据此修改，都会向测试分布过拟合。严格说不是物理上只能运行一行代码，而是 Test 结果不能参与任何方案决策。

## 4. 什么是数据泄漏

> 训练或模型选择使用了真实预测时不可获得的信息。包括标签泄漏、未来信息、同实体跨集合、全量数据拟合标准化、全量 SMOTE 和使用 Test 调参。

## 5. 为什么先切分再标准化

> StandardScaler 的均值和方差也是从数据学习的参数。如果在全数据上 fit，就使用了 Validation/Test 的分布信息。Pipeline 可以保证每个训练 Fold 独立拟合。

## 6. Stratified Split 和 Group Split 有什么区别

> Stratified Split 保持标签比例；Group Split 保证同一实体不跨集合。存在多条同源样本时，Group 隔离通常比单纯标签比例更重要，可以使用 StratifiedGroupKFold 同时兼顾。

## 7. 时间序列为什么不能随机切分

> 随机切分会让未来样本进入训练，并可能让未来统计量预测过去。生产目标是用过去预测未来，所以验证和测试必须位于训练时间之后。

## 8. Cross Validation 能代替测试集吗

> 不能。CV 用于稳定模型选择，开发者仍会根据 CV 结果调整。独立 Test 用于评估冻结方案。数据很少时可以使用 Nested CV，但外层 Fold 才承担测试估计角色。

## 9. Top 模型为什么还要和 baseline 比

> 如果复杂模型没有显著超过简单规则或多数类预测，就没有证明额外复杂度、部署成本和维护成本值得。

## 10. 类别不平衡为什么不能只看 Accuracy

> 多数类预测可能得到很高 Accuracy，但少数类 Recall 为 0。应根据业务关注 Precision、Recall、F1、PR-AUC、混淆矩阵和阈值成本。

## 11. 0.5 为什么不一定是最佳阈值

> 0.5 只是概率到类别的默认决策点，不代表业务成本最优。漏报和误报代价不同，应在 Validation 上选择满足业务约束的阈值。

## 12. 为什么验证后还要 Train+Validation 重训

> 超参数和规则已经冻结，验证集不再承担选择任务，可以作为额外训练数据提高最终模型的数据利用率。最终泛化仍由未参与任何决策的 Test 评估。

## 13. Test 很差怎么办

> 不能继续针对同一 Test 调到好看。应先检查数据、切分和分布差异，回到开发集重新实验，并承认旧 Test 已参与决策；之后使用新的独立数据或未来时间窗口做最终评估。

## 14. 如何保证实验可复现

> 固定数据快照、切分索引、随机种子、代码提交、依赖锁文件、配置、模型 Artifact 和指标报告。随机种子只是其中一项，不代表跨硬件完全确定。

## 15. Optuna 应该优化哪个数据集

> Objective 应来自训练开发数据中的 Validation 或 Cross Validation，不能直接优化 Test。完成 Study 后冻结 best params，在 Train+Validation 重训，再评估 Test。

## 16. Early Stopping 看哪个指标

> 每个 epoch 在 Validation 上计算业务指标或 loss，用它选择 best checkpoint 和停止时刻。Test 不能参与 Early Stopping。

## 17. 如何检查模型是否过拟合

> 比较 Train 与 Validation 指标和学习曲线。Train 很好但 Validation 明显差通常是过拟合；还要排查 Validation 分布变化和数据泄漏，不能只归因于模型复杂。

## 18. 模型上线为什么必须保存 Pipeline

> 预测依赖与训练相同的缺失值处理、编码、标准化和特征顺序。只保存 estimator 容易产生 train-serving skew，Pipeline 可以把预处理和模型作为一个 Artifact 交付。

## 19. 单元测试能测试模型准确率吗

> 单元测试适合验证数据边界、Shape、Schema、无 NaN、保存加载一致性和确定性。模型质量应使用固定评估集和统计指标，不应把一个波动的准确率阈值当作普通单元测试。

## 20. 离线效果很好，上线效果差的原因

可能包括：

- 训练和线上预处理不一致；
- 数据漂移；
- 概念漂移；
- 标签定义偏离业务；
- 离线数据泄漏；
- 线上缺失值或未知类别；
- 阈值不符合真实成本；
- 反馈回路改变数据分布。

---

# 十八、面试中的完整回答模板

我会先把业务问题转换成明确的机器学习目标，定义一条样本、标签产生时间、可用特征和主要指标。例如故障检测中，如果漏报代价高，我会把 Recall 作为硬约束，同时关注 Precision、F1、PR-AUC 和推理延迟，而不是只看 Accuracy。

数据进入实验后，我会先按真实部署场景选择切分策略。普通独立样本可以分层随机切分；同一设备、用户或打印任务产生多条样本时使用 Group Split；预测未来时按时间切分。测试集最先隔离，后续所有特征设计、预处理、模型选择、超参数和阈值都只使用训练集与验证集。

预处理也是训练的一部分。缺失值填充、标准化、One-Hot、PCA、特征选择和重采样都必须只在训练数据上 fit。我通常使用 sklearn Pipeline 和 ColumnTransformer，把预处理与模型绑定，这样固定验证、Cross Validation 和线上推理都会使用同一套变换，也能避免 train-serving skew。

训练时先建立 DummyClassifier、均值预测或简单线性模型作为 baseline。候选模型只在 Train 上 fit，在 Validation 或 K-Fold Cross Validation 上比较。我会同时记录主要指标、标准差、训练时间、推理时间和子群体表现。二分类阈值也在 Validation 上根据误报和漏报成本选择，不能直接使用 Test 找最优阈值。

方案冻结后，我会使用固定的特征、超参数和阈值，在 Train+Validation 上重训最终 Pipeline，然后只对独立 Test 做一次正式评估。测试报告会包含 Precision、Recall、F1、ROC-AUC或PR-AUC、混淆矩阵、置信区间和错误分析。如果根据 Test 结果继续修改，原 Test 就已经变成开发反馈，之后必须使用新的独立数据做最终评估。

工程上，我会保存完整 Pipeline、阈值、特征 Schema、标签映射、数据版本、代码提交、依赖版本、随机种子和测试指标，并验证模型保存前后的预测一致。代码会拆成 data、features、train、evaluate 和 inference 模块，使用类型标注、配置对象、日志与单元测试，保证数据集合不交叉、Group 不泄漏、时间顺序正确、输出 Shape 合法。

上线后还需要监控特征分布、缺失率、未知类别、概率分布、延迟和有真实标签后的线上指标。测试集只说明模型在某个历史未知样本上的表现，数据或概念漂移后仍可能需要重新标注和训练。

当前 OrangeVC 没有机器学习训练模块，因此我会把这部分作为独立 Python 实践来展示。只有在我实际运行训练、保留配置与报告、能解释每一次切分和指标后，才会在简历中写“独立完成训练—验证—测试流程”，而不会把它包装成 OrangeVC 已实现的能力。

真正掌握的标准是：你能不看资料画出从问题定义、数据切分、Pipeline、baseline、模型选择、阈值选择、Train+Validation 重训到 Test 和部署监控的完整链路；能现场指出至少五种数据泄漏；能针对随机样本、分组数据和时间序列选择不同切分方式；能解释测试集为什么不能参与调参；还能把 Notebook 代码拆成可测试、可复现、可部署的 Python 模块。做到这些，Python 与机器学习实验流程相关的实习面试就有足够深度。

