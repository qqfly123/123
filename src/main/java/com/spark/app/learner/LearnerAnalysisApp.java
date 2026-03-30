package com.spark.app.learner;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

/**
 * LearnerAnalysisApp - 终身学习者能力演进与路径推荐系统主入口。
 *
 * <p>整合能力画像、能力演进追踪和学习路径推荐三大模块，
 * 为终身学习者提供全方位的能力分析与个性化路径建议。</p>
 *
 * <p>用法：
 * <pre>
 *   spark-submit --class com.spark.app.learner.LearnerAnalysisApp \
 *     spark-data-analysis-1.0.0.jar \
 *     &lt;数据目录路径&gt; [学习者ID]
 * </pre>
 * </p>
 */
public class LearnerAnalysisApp {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("用法: LearnerAnalysisApp <数据目录路径> [学习者ID]");
            System.err.println("  数据目录下需包含: learners.csv, skills.csv, courses.csv, learning_records.csv");
            System.exit(1);
        }

        String dataDir = args[0];
        String learnerId = args.length > 1 ? args[1] : null;

        SparkSession spark = SparkSession.builder()
                .appName("LearnerAnalysisApp")
                .getOrCreate();

        try {
            // 加载数据
            Dataset<Row> learners = loadCsv(spark, dataDir + "/learners.csv",
                    SchemaDefinitions.getLearnerSchema());
            Dataset<Row> skills = loadCsv(spark, dataDir + "/skills.csv",
                    SchemaDefinitions.getSkillSchema());
            Dataset<Row> courses = loadCsv(spark, dataDir + "/courses.csv",
                    SchemaDefinitions.getCourseSchema());
            Dataset<Row> records = loadCsv(spark, dataDir + "/learning_records.csv",
                    SchemaDefinitions.getLearningRecordSchema());

            System.out.println("====== 数据加载完成 ======");
            System.out.println("学习者: " + learners.count() + " 人");
            System.out.println("技能库: " + skills.count() + " 项");
            System.out.println("课程库: " + courses.count() + " 门");
            System.out.println("学习记录: " + records.count() + " 条");

            // ========== 能力画像 ==========
            System.out.println("\n====== 学习者能力画像 ======");
            Dataset<Row> skillProfile = CapabilityProfiler.buildSkillProfile(records, skills);
            if (learnerId != null) {
                skillProfile.filter("learner_id = '" + learnerId + "'").show(50, false);
            } else {
                skillProfile.show(50, false);
            }

            System.out.println("====== 按类别能力分布 ======");
            CapabilityProfiler.getCategoryDistribution(skillProfile).show(50, false);

            System.out.println("====== 综合能力评级 ======");
            CapabilityProfiler.getOverallRating(skillProfile).show(false);

            // ========== 能力演进 ==========
            System.out.println("====== 技能演进时间线 ======");
            Dataset<Row> timeline = CapabilityEvolution.buildEvolutionTimeline(records, skills);
            if (learnerId != null) {
                timeline.filter("learner_id = '" + learnerId + "'").show(50, false);
            } else {
                timeline.show(50, false);
            }

            System.out.println("====== 进步趋势分析 ======");
            CapabilityEvolution.analyzeProgressTrend(records, skills).show(50, false);

            System.out.println("====== 月度学习活跃度 ======");
            CapabilityEvolution.getMonthlyActivity(records).show(50, false);

            // ========== 路径推荐 ==========
            if (learnerId != null) {
                System.out.println("====== 技能缺口分析 (学习者: " + learnerId + ") ======");
                PathRecommender.identifySkillGaps(skills, records, learnerId).show(false);

                System.out.println("====== 推荐课程 (学习者: " + learnerId + ") ======");
                PathRecommender.recommendCourses(courses, records, learnerId).show(false);

                System.out.println("====== 推荐学习路径 (学习者: " + learnerId + ") ======");
                PathRecommender.generateLearningPath(courses, records, learnerId, 5).show(false);
            } else {
                // 为所有学习者生成推荐
                String[] learnerIds = (String[]) learners.select("learner_id")
                        .as(org.apache.spark.sql.Encoders.STRING()).collectAsList().toArray(new String[0]);
                for (String lid : learnerIds) {
                    System.out.println("\n====== 推荐学习路径 (学习者: " + lid + ") ======");
                    PathRecommender.generateLearningPath(courses, records, lid, 3).show(false);
                }
            }

        } finally {
            spark.stop();
        }
    }

    private static Dataset<Row> loadCsv(SparkSession spark, String path,
                                         org.apache.spark.sql.types.StructType schema) {
        return spark.read()
                .option("header", "true")
                .schema(schema)
                .csv(path);
    }
}
