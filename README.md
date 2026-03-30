# Spark Data Analysis

基于 Apache Spark 的数据分析项目，包含词频统计和 CSV 数据分析两个示例应用。

## 项目结构

```
├── pom.xml                                    # Maven 构建配置
├── data/
│   └── sales_sample.csv                       # 示例销售数据
└── src/
    ├── main/java/com/spark/app/
    │   ├── WordCount.java                     # 词频统计应用
    │   └── SparkDataAnalysis.java             # CSV 数据分析应用
    └── test/java/com/spark/app/
        ├── WordCountTest.java                 # 词频统计测试
        └── SparkDataAnalysisTest.java         # 数据分析测试
```

## 环境要求

- Java 11+
- Maven 3.6+
- Apache Spark 3.5.x（运行时需要）

## 构建

```bash
mvn clean package
```

## 运行

### 词频统计

```bash
spark-submit \
  --class com.spark.app.WordCount \
  target/spark-data-analysis-1.0.0.jar \
  <输入文本文件路径>
```

### CSV 数据分析

```bash
spark-submit \
  --class com.spark.app.SparkDataAnalysis \
  target/spark-data-analysis-1.0.0.jar \
  data/sales_sample.csv
```

## 运行测试

```bash
mvn test
```

## 功能说明

### WordCount（词频统计）

读取文本文件，将内容按空格拆分为单词，统计每个单词出现的次数，并按出现次数降序输出。

### SparkDataAnalysis（数据分析）

读取 CSV 格式的销售数据，执行以下分析：

- **按类别统计**：汇总每个产品类别的总销售额、总数量和平均价格
- **按日期统计**：汇总每天的销售额和订单数量
- **热门产品排行**：列出销售额最高的前 N 个产品
- **基础统计信息**：展示数量和价格的描述性统计