package com.spark.app.learner;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import static org.apache.spark.sql.functions.*;

/**
 * LearningEfficiency - 学习效率分析模块（第三阶段）。
 *
 * <p>评估学习者的学习效率，帮助发现最有效的学习方式和最佳学习节奏：</p>
 * <ul>
 *   <li>每小时学习产出（得分/时长）</li>
 *   <li>多次学习同一技能时的提升效率</li>
 *   <li>不同难度等级的学习效率对比</li>
 *   <li>学习效率排行榜</li>
 * </ul>
 */
public class LearningEfficiency {

    /**
     * 计算每位学习者每项技能的学习效率。
     *
     * <p>效率指标 = 最新得分 / 累计学习时长（得分/小时）。
     * 还计算单次最佳效率（最高单次得分/该次时长）。</p>
     *
     * @param records 学习记录 Dataset
     * @param skills  技能信息 Dataset
     * @return 学习效率数据 (learner_id, skill_id, skill_name, latest_score,
     *         total_hours, efficiency, best_single_efficiency)
     */
    public static Dataset<Row> computeSkillEfficiency(Dataset<Row> records, Dataset<Row> skills) {
        // 获取最新得分
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

        // 聚合学习时长
        Dataset<Row> aggregated = records
                .groupBy("learner_id", "skill_id")
                .agg(
                        sum("study_hours").alias("total_hours"),
                        count("*").alias("attempts")
                );

        // 单次最佳效率
        Dataset<Row> bestSingle = records
                .filter(col("study_hours").gt(0))
                .withColumn("single_eff",
                        col("score").cast("double").divide(col("study_hours")))
                .groupBy("learner_id", "skill_id")
                .agg(max("single_eff").alias("best_single_efficiency"));

        // 合并
        Dataset<Row> result = aggregated
                .join(latestRecords, new String[]{"learner_id", "skill_id"}, "inner")
                .join(bestSingle, new String[]{"learner_id", "skill_id"}, "inner")
                .join(skills.select("skill_id", "skill_name", "category", "difficulty_level"),
                        new String[]{"skill_id"}, "inner");

        return result
                .withColumn("efficiency",
                        when(col("total_hours").gt(0),
                                col("latest_score").cast("double").divide(col("total_hours"))
                                        .cast("decimal(5,2)"))
                                .otherwise(lit(0)))
                .withColumn("best_single_efficiency",
                        col("best_single_efficiency").cast("decimal(5,2)"))
                .select(
                        col("learner_id"),
                        col("skill_id"),
                        col("skill_name"),
                        col("category"),
                        col("difficulty_level"),
                        col("latest_score"),
                        col("total_hours"),
                        col("attempts"),
                        col("efficiency"),
                        col("best_single_efficiency")
                )
                .orderBy(col("learner_id"), col("efficiency").desc());
    }

    /**
     * 按难度等级分析学习效率。
     *
     * <p>对比不同难度技能的平均效率，反映学习者应对高难度内容的能力。</p>
     *
     * @param records 学习记录 Dataset
     * @param skills  技能信息 Dataset
     * @return 按难度等级的效率分析 (learner_id, difficulty_level, avg_efficiency, avg_score, avg_hours)
     */
    public static Dataset<Row> efficiencyByDifficulty(Dataset<Row> records, Dataset<Row> skills) {
        Dataset<Row> efficiency = computeSkillEfficiency(records, skills);

        return efficiency
                .groupBy("learner_id", "difficulty_level")
                .agg(
                        avg("efficiency").cast("decimal(5,2)").alias("avg_efficiency"),
                        avg("latest_score").cast("decimal(5,1)").alias("avg_score"),
                        avg("total_hours").cast("decimal(5,1)").alias("avg_hours")
                )
                .orderBy("learner_id", "difficulty_level");
    }

    /**
     * 生成学习效率排行榜。
     *
     * <p>综合所有技能的学习效率，对学习者进行排名。</p>
     *
     * @param records 学习记录 Dataset
     * @param skills  技能信息 Dataset
     * @return 效率排行榜 (learner_id, overall_efficiency, total_skills, total_hours, avg_score)
     */
    public static Dataset<Row> getEfficiencyLeaderboard(Dataset<Row> records, Dataset<Row> skills) {
        Dataset<Row> efficiency = computeSkillEfficiency(records, skills);

        return efficiency
                .groupBy("learner_id")
                .agg(
                        avg("efficiency").cast("decimal(5,2)").alias("overall_efficiency"),
                        count("skill_id").alias("total_skills"),
                        sum("total_hours").alias("total_hours"),
                        avg("latest_score").cast("decimal(5,1)").alias("avg_score")
                )
                .orderBy(col("overall_efficiency").desc());
    }
}
