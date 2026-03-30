package com.spark.app.learner;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

import static org.apache.spark.sql.functions.col;

/**
 * LearnerAnalysisApp - 终身学习者能力演进与路径推荐系统主入口。
 *
 * <p>整合三个阶段的六大分析模块，为终身学习者提供全方位的能力分析与个性化路径建议。</p>
 *
 * <p><strong>阶段一：</strong>能力画像 + 能力演进 + 路径推荐<br>
 * <strong>阶段二：</strong>学习者相似度 + 协同过滤推荐<br>
 * <strong>阶段三：</strong>学习效率分析 + 技能衰退预测</p>
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

            // ==================== 阶段一 ====================
            System.out.println("\n========== 阶段一：能力画像与路径推荐 ==========");

            // 能力画像
            System.out.println("\n====== 学习者能力画像 ======");
            Dataset<Row> skillProfile = CapabilityProfiler.buildSkillProfile(records, skills);
            if (learnerId != null) {
                skillProfile.filter(col("learner_id").equalTo(learnerId)).show(50, false);
            } else {
                skillProfile.show(50, false);
            }

            System.out.println("====== 按类别能力分布 ======");
            CapabilityProfiler.getCategoryDistribution(skillProfile).show(50, false);

            System.out.println("====== 综合能力评级 ======");
            CapabilityProfiler.getOverallRating(skillProfile).show(false);

            // 能力演进
            System.out.println("====== 技能演进时间线 ======");
            Dataset<Row> timeline = CapabilityEvolution.buildEvolutionTimeline(records, skills);
            if (learnerId != null) {
                timeline.filter(col("learner_id").equalTo(learnerId)).show(50, false);
            } else {
                timeline.show(50, false);
            }

            System.out.println("====== 进步趋势分析 ======");
            CapabilityEvolution.analyzeProgressTrend(records, skills).show(50, false);

            System.out.println("====== 月度学习活跃度 ======");
            CapabilityEvolution.getMonthlyActivity(records).show(50, false);

            // 路径推荐
            if (learnerId != null) {
                System.out.println("====== 技能缺口分析 (学习者: " + learnerId + ") ======");
                PathRecommender.identifySkillGaps(skills, records, learnerId).show(false);

                System.out.println("====== 推荐课程 (学习者: " + learnerId + ") ======");
                PathRecommender.recommendCourses(courses, records, learnerId).show(false);

                System.out.println("====== 推荐学习路径 (学习者: " + learnerId + ") ======");
                PathRecommender.generateLearningPath(courses, records, learnerId, 5).show(false);
            } else {
                String[] learnerIds = (String[]) learners.select("learner_id")
                        .as(org.apache.spark.sql.Encoders.STRING()).collectAsList().toArray(new String[0]);
                for (String lid : learnerIds) {
                    System.out.println("\n====== 推荐学习路径 (学习者: " + lid + ") ======");
                    PathRecommender.generateLearningPath(courses, records, lid, 3).show(false);
                }
            }

            // ==================== 阶段二 ====================
            System.out.println("\n========== 阶段二：相似度与协同推荐 ==========");

            System.out.println("====== 学习者相似度矩阵 ======");
            LearnerSimilarity.computeAllSimilarities(records).show(50, false);

            if (learnerId != null) {
                System.out.println("====== 相似学习者 (学习者: " + learnerId + ") ======");
                LearnerSimilarity.findSimilarLearners(records, learnerId, 5).show(false);

                System.out.println("====== 协同过滤推荐课程 (学习者: " + learnerId + ") ======");
                CollaborativeRecommender.recommendBySimilarity(
                        records, courses, learnerId, 3, 5).show(false);
            }

            System.out.println("====== 热门技能排行 ======");
            CollaborativeRecommender.discoverPopularSkills(records, skills, 10).show(false);

            // ==================== 阶段三 ====================
            System.out.println("\n========== 阶段三：效率分析与衰退预测 ==========");

            System.out.println("====== 学习效率分析 ======");
            Dataset<Row> efficiency = LearningEfficiency.computeSkillEfficiency(records, skills);
            if (learnerId != null) {
                efficiency.filter(col("learner_id").equalTo(learnerId)).show(50, false);
            } else {
                efficiency.show(50, false);
            }

            System.out.println("====== 按难度等级效率对比 ======");
            LearningEfficiency.efficiencyByDifficulty(records, skills).show(50, false);

            System.out.println("====== 学习效率排行榜 ======");
            LearningEfficiency.getEfficiencyLeaderboard(records, skills).show(false);

            // 技能衰退预测（使用当前日期作为参考）
            String referenceDate = java.time.LocalDate.now().toString();
            System.out.println("====== 技能衰退预测 (参考日期: " + referenceDate + ") ======");
            Dataset<Row> decay = SkillDecayPredictor.predictSkillDecay(records, skills, referenceDate);
            if (learnerId != null) {
                decay.filter(col("learner_id").equalTo(learnerId)).show(50, false);
            } else {
                decay.show(50, false);
            }

            System.out.println("====== 需要复习的技能 ======");
            SkillDecayPredictor.identifyReviewNeeded(records, skills, referenceDate).show(50, false);

            if (learnerId != null) {
                System.out.println("====== 复习优先级 (学习者: " + learnerId + ") ======");
                SkillDecayPredictor.getReviewPriority(
                        records, skills, referenceDate, learnerId).show(false);
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
