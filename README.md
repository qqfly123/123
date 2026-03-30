# Spark Data Analysis

基于 Apache Spark 的数据分析项目，包含词频统计、CSV 数据分析，以及**终身学习者能力演进与路径推荐系统**（含 Web 后端 + 前端可视化）。

## 系统架构

```
┌──────────────────────────────────────────────────────────────┐
│                      前端（浏览器）                           │
│  index.html + ECharts 可视化 + JavaScript                    │
│  ├── 仪表盘（统计概览、评级、热门技能、效率排行）             │
│  ├── 能力画像（雷达图、类别分布饼图、技能详情表）             │
│  ├── 能力演进（趋势折线图、月度活跃度图）                     │
│  ├── 路径推荐（缺口分析、推荐课程、学习路径图）               │
│  ├── 相似度（热力图矩阵、协同推荐表）                        │
│  ├── 学习效率（效率柱状图、难度对比、排行榜）                 │
│  └── 技能衰退（衰退预测图、复习提醒、优先级表）               │
└────────────────────────┬─────────────────────────────────────┘
                         │ HTTP REST API (JSON)
┌────────────────────────┴─────────────────────────────────────┐
│                   后端（Spring Boot）                         │
│  AnalysisController  →  AnalysisService  →  Spark 分析模块    │
│  GET /api/overview    GET /api/profile/*   GET /api/decay/*   │
└────────────────────────┬─────────────────────────────────────┘
                         │ Spark SQL
┌────────────────────────┴─────────────────────────────────────┐
│                   数据层 (CSV Files)                          │
│  learners.csv  skills.csv  courses.csv  learning_records.csv  │
└──────────────────────────────────────────────────────────────┘
```

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
    ├── main/
    │   ├── java/com/spark/app/
    │   │   ├── WordCount.java                        # 词频统计应用
    │   │   ├── SparkDataAnalysis.java                # CSV 数据分析应用
    │   │   ├── learner/
    │   │   │   ├── SchemaDefinitions.java            # 数据实体 Schema 定义
    │   │   │   ├── CapabilityProfiler.java           # [阶段一] 能力画像构建
    │   │   │   ├── CapabilityEvolution.java          # [阶段一] 能力演进追踪
    │   │   │   ├── PathRecommender.java              # [阶段一] 学习路径推荐
    │   │   │   ├── LearnerSimilarity.java            # [阶段二] 学习者相似度
    │   │   │   ├── CollaborativeRecommender.java     # [阶段二] 协同过滤推荐
    │   │   │   ├── LearningEfficiency.java           # [阶段三] 学习效率分析
    │   │   │   ├── SkillDecayPredictor.java          # [阶段三] 技能衰退预测
    │   │   │   └── LearnerAnalysisApp.java           # Spark 批处理入口
    │   │   └── web/
    │   │       ├── WebApplication.java               # Spring Boot 入口
    │   │       ├── config/
    │   │       │   ├── SparkConfig.java              # Spark 会话配置
    │   │       │   └── WebConfig.java                # CORS 配置
    │   │       ├── controller/
    │   │       │   └── AnalysisController.java       # REST API 控制器
    │   │       └── service/
    │   │           └── AnalysisService.java          # 分析服务层
    │   └── resources/
    │       ├── application.properties                # Spring Boot 配置
    │       └── static/
    │           ├── index.html                        # 前端主页面
    │           ├── css/style.css                     # 样式表
    │           └── js/
    │               ├── api.js                        # API 调用层
    │               ├── charts.js                     # ECharts 图表工具
    │               └── app.js                        # 前端主逻辑
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
- Apache Spark 3.5.x（自动通过 Maven 管理）

## 快速开始

### 1. 构建项目

```bash
mvn clean package -DskipTests
```

### 2. 启动 Web 系统（推荐）

```bash
# 启动 Spring Boot 服务（内嵌 Spark + Web 服务器）
mvn spring-boot:run
```

启动后访问 http://localhost:8080 即可看到可视化界面。

### 3. 运行测试

```bash
mvn test
```

---

## Web 可视化系统

### 功能页面

| 页面 | 功能 | 可视化类型 |
|------|------|-----------|
| 📊 仪表盘 | 系统概览、评级统计、热门技能 | 柱状图、水平条形图 |
| 🎯 能力画像 | 技能雷达图、类别分布、详情表 | 雷达图、饼图 |
| 📈 能力演进 | 进步趋势、月度活跃度 | 折线图 |
| 🗺️ 路径推荐 | 技能缺口、推荐课程、学习路径 | 条形图、柱状图 |
| 🔗 相似度 | 学习者相似度矩阵、协同推荐 | 热力图 |
| ⚡ 学习效率 | 技能效率、难度对比、排行榜 | 柱状图、折线图 |
| 📉 技能衰退 | 衰退预测、复习优先级 | 折线图、条形图 |

### REST API 端点

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/overview` | 系统概览统计 |
| GET | `/api/learners` | 学习者列表 |
| GET | `/api/profile/skills?learnerId=L001` | 技能画像 |
| GET | `/api/profile/categories?learnerId=L001` | 类别分布 |
| GET | `/api/profile/ratings` | 综合评级 |
| GET | `/api/evolution/timeline?learnerId=L001` | 演进时间线 |
| GET | `/api/evolution/trend?learnerId=L001` | 进步趋势 |
| GET | `/api/evolution/monthly?learnerId=L001` | 月度活跃度 |
| GET | `/api/path/gaps/{learnerId}` | 技能缺口 |
| GET | `/api/path/courses/{learnerId}` | 推荐课程 |
| GET | `/api/path/learning/{learnerId}` | 学习路径 |
| GET | `/api/similarity/all` | 全部相似度 |
| GET | `/api/similarity/{learnerId}` | 相似学习者 |
| GET | `/api/collaborative/{learnerId}` | 协同推荐 |
| GET | `/api/popular-skills` | 热门技能 |
| GET | `/api/efficiency/skills?learnerId=L001` | 技能效率 |
| GET | `/api/efficiency/difficulty?learnerId=L001` | 按难度效率 |
| GET | `/api/efficiency/leaderboard` | 效率排行榜 |
| GET | `/api/decay/predict?learnerId=L001` | 衰退预测 |
| GET | `/api/decay/review?learnerId=L001` | 需复习技能 |
| GET | `/api/decay/priority/{learnerId}` | 复习优先级 |

---

## Spark 分析模块

### 三阶段架构

#### 阶段一：能力画像与路径推荐

1. **CapabilityProfiler** — 技能画像、类别分布、综合评级
2. **CapabilityEvolution** — 演进时间线、进步趋势、月度活跃度
3. **PathRecommender** — 技能缺口、课程推荐、学习路径

#### 阶段二：社交化推荐

4. **LearnerSimilarity** — 余弦相似度矩阵
5. **CollaborativeRecommender** — 协同过滤推荐、热门技能

#### 阶段三：智能分析

6. **LearningEfficiency** — 学习效率、难度对比、排行榜
7. **SkillDecayPredictor** — 衰退预测 `score × e^(-λ×days)`、复习优先级

### 数据模型

| 数据文件 | 说明 |
|---|---|
| `learners.csv` | 学习者基本信息（ID、姓名、注册日期） |
| `skills.csv` | 技能库（ID、名称、类别、难度等级 1-4） |
| `courses.csv` | 课程库（ID、名称、对应技能、难度、先修条件） |
| `learning_records.csv` | 学习记录（学习者、技能、得分、日期、学时） |

### Spark 批处理运行

```bash
spark-submit \
  --class com.spark.app.learner.LearnerAnalysisApp \
  target/spark-data-analysis-1.0.0.jar \
  data/ [可选:学习者ID]
```