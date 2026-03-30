package com.spark.app.learner;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

import static org.apache.spark.sql.functions.*;

/**
 * CapabilityProfiler - 构建学习者能力画像。
 *
 * <p>通过聚合学习记录数据，为每位学习者生成能力画像，包括：</p>
 * <ul>
 *   <li>每项技能的最新得分、最高得分、平均得分</li>
 *   <li>总学习时长统计</li>
 *   <li>按技能类别的能力分布</li>
 *   <li>综合能力评级</li>
 * </ul>
 */
public class CapabilityProfiler {

    /**
     * 生成学习者的技能级别画像。
     *
     * <p>对每位学习者的每项技能，取最新得分（按完成日期排序的最后一次）、
     * 最高得分、平均得分，以及累计学习时长。</p>
     *
     * @param records 学习记录 Dataset
     * @param skills  技能信息 Dataset
     * @return 学习者技能画像，包含 learner_id, skill_id, skill_name, category,
     *         latest_score, max_score, avg_score, total_hours, attempts
     */
    public static Dataset<Row> buildSkillProfile(Dataset<Row> records, Dataset<Row> skills) {
        // 计算每位学习者每项技能的最新一次记录的得分
        Dataset<Row> latestRecords = records
                .withColumn("rn", row_number().over(
                        org.apache.spark.sql.expressions.Window
                                .partitionBy("learner_id", "skill_id")
                                .orderBy(col("completion_date").desc())))
                .filter(col("rn").equalTo(1))
                .select(
                        col("learner_id"),
                        col("skill_id"),
                        col("score").alias("latest_score")
                );

        // 聚合每位学习者每项技能的统计信息
        Dataset<Row> aggregated = records
                .groupBy("learner_id", "skill_id")
                .agg(
                        max("score").alias("max_score"),
                        avg("score").alias("avg_score"),
                        sum("study_hours").alias("total_hours"),
                        count("*").alias("attempts")
                );

        // 合并最新得分与聚合统计
        Dataset<Row> profile = aggregated
                .join(latestRecords, new String[]{"learner_id", "skill_id"}, "inner")
                .join(skills.select("skill_id", "skill_name", "category", "difficulty_level"),
                        new String[]{"skill_id"}, "inner");

        return profile.select(
                col("learner_id"),
                col("skill_id"),
                col("skill_name"),
                col("category"),
                col("difficulty_level"),
                col("latest_score"),
                col("max_score"),
                col("avg_score").cast("decimal(5,1)").alias("avg_score"),
                col("total_hours"),
                col("attempts")
        ).orderBy("learner_id", "skill_id");
    }

    /**
     * 按技能类别生成能力分布。
     *
     * <p>对每位学习者按类别聚合，计算平均得分、已掌握技能数和总学习时长。</p>
     *
     * @param skillProfile 由 {@link #buildSkillProfile} 生成的技能画像
     * @return 按类别汇总的能力分布
     */
    public static Dataset<Row> getCategoryDistribution(Dataset<Row> skillProfile) {
        return skillProfile
                .groupBy("learner_id", "category")
                .agg(
                        avg("latest_score").cast("decimal(5,1)").alias("avg_category_score"),
                        count("skill_id").alias("skills_count"),
                        sum("total_hours").alias("category_hours")
                )
                .orderBy(col("learner_id"), col("avg_category_score").desc());
    }

    /**
     * 生成综合能力评级。
     *
     * <p>综合各项技能加权得分（按技能难度加权），为每位学习者生成总体评级：
     * 优秀 (≥85)、良好 (≥70)、进步中 (≥60)、入门 (&lt;60)。</p>
     *
     * @param skillProfile 由 {@link #buildSkillProfile} 生成的技能画像
     * @return 每位学习者的综合评级
     */
    public static Dataset<Row> getOverallRating(Dataset<Row> skillProfile) {
        return skillProfile
                .withColumn("weighted_score",
                        col("latest_score").multiply(col("difficulty_level")))
                .groupBy("learner_id")
                .agg(
                        sum("weighted_score").divide(sum("difficulty_level"))
                                .cast("decimal(5,1)").alias("overall_score"),
                        count("skill_id").alias("total_skills"),
                        sum("total_hours").alias("total_study_hours")
                )
                .withColumn("rating",
                        when(col("overall_score").geq(85), "优秀")
                                .when(col("overall_score").geq(70), "良好")
                                .when(col("overall_score").geq(60), "进步中")
                                .otherwise("入门"))
                .orderBy(col("overall_score").desc());
    }
}
