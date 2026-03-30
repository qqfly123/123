package com.spark.app;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

import static org.apache.spark.sql.functions.*;

/**
 * WordCount - 经典的 Spark 词频统计应用。
 *
 * <p>读取文本文件，统计每个单词出现的次数，并按次数降序输出结果。</p>
 *
 * <p>用法：
 * <pre>
 *   spark-submit --class com.spark.app.WordCount spark-data-analysis-1.0.0.jar &lt;输入文件路径&gt;
 * </pre>
 * </p>
 */
public class WordCount {

    /**
     * 对指定文本文件执行词频统计，返回包含 word 和 count 两列的 Dataset。
     *
     * @param spark    SparkSession 实例
     * @param filePath 输入文本文件路径
     * @return 每个单词及其出现次数，按 count 降序排列
     */
    public static Dataset<Row> countWords(SparkSession spark, String filePath) {
        Dataset<Row> lines = spark.read().textFile(filePath).toDF("line");

        Dataset<Row> words = lines
                .select(explode(split(col("line"), "\\s+")).alias("word"))
                .filter(col("word").notEqual(""));

        return words
                .groupBy("word")
                .agg(count("*").alias("count"))
                .orderBy(col("count").desc());
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("用法: WordCount <输入文件路径>");
            System.exit(1);
        }

        SparkSession spark = SparkSession.builder()
                .appName("WordCount")
                .getOrCreate();

        try {
            Dataset<Row> result = countWords(spark, args[0]);

            System.out.println("========== 词频统计结果 ==========");
            result.show(50, false);
            System.out.println("不同单词总数: " + result.count());
        } finally {
            spark.stop();
        }
    }
}
