package com.spark.app.learner;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import static org.apache.spark.sql.functions.*;

/**
 * SkillDecayPredictor - 技能衰退预测模块（第三阶段）。
 *
 * <p>基于学习记录的时间维度，预测学习者技能的遗忘程度，
 * 识别需要复习巩固的技能，帮助学习者维持已有能力。</p>
 *
 * <p>使用艾宾浩斯遗忘曲线简化模型：
 * <br>predicted_score = latest_score × e^(-λ × days_since_last)
 * <br>其中 λ 为衰退系数，默认值为 0.002。</p>
 *
 * <ul>
 *   <li>计算距上次学习的天数</li>
 *   <li>预测当前技能掌握水平</li>
 *   <li>识别需要复习的技能</li>
 *   <li>生成复习优先级列表</li>
 * </ul>
 */
public class SkillDecayPredictor {

    /** 默认衰退系数 λ（每天衰减率） */
    private static final double DEFAULT_DECAY_RATE = 0.002;

    /** 默认复习阈值：预测分数低于此值需要复习 */
    private static final int DEFAULT_REVIEW_THRESHOLD = 70;

    /**
     * 预测学习者各技能的当前掌握水平。
     *
     * <p>基于最后一次学习的得分和距今天数，使用指数衰减模型预测当前水平。</p>
     *
     * @param records      学习记录 Dataset
     * @param skills       技能信息 Dataset
     * @param referenceDate 参考日期（用于计算距离天数，格式 "yyyy-MM-dd"）
     * @param decayRate    衰退系数 λ
     * @return 技能衰退预测 (learner_id, skill_id, skill_name, latest_score,
     *         last_study_date, days_since_last, predicted_score, decay_amount)
     */
    public static Dataset<Row> predictSkillDecay(Dataset<Row> records, Dataset<Row> skills,
                                                  String referenceDate, double decayRate) {
        // 取每项技能的最新学习记录
        Dataset<Row> latestRecords = records
                .withColumn("rn", row_number().over(
                        org.apache.spark.sql.expressions.Window
                                .partitionBy("learner_id", "skill_id")
                                .orderBy(col("completion_date").desc())))
                .filter(col("rn").equalTo(1))
                .drop("rn");

        // 计算天数差和预测分数
        Dataset<Row> withDecay = latestRecords
                .withColumn("last_study_date", col("completion_date"))
                .withColumn("days_since_last",
                        datediff(lit(referenceDate).cast("date"),
                                col("completion_date").cast("date")))
                .withColumn("predicted_score",
                        round(col("score").multiply(
                                exp(lit(-decayRate).multiply(col("days_since_last")))), 1))
                .withColumn("decay_amount",
                        round(col("score").minus(col("predicted_score")), 1));

        // 关联技能信息
        return withDecay
                .join(skills.select("skill_id", "skill_name", "category"),
                        new String[]{"skill_id"}, "inner")
                .select(
                        col("learner_id"),
                        col("skill_id"),
                        col("skill_name"),
                        col("category"),
                        col("score").alias("latest_score"),
                        col("last_study_date"),
                        col("days_since_last"),
                        col("predicted_score"),
                        col("decay_amount")
                )
                .orderBy(col("learner_id"), col("decay_amount").desc());
    }

    /**
     * 使用默认衰退系数预测技能衰退。
     *
     * @param records       学习记录 Dataset
     * @param skills        技能信息 Dataset
     * @param referenceDate 参考日期
     * @return 技能衰退预测
     */
    public static Dataset<Row> predictSkillDecay(Dataset<Row> records, Dataset<Row> skills,
                                                  String referenceDate) {
        return predictSkillDecay(records, skills, referenceDate, DEFAULT_DECAY_RATE);
    }

    /**
     * 识别需要复习的技能。
     *
     * <p>筛选预测分数低于指定阈值的技能，表示这些技能可能已遗忘较多，需要复习。</p>
     *
     * @param records        学习记录 Dataset
     * @param skills         技能信息 Dataset
     * @param referenceDate  参考日期
     * @param threshold      复习阈值分数
     * @return 需要复习的技能列表
     */
    public static Dataset<Row> identifyReviewNeeded(Dataset<Row> records, Dataset<Row> skills,
                                                     String referenceDate, int threshold) {
        return predictSkillDecay(records, skills, referenceDate)
                .filter(col("predicted_score").lt(threshold))
                .orderBy(col("learner_id"), col("decay_amount").desc());
    }

    /**
     * 使用默认阈值识别需要复习的技能。
     *
     * @param records       学习记录 Dataset
     * @param skills        技能信息 Dataset
     * @param referenceDate 参考日期
     * @return 需要复习的技能列表
     */
    public static Dataset<Row> identifyReviewNeeded(Dataset<Row> records, Dataset<Row> skills,
                                                     String referenceDate) {
        return identifyReviewNeeded(records, skills, referenceDate, DEFAULT_REVIEW_THRESHOLD);
    }

    /**
     * 生成复习优先级列表。
     *
     * <p>综合考虑衰退量和技能难度，为学习者生成按优先级排序的复习清单。
     * 优先级 = 衰退量 × 难度等级（高难度技能遗忘后更需要及时复习）。</p>
     *
     * @param records       学习记录 Dataset
     * @param skills        技能信息 Dataset
     * @param referenceDate 参考日期
     * @param learnerId     学习者 ID
     * @return 复习优先级列表 (skill_id, skill_name, latest_score, predicted_score,
     *         decay_amount, difficulty_level, review_priority)
     */
    public static Dataset<Row> getReviewPriority(Dataset<Row> records, Dataset<Row> skills,
                                                  String referenceDate, String learnerId) {
        Dataset<Row> decay = predictSkillDecay(records, skills, referenceDate);

        return decay
                .filter(col("learner_id").equalTo(learnerId))
                .join(skills.select(
                                skills.col("skill_id").alias("s_skill_id"),
                                skills.col("difficulty_level")),
                        col("skill_id").equalTo(col("s_skill_id")))
                .withColumn("review_priority",
                        round(col("decay_amount").multiply(col("difficulty_level")), 1))
                .select(
                        col("skill_id"),
                        col("skill_name"),
                        col("latest_score"),
                        col("predicted_score"),
                        col("decay_amount"),
                        col("difficulty_level"),
                        col("review_priority")
                )
                .orderBy(col("review_priority").desc());
    }
}
