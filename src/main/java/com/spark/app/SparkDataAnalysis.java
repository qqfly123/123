package com.spark.app;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

import static org.apache.spark.sql.functions.*;

/**
 * SparkDataAnalysis - 基于 Spark SQL 的 CSV 数据分析应用。
 *
 * <p>读取 CSV 文件，执行常见的数据分析操作，包括数据概览、统计汇总和分组聚合。</p>
 *
 * <p>用法：
 * <pre>
 *   spark-submit --class com.spark.app.SparkDataAnalysis spark-data-analysis-1.0.0.jar &lt;CSV文件路径&gt;
 * </pre>
 * </p>
 */
public class SparkDataAnalysis {

    /**
     * 定义销售数据的 Schema。
     */
    public static StructType getSalesSchema() {
        return new StructType(new StructField[]{
                DataTypes.createStructField("date", DataTypes.StringType, false),
                DataTypes.createStructField("product", DataTypes.StringType, false),
                DataTypes.createStructField("category", DataTypes.StringType, false),
                DataTypes.createStructField("quantity", DataTypes.IntegerType, false),
                DataTypes.createStructField("price", DataTypes.DoubleType, false)
        });
    }

    /**
     * 加载 CSV 数据文件并返回 Dataset。
     *
     * @param spark    SparkSession 实例
     * @param filePath CSV 文件路径
     * @return 加载后的 Dataset
     */
    public static Dataset<Row> loadData(SparkSession spark, String filePath) {
        return spark.read()
                .option("header", "true")
                .schema(getSalesSchema())
                .csv(filePath);
    }

    /**
     * 按产品类别汇总销售额。
     *
     * @param data 输入 Dataset
     * @return 每个类别的总销售额和总数量
     */
    public static Dataset<Row> analyzeSalesByCategory(Dataset<Row> data) {
        return data
                .withColumn("total_price", col("quantity").multiply(col("price")))
                .groupBy("category")
                .agg(
                        sum("total_price").alias("total_revenue"),
                        sum("quantity").alias("total_quantity"),
                        avg("price").alias("avg_price")
                )
                .orderBy(col("total_revenue").desc());
    }

    /**
     * 按日期汇总销售趋势。
     *
     * @param data 输入 Dataset
     * @return 每天的销售额和订单数
     */
    public static Dataset<Row> analyzeSalesByDate(Dataset<Row> data) {
        return data
                .withColumn("total_price", col("quantity").multiply(col("price")))
                .groupBy("date")
                .agg(
                        sum("total_price").alias("daily_revenue"),
                        count("*").alias("order_count")
                )
                .orderBy("date");
    }

    /**
     * 获取销售额排名前 N 的产品。
     *
     * @param data 输入 Dataset
     * @param n    返回的产品数量
     * @return 销售额最高的 N 个产品
     */
    public static Dataset<Row> getTopProducts(Dataset<Row> data, int n) {
        return data
                .withColumn("total_price", col("quantity").multiply(col("price")))
                .groupBy("product")
                .agg(sum("total_price").alias("total_revenue"))
                .orderBy(col("total_revenue").desc())
                .limit(n);
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("用法: SparkDataAnalysis <CSV文件路径>");
            System.exit(1);
        }

        SparkSession spark = SparkSession.builder()
                .appName("SparkDataAnalysis")
                .getOrCreate();

        try {
            Dataset<Row> data = loadData(spark, args[0]);

            System.out.println("========== 数据概览 ==========");
            data.printSchema();
            System.out.println("总记录数: " + data.count());
            data.show(10, false);

            System.out.println("========== 按类别统计 ==========");
            analyzeSalesByCategory(data).show(false);

            System.out.println("========== 按日期统计 ==========");
            analyzeSalesByDate(data).show(false);

            System.out.println("========== 销售额 Top 5 产品 ==========");
            getTopProducts(data, 5).show(false);

            System.out.println("========== 基础统计信息 ==========");
            data.describe("quantity", "price").show();
        } finally {
            spark.stop();
        }
    }
}
