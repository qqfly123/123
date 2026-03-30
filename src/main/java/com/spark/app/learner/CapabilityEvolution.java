package com.spark.app.learner;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import static org.apache.spark.sql.functions.*;

/**
 * CapabilityEvolution - 追踪学习者的能力演进。
 *
 * <p>基于学习记录中的时间维度，分析每位学习者各项技能随时间变化的趋势，包括：</p>
 * <ul>
 *   <li>技能得分时间线</li>
 *   <li>得分变化趋势（提升/下降/稳定）</li>
 *   <li>月度学习热力分析</li>
 * </ul>
 */
public class CapabilityEvolution {

    /**
     * 生成学习者技能演进时间线。
     *
     * <p>对每位学习者的每项技能，按完成日期排序，
     * 计算与前一次得分的差值（score_change）以及累计学习时长。</p>
     *
     * @param records 学习记录 Dataset
     * @param skills  技能信息 Dataset
     * @return 技能演进时间线
     */
    public static Dataset<Row> buildEvolutionTimeline(Dataset<Row> records, Dataset<Row> skills) {
        var window = org.apache.spark.sql.expressions.Window
                .partitionBy("learner_id", "skill_id")
                .orderBy("completion_date");

        Dataset<Row> timeline = records
                .withColumn("prev_score", lag("score", 1).over(window))
                .withColumn("score_change",
                        when(col("prev_score").isNull(), lit(0))
                                .otherwise(col("score").minus(col("prev_score"))))
                .withColumn("cumulative_hours", sum("study_hours").over(window));

        return timeline
                .join(skills.select("skill_id", "skill_name", "category"),
                        new String[]{"skill_id"}, "inner")
                .select(
                        col("learner_id"),
                        col("skill_id"),
                        col("skill_name"),
                        col("category"),
                        col("completion_date"),
                        col("score"),
                        col("prev_score"),
                        col("score_change"),
                        col("study_hours"),
                        col("cumulative_hours")
                )
                .orderBy("learner_id", "skill_id", "completion_date");
    }

    /**
     * 分析每位学习者各项技能的进步趋势。
     *
     * <p>对有多次学习记录的技能，比较第一次和最后一次得分来判断趋势：
     * 提升、下降或稳定。</p>
     *
     * @param records 学习记录 Dataset
     * @param skills  技能信息 Dataset
     * @return 每项技能的进步趋势
     */
    public static Dataset<Row> analyzeProgressTrend(Dataset<Row> records, Dataset<Row> skills) {
        var firstWindow = org.apache.spark.sql.expressions.Window
                .partitionBy("learner_id", "skill_id")
                .orderBy("completion_date");
        var lastWindow = org.apache.spark.sql.expressions.Window
                .partitionBy("learner_id", "skill_id")
                .orderBy(col("completion_date").desc());

        Dataset<Row> withFirst = records
                .withColumn("rn_first", row_number().over(firstWindow))
                .filter(col("rn_first").equalTo(1))
                .select(
                        col("learner_id"),
                        col("skill_id"),
                        col("score").alias("first_score"),
                        col("completion_date").alias("first_date")
                );

        Dataset<Row> withLast = records
                .withColumn("rn_last", row_number().over(lastWindow))
                .filter(col("rn_last").equalTo(1))
                .select(
                        col("learner_id"),
                        col("skill_id"),
                        col("score").alias("last_score"),
                        col("completion_date").alias("last_date")
                );

        Dataset<Row> attemptCounts = records
                .groupBy("learner_id", "skill_id")
                .agg(
                        count("*").alias("attempts"),
                        sum("study_hours").alias("total_hours")
                );

        Dataset<Row> trend = withFirst
                .join(withLast, new String[]{"learner_id", "skill_id"}, "inner")
                .join(attemptCounts, new String[]{"learner_id", "skill_id"}, "inner")
                .withColumn("score_improvement", col("last_score").minus(col("first_score")))
                .withColumn("trend",
                        when(col("score_improvement").gt(0), "提升")
                                .when(col("score_improvement").lt(0), "下降")
                                .otherwise("稳定"))
                .join(skills.select("skill_id", "skill_name"),
                        new String[]{"skill_id"}, "inner");

        return trend.select(
                col("learner_id"),
                col("skill_id"),
                col("skill_name"),
                col("first_score"),
                col("last_score"),
                col("score_improvement"),
                col("trend"),
                col("attempts"),
                col("total_hours"),
                col("first_date"),
                col("last_date")
        ).orderBy("learner_id", "skill_id");
    }

    /**
     * 生成月度学习活跃度统计。
     *
     * <p>按学习者和月份统计学习课程数、总学习时长和平均得分。</p>
     *
     * @param records 学习记录 Dataset
     * @return 月度学习热力数据
     */
    public static Dataset<Row> getMonthlyActivity(Dataset<Row> records) {
        return records
                .withColumn("month", substring(col("completion_date"), 1, 7))
                .groupBy("learner_id", "month")
                .agg(
                        count("*").alias("courses_completed"),
                        sum("study_hours").alias("monthly_hours"),
                        avg("score").cast("decimal(5,1)").alias("avg_monthly_score")
                )
                .orderBy("learner_id", "month");
    }
}
