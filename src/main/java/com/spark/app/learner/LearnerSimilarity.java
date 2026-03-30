package com.spark.app.learner;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import static org.apache.spark.sql.functions.*;

/**
 * LearnerSimilarity - 学习者相似度分析模块（第二阶段）。
 *
 * <p>基于学习者的技能得分向量，利用余弦相似度算法计算学习者之间的相似程度，
 * 用于支持协同过滤推荐。</p>
 *
 * <ul>
 *   <li>构建技能得分向量（learner × skill 矩阵）</li>
 *   <li>计算两两学习者之间的余弦相似度</li>
 *   <li>查找指定学习者的最相似学习者</li>
 * </ul>
 */
public class LearnerSimilarity {

    /**
     * 构建学习者技能得分矩阵。
     *
     * <p>对每位学习者的每项技能，取最新得分作为该技能维度的值。</p>
     *
     * @param records 学习记录 Dataset
     * @return 学习者-技能得分矩阵 (learner_id, skill_id, score)
     */
    public static Dataset<Row> buildScoreMatrix(Dataset<Row> records) {
        return records
                .withColumn("rn", row_number().over(
                        org.apache.spark.sql.expressions.Window
                                .partitionBy("learner_id", "skill_id")
                                .orderBy(col("completion_date").desc())))
                .filter(col("rn").equalTo(1))
                .select(col("learner_id"), col("skill_id"), col("score"))
                .orderBy("learner_id", "skill_id");
    }

    /**
     * 计算所有学习者两两之间的余弦相似度。
     *
     * <p>仅基于两位学习者共同学过的技能计算相似度。
     * 余弦相似度 = (A·B) / (|A| × |B|)，结果在 [0, 1] 区间。
     * 至少需要 1 项共同技能才产生有效结果。</p>
     *
     * @param records 学习记录 Dataset
     * @return 学习者对的相似度 (learner_a, learner_b, similarity, common_skills)
     */
    public static Dataset<Row> computeAllSimilarities(Dataset<Row> records) {
        Dataset<Row> matrix = buildScoreMatrix(records);

        Dataset<Row> matrixA = matrix
                .withColumnRenamed("learner_id", "learner_a")
                .withColumnRenamed("score", "score_a");

        Dataset<Row> matrixB = matrix
                .withColumnRenamed("learner_id", "learner_b")
                .withColumnRenamed("score", "score_b");

        // 自连接找到共同技能
        Dataset<Row> joined = matrixA.join(matrixB,
                        matrixA.col("skill_id").equalTo(matrixB.col("skill_id")))
                .filter(col("learner_a").lt(col("learner_b")))
                .select(
                        col("learner_a"),
                        col("learner_b"),
                        col("score_a"),
                        col("score_b")
                );

        // 计算余弦相似度各分量
        return joined
                .groupBy("learner_a", "learner_b")
                .agg(
                        sum(col("score_a").multiply(col("score_b"))).alias("dot_product"),
                        sqrt(sum(col("score_a").multiply(col("score_a")))).alias("norm_a"),
                        sqrt(sum(col("score_b").multiply(col("score_b")))).alias("norm_b"),
                        count("*").alias("common_skills")
                )
                .withColumn("similarity",
                        when(col("norm_a").multiply(col("norm_b")).gt(0),
                                col("dot_product").divide(col("norm_a").multiply(col("norm_b")))
                                        .cast("decimal(5,4)"))
                                .otherwise(lit(0)))
                .select("learner_a", "learner_b", "similarity", "common_skills")
                .orderBy(col("similarity").desc());
    }

    /**
     * 查找与指定学习者最相似的前 N 位学习者。
     *
     * @param records   学习记录 Dataset
     * @param learnerId 目标学习者 ID
     * @param topN      返回的相似学习者数量
     * @return 相似学习者列表 (similar_learner, similarity, common_skills)
     */
    public static Dataset<Row> findSimilarLearners(Dataset<Row> records, String learnerId, int topN) {
        Dataset<Row> allSim = computeAllSimilarities(records);

        // 目标学习者可能在 learner_a 或 learner_b 列
        Dataset<Row> asA = allSim
                .filter(col("learner_a").equalTo(learnerId))
                .select(
                        col("learner_b").alias("similar_learner"),
                        col("similarity"),
                        col("common_skills")
                );

        Dataset<Row> asB = allSim
                .filter(col("learner_b").equalTo(learnerId))
                .select(
                        col("learner_a").alias("similar_learner"),
                        col("similarity"),
                        col("common_skills")
                );

        return asA.union(asB)
                .orderBy(col("similarity").desc())
                .limit(topN);
    }
}
