package com.spark.app.learner;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import static org.apache.spark.sql.functions.*;

/**
 * PathRecommender - 学习路径推荐引擎。
 *
 * <p>基于学习者当前能力画像，推荐适合的下一步学习路径：</p>
 * <ul>
 *   <li>识别尚未学习的技能（技能缺口）</li>
 *   <li>检查课程先修条件是否满足</li>
 *   <li>基于难度递进原则排序推荐</li>
 *   <li>生成个性化学习路径</li>
 * </ul>
 */
public class PathRecommender {

    /**
     * 识别学习者的技能缺口。
     *
     * <p>对比全部可用技能与学习者已掌握的技能，找出尚未学习的技能。</p>
     *
     * @param skills      全部技能 Dataset
     * @param records     学习记录 Dataset
     * @param learnerId   学习者 ID
     * @return 该学习者尚未学习的技能列表
     */
    public static Dataset<Row> identifySkillGaps(Dataset<Row> skills, Dataset<Row> records,
                                                  String learnerId) {
        Dataset<Row> learnedSkills = records
                .filter(col("learner_id").equalTo(learnerId))
                .select("skill_id")
                .distinct();

        return skills
                .join(learnedSkills, skills.col("skill_id").equalTo(learnedSkills.col("skill_id")), "left_anti")
                .orderBy("difficulty_level", "skill_id");
    }

    /**
     * 推荐满足先修条件的课程。
     *
     * <p>对于每门尚未学习的课程，检查其所有先修技能是否已被该学习者掌握。
     * 如果先修条件全部满足或课程无先修条件，则标记为可推荐。</p>
     *
     * @param courses    课程 Dataset
     * @param records    学习记录 Dataset
     * @param learnerId  学习者 ID
     * @return 推荐课程列表，附带先修条件满足状态
     */
    public static Dataset<Row> recommendCourses(Dataset<Row> courses, Dataset<Row> records,
                                                 String learnerId) {
        // 获取学习者已掌握的技能ID列表
        Dataset<Row> learnedSkills = records
                .filter(col("learner_id").equalTo(learnerId))
                .select("skill_id")
                .distinct();

        // 获取已学过的课程对应的技能（用于过滤已完成的课程）
        Dataset<Row> learnedCourseSkills = learnedSkills
                .withColumnRenamed("skill_id", "learned_skill_id");

        // 排除已学过的技能对应的课程
        Dataset<Row> unlearned = courses
                .join(learnedCourseSkills,
                        courses.col("skill_id").equalTo(learnedCourseSkills.col("learned_skill_id")),
                        "left_anti");

        // 将已掌握技能收集为一个列表以便检查先修条件
        Dataset<Row> learnedList = learnedSkills
                .agg(collect_set("skill_id").alias("learned_set"));

        // 交叉连接后检查先修条件
        Dataset<Row> result = unlearned
                .crossJoin(learnedList)
                .withColumn("prereqs_met",
                        when(col("prerequisites").isNull().or(col("prerequisites").equalTo("")),
                                lit(true))
                                .otherwise(
                                        // 检查每个先修技能是否都在已学技能集合中
                                        checkPrerequisites(col("prerequisites"), col("learned_set"))
                                ))
                .drop("learned_set");

        return result
                .orderBy(col("prereqs_met").desc(), col("difficulty_level"), col("course_id"));
    }

    /**
     * 生成个性化学习路径（仅返回满足先修条件的推荐课程）。
     *
     * @param courses    课程 Dataset
     * @param records    学习记录 Dataset
     * @param learnerId  学习者 ID
     * @param maxCourses 推荐的最大课程数
     * @return 推荐学习路径
     */
    public static Dataset<Row> generateLearningPath(Dataset<Row> courses, Dataset<Row> records,
                                                     String learnerId, int maxCourses) {
        return recommendCourses(courses, records, learnerId)
                .filter(col("prereqs_met").equalTo(true))
                .limit(maxCourses)
                .select("course_id", "course_name", "skill_id", "difficulty_level", "prerequisites");
    }

    /**
     * 检查先修条件中的所有技能是否都在已学技能集合中。
     * 先修条件以分号分隔，例如 "S001;S003"。
     */
    private static org.apache.spark.sql.Column checkPrerequisites(
            org.apache.spark.sql.Column prerequisites,
            org.apache.spark.sql.Column learnedSet) {
        // 将先修条件字符串拆分为数组，然后检查每个元素是否在已学集合中
        return size(array_except(split(prerequisites, ";"), learnedSet)).equalTo(0);
    }
}
