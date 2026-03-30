package com.spark.app.learner;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import static org.apache.spark.sql.functions.*;

/**
 * CollaborativeRecommender - 基于协同过滤的课程推荐（第二阶段）。
 *
 * <p>利用学习者相似度，推荐相似学习者已学但目标学习者尚未学习的课程。
 * 推荐优先级由相似度和课程难度共同决定。</p>
 *
 * <ul>
 *   <li>基于相似学习者的学习历史推荐课程</li>
 *   <li>结合相似度权重和技能得分排序</li>
 *   <li>过滤已学课程，避免重复推荐</li>
 * </ul>
 */
public class CollaborativeRecommender {

    /**
     * 基于协同过滤为指定学习者推荐课程。
     *
     * <p>算法步骤：
     * <ol>
     *   <li>找到最相似的 topK 位学习者</li>
     *   <li>收集这些相似学习者学过但目标学习者未学过的技能</li>
     *   <li>按相似度加权得分排序，关联课程信息</li>
     * </ol>
     * </p>
     *
     * @param records    学习记录 Dataset
     * @param courses    课程 Dataset
     * @param learnerId  目标学习者 ID
     * @param topK       参考的相似学习者数量
     * @param maxResults 返回的最大推荐数量
     * @return 推荐课程列表 (course_id, course_name, skill_id, recommended_by_count, avg_weighted_score)
     */
    public static Dataset<Row> recommendBySimilarity(Dataset<Row> records, Dataset<Row> courses,
                                                      String learnerId, int topK, int maxResults) {
        // 1. 找到相似学习者
        Dataset<Row> similarLearners = LearnerSimilarity.findSimilarLearners(records, learnerId, topK);

        // 2. 目标学习者已学的技能
        Dataset<Row> mySkills = records
                .filter(col("learner_id").equalTo(learnerId))
                .select("skill_id")
                .distinct();

        // 3. 获取相似学习者的最新学习记录
        Dataset<Row> simRecords = records
                .join(similarLearners,
                        records.col("learner_id").equalTo(similarLearners.col("similar_learner")))
                .select(
                        records.col("learner_id"),
                        records.col("skill_id"),
                        records.col("score"),
                        similarLearners.col("similarity")
                );

        // 4. 取每位相似学习者每项技能的最新得分
        Dataset<Row> simLatest = simRecords
                .withColumn("rn", row_number().over(
                        org.apache.spark.sql.expressions.Window
                                .partitionBy("learner_id", "skill_id")
                                .orderBy(col("score").desc())))
                .filter(col("rn").equalTo(1))
                .drop("rn");

        // 5. 过滤掉目标学习者已学的技能
        Dataset<Row> newSkills = simLatest
                .join(mySkills,
                        simLatest.col("skill_id").equalTo(mySkills.col("skill_id")),
                        "left_anti");

        // 6. 按技能聚合：推荐人数 + 相似度加权平均分
        Dataset<Row> recommendations = newSkills
                .groupBy(newSkills.col("skill_id"))
                .agg(
                        count("*").alias("recommended_by_count"),
                        avg(col("score").multiply(col("similarity")))
                                .cast("decimal(5,1)").alias("avg_weighted_score")
                )
                .orderBy(col("recommended_by_count").desc(), col("avg_weighted_score").desc());

        // 7. 关联课程信息
        return recommendations
                .join(courses, recommendations.col("skill_id").equalTo(courses.col("skill_id")))
                .select(
                        courses.col("course_id"),
                        courses.col("course_name"),
                        courses.col("skill_id"),
                        col("recommended_by_count"),
                        col("avg_weighted_score")
                )
                .orderBy(col("recommended_by_count").desc(), col("avg_weighted_score").desc())
                .limit(maxResults);
    }

    /**
     * 发现热门技能：被最多学习者学习且得分较高的技能。
     *
     * @param records 学习记录 Dataset
     * @param skills  技能信息 Dataset
     * @param topN    返回的热门技能数量
     * @return 热门技能列表 (skill_id, skill_name, category, learner_count, avg_score)
     */
    public static Dataset<Row> discoverPopularSkills(Dataset<Row> records, Dataset<Row> skills,
                                                      int topN) {
        return records
                .groupBy("skill_id")
                .agg(
                        countDistinct("learner_id").alias("learner_count"),
                        avg("score").cast("decimal(5,1)").alias("avg_score")
                )
                .join(skills.select("skill_id", "skill_name", "category"),
                        new String[]{"skill_id"}, "inner")
                .select("skill_id", "skill_name", "category", "learner_count", "avg_score")
                .orderBy(col("learner_count").desc(), col("avg_score").desc())
                .limit(topN);
    }
}
