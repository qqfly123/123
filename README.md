# Spark Data Analysis

基于 Apache Spark 的数据分析项目，包含词频统计、CSV 数据分析，以及**终身学习者能力演进与路径推荐系统**（三阶段架构）。

## 项目结构

```
├── pom.xml                                           # Maven 构建配置
├── data/
│   ├── sales_sample.csv                              # 示例销售数据
│   ├── learners.csv                                  # 学习者信息
│   ├── skills.csv                                    # 技能库
│   ├── courses.csv                                   # 课程库（含先修条件）
│   └── learning_records.csv                          # 学习记录
└── src/
    ├── main/java/com/spark/app/
    │   ├── WordCount.java                            # 词频统计应用
    │   ├── SparkDataAnalysis.java                    # CSV 数据分析应用
    │   └── learner/
    │       ├── SchemaDefinitions.java                # 数据实体 Schema 定义
    │       ├── CapabilityProfiler.java               # [阶段一] 能力画像构建
    │       ├── CapabilityEvolution.java              # [阶段一] 能力演进追踪
    │       ├── PathRecommender.java                  # [阶段一] 学习路径推荐
    │       ├── LearnerSimilarity.java                # [阶段二] 学习者相似度分析
    │       ├── CollaborativeRecommender.java         # [阶段二] 协同过滤推荐
    │       ├── LearningEfficiency.java               # [阶段三] 学习效率分析
    │       ├── SkillDecayPredictor.java              # [阶段三] 技能衰退预测
    │       └── LearnerAnalysisApp.java               # 系统主入口
    └── test/java/com/spark/app/
        ├── WordCountTest.java                        # 词频统计测试
        ├── SparkDataAnalysisTest.java                # 数据分析测试
        └── learner/
            ├── CapabilityProfilerTest.java           # 能力画像测试
            ├── CapabilityEvolutionTest.java          # 能力演进测试
            ├── PathRecommenderTest.java              # 路径推荐测试
            ├── LearnerSimilarityTest.java            # 相似度分析测试
            ├── CollaborativeRecommenderTest.java     # 协同推荐测试
            ├── LearningEfficiencyTest.java           # 学习效率测试
            └── SkillDecayPredictorTest.java          # 衰退预测测试
```

## 环境要求

- Java 11+
- Maven 3.6+
- Apache Spark 3.5.x（运行时需要）

## 构建

```bash
mvn clean package
```

## 运行测试

```bash
mvn test
```

---

## 终身学习者能力演进与路径推荐系统

### 系统概述

为终身学习者提供全方位的能力分析与个性化学习路径推荐，采用**三阶段递进架构**：

### 阶段一：能力画像与路径推荐

基础分析层，包含三大核心模块：

1. **能力画像（CapabilityProfiler）**：基于学习记录构建学习者的技能画像
2. **能力演进（CapabilityEvolution）**：追踪技能随时间的变化趋势
3. **路径推荐（PathRecommender）**：基于技能缺口和先修条件推荐学习路径

#### 能力画像

- **技能级别画像**：每项技能的最新得分、最高得分、平均得分、学习次数、累计学时
- **类别能力分布**：按技能类别（编程语言、大数据、人工智能等）汇总能力水平
- **综合能力评级**：基于技能难度加权的综合评分（优秀 ≥85 / 良好 ≥70 / 进步中 ≥60 / 入门）

#### 能力演进

- **技能演进时间线**：每项技能每次学习的得分变化和累计学时
- **进步趋势分析**：比较首次和最近一次得分，判断提升/下降/稳定趋势
- **月度学习活跃度**：按月统计学习课程数、总学时和平均得分

#### 路径推荐

- **技能缺口识别**：对比技能库找出尚未学习的技能
- **课程推荐**：检查先修条件是否满足，标记可立即学习的课程
- **个性化学习路径**：生成满足先修条件的推荐课程序列，按难度递进排序

### 阶段二：学习者相似度与协同过滤推荐

社交化推荐层，利用群体智慧增强推荐效果：

4. **学习者相似度（LearnerSimilarity）**：基于技能得分向量计算余弦相似度
5. **协同过滤推荐（CollaborativeRecommender）**：基于相似学习者的学习历史推荐课程

#### 学习者相似度

- **技能得分矩阵**：构建 learner × skill 得分矩阵
- **余弦相似度**：基于共同技能计算学习者之间的相似程度
- **相似学习者发现**：为每位学习者找到最相似的同伴

#### 协同过滤推荐

- **协同课程推荐**：推荐相似学习者已学但自己未学的课程，按推荐人数和加权得分排序
- **热门技能发现**：基于全体学习者数据发现最受欢迎的技能

### 阶段三：学习效率分析与技能衰退预测

智能分析层，提供深度洞察和预测能力：

6. **学习效率（LearningEfficiency）**：评估学习投入产出比
7. **技能衰退预测（SkillDecayPredictor）**：基于艾宾浩斯遗忘曲线预测技能衰退

#### 学习效率分析

- **技能学习效率**：得分/学时比率、单次最佳效率
- **难度效率对比**：不同难度等级技能的学习效率差异分析
- **效率排行榜**：学习者综合效率排名

#### 技能衰退预测

- **衰退预测模型**：基于指数衰减公式 `predicted = score × e^(-λ × days)` 预测当前掌握水平
- **复习提醒**：识别预测分数低于阈值的技能，及时提醒复习
- **复习优先级**：综合衰退量和技能难度，生成按优先级排序的复习清单

### 数据模型

| 数据文件 | 说明 |
|---|---|
| `learners.csv` | 学习者基本信息（ID、姓名、注册日期） |
| `skills.csv` | 技能库（ID、名称、类别、难度等级 1-4） |
| `courses.csv` | 课程库（ID、名称、对应技能、难度、先修条件） |
| `learning_records.csv` | 学习记录（学习者、技能、得分、日期、学时） |

### 运行系统

```bash
# 为所有学习者生成完整的三阶段分析报告
spark-submit \
  --class com.spark.app.learner.LearnerAnalysisApp \
  target/spark-data-analysis-1.0.0.jar \
  data/

# 为指定学习者生成分析报告（含协同推荐和衰退预测）
spark-submit \
  --class com.spark.app.learner.LearnerAnalysisApp \
  target/spark-data-analysis-1.0.0.jar \
  data/ L001
```

---

## 基础示例应用

### 词频统计（WordCount）

读取文本文件，将内容按空格拆分为单词，统计每个单词的出现次数，并按出现次数降序输出。

```bash
spark-submit \
  --class com.spark.app.WordCount \
  target/spark-data-analysis-1.0.0.jar \
  <输入文本文件路径>
```

### CSV 数据分析（SparkDataAnalysis）

读取 CSV 格式的销售数据，执行以下分析：

- **按类别统计**：汇总每个产品类别的总销售额、总数量和平均价格
- **按日期统计**：汇总每天的销售额和订单数量
- **热门产品排行**：列出销售额最高的前 N 个产品
- **基础统计信息**：展示数量和价格的描述性统计

```bash
spark-submit \
  --class com.spark.app.SparkDataAnalysis \
  target/spark-data-analysis-1.0.0.jar \
  data/sales_sample.csv
```