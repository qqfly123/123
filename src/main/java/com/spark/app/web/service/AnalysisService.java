package com.spark.app.web.service;

import com.spark.app.learner.*;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 分析服务层 - 封装 Spark 分析模块，转换为 JSON 友好的 Map/List 结构。
 */
@Service
public class AnalysisService {

    private final SparkSession spark;

    @Value("${spark.data.dir}")
    private String dataDir;

    private Dataset<Row> learners;
    private Dataset<Row> skills;
    private Dataset<Row> courses;
    private Dataset<Row> records;

    public AnalysisService(SparkSession spark) {
        this.spark = spark;
    }

    @PostConstruct
    public void loadData() {
        learners = loadCsv(dataDir + "/learners.csv", SchemaDefinitions.getLearnerSchema());
        skills = loadCsv(dataDir + "/skills.csv", SchemaDefinitions.getSkillSchema());
        courses = loadCsv(dataDir + "/courses.csv", SchemaDefinitions.getCourseSchema());
        records = loadCsv(dataDir + "/learning_records.csv", SchemaDefinitions.getLearningRecordSchema());
    }

    /**
     * 重新加载数据。
     */
    public void reloadData() {
        loadData();
    }

    private Dataset<Row> loadCsv(String path, org.apache.spark.sql.types.StructType schema) {
        return spark.read().option("header", "true").schema(schema).csv(path);
    }

    // ========== 通用转换 ==========

    private List<Map<String, Object>> toListOfMaps(Dataset<Row> ds) {
        String[] columns = ds.columns();
        return ds.collectAsList().stream().map(row -> {
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < columns.length; i++) {
                map.put(columns[i], row.isNullAt(i) ? null : row.get(i));
            }
            return map;
        }).collect(Collectors.toList());
    }

    // ========== 概览 ==========

    public Map<String, Object> getOverview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("learnerCount", learners.count());
        result.put("skillCount", skills.count());
        result.put("courseCount", courses.count());
        result.put("recordCount", records.count());
        result.put("learners", toListOfMaps(learners));
        result.put("skills", toListOfMaps(skills));
        return result;
    }

    // ========== 阶段一：能力画像 ==========

    public List<Map<String, Object>> getSkillProfile(String learnerId) {
        Dataset<Row> profile = CapabilityProfiler.buildSkillProfile(records, skills);
        if (learnerId != null && !learnerId.isEmpty()) {
            profile = profile.filter(
                    org.apache.spark.sql.functions.col("learner_id").equalTo(learnerId));
        }
        return toListOfMaps(profile);
    }

    public List<Map<String, Object>> getCategoryDistribution(String learnerId) {
        Dataset<Row> profile = CapabilityProfiler.buildSkillProfile(records, skills);
        Dataset<Row> dist = CapabilityProfiler.getCategoryDistribution(profile);
        if (learnerId != null && !learnerId.isEmpty()) {
            dist = dist.filter(
                    org.apache.spark.sql.functions.col("learner_id").equalTo(learnerId));
        }
        return toListOfMaps(dist);
    }

    public List<Map<String, Object>> getOverallRating() {
        Dataset<Row> profile = CapabilityProfiler.buildSkillProfile(records, skills);
        return toListOfMaps(CapabilityProfiler.getOverallRating(profile));
    }

    // ========== 阶段一：能力演进 ==========

    public List<Map<String, Object>> getEvolutionTimeline(String learnerId) {
        Dataset<Row> timeline = CapabilityEvolution.buildEvolutionTimeline(records, skills);
        if (learnerId != null && !learnerId.isEmpty()) {
            timeline = timeline.filter(
                    org.apache.spark.sql.functions.col("learner_id").equalTo(learnerId));
        }
        return toListOfMaps(timeline);
    }

    public List<Map<String, Object>> getProgressTrend(String learnerId) {
        Dataset<Row> trend = CapabilityEvolution.analyzeProgressTrend(records, skills);
        if (learnerId != null && !learnerId.isEmpty()) {
            trend = trend.filter(
                    org.apache.spark.sql.functions.col("learner_id").equalTo(learnerId));
        }
        return toListOfMaps(trend);
    }

    public List<Map<String, Object>> getMonthlyActivity(String learnerId) {
        Dataset<Row> monthly = CapabilityEvolution.getMonthlyActivity(records);
        if (learnerId != null && !learnerId.isEmpty()) {
            monthly = monthly.filter(
                    org.apache.spark.sql.functions.col("learner_id").equalTo(learnerId));
        }
        return toListOfMaps(monthly);
    }

    // ========== 阶段一：路径推荐 ==========

    public List<Map<String, Object>> getSkillGaps(String learnerId) {
        return toListOfMaps(PathRecommender.identifySkillGaps(skills, records, learnerId));
    }

    public List<Map<String, Object>> getRecommendedCourses(String learnerId) {
        return toListOfMaps(PathRecommender.recommendCourses(courses, records, learnerId));
    }

    public List<Map<String, Object>> getLearningPath(String learnerId, int maxCourses) {
        return toListOfMaps(
                PathRecommender.generateLearningPath(courses, records, learnerId, maxCourses));
    }

    // ========== 阶段二：相似度 ==========

    public List<Map<String, Object>> getAllSimilarities() {
        return toListOfMaps(LearnerSimilarity.computeAllSimilarities(records));
    }

    public List<Map<String, Object>> getSimilarLearners(String learnerId, int topN) {
        return toListOfMaps(LearnerSimilarity.findSimilarLearners(records, learnerId, topN));
    }

    // ========== 阶段二：协同推荐 ==========

    public List<Map<String, Object>> getCollaborativeRecommendations(
            String learnerId, int topK, int maxResults) {
        return toListOfMaps(CollaborativeRecommender.recommendBySimilarity(
                records, courses, learnerId, topK, maxResults));
    }

    public List<Map<String, Object>> getPopularSkills(int topN) {
        return toListOfMaps(CollaborativeRecommender.discoverPopularSkills(records, skills, topN));
    }

    // ========== 阶段三：效率 ==========

    public List<Map<String, Object>> getSkillEfficiency(String learnerId) {
        Dataset<Row> eff = LearningEfficiency.computeSkillEfficiency(records, skills);
        if (learnerId != null && !learnerId.isEmpty()) {
            eff = eff.filter(
                    org.apache.spark.sql.functions.col("learner_id").equalTo(learnerId));
        }
        return toListOfMaps(eff);
    }

    public List<Map<String, Object>> getEfficiencyByDifficulty(String learnerId) {
        Dataset<Row> eff = LearningEfficiency.efficiencyByDifficulty(records, skills);
        if (learnerId != null && !learnerId.isEmpty()) {
            eff = eff.filter(
                    org.apache.spark.sql.functions.col("learner_id").equalTo(learnerId));
        }
        return toListOfMaps(eff);
    }

    public List<Map<String, Object>> getEfficiencyLeaderboard() {
        return toListOfMaps(LearningEfficiency.getEfficiencyLeaderboard(records, skills));
    }

    // ========== 阶段三：衰退预测 ==========

    public List<Map<String, Object>> getSkillDecay(String learnerId) {
        String refDate = LocalDate.now().toString();
        Dataset<Row> decay = SkillDecayPredictor.predictSkillDecay(records, skills, refDate);
        if (learnerId != null && !learnerId.isEmpty()) {
            decay = decay.filter(
                    org.apache.spark.sql.functions.col("learner_id").equalTo(learnerId));
        }
        return toListOfMaps(decay);
    }

    public List<Map<String, Object>> getReviewNeeded(String learnerId) {
        String refDate = LocalDate.now().toString();
        Dataset<Row> review = SkillDecayPredictor.identifyReviewNeeded(records, skills, refDate);
        if (learnerId != null && !learnerId.isEmpty()) {
            review = review.filter(
                    org.apache.spark.sql.functions.col("learner_id").equalTo(learnerId));
        }
        return toListOfMaps(review);
    }

    public List<Map<String, Object>> getReviewPriority(String learnerId) {
        String refDate = LocalDate.now().toString();
        return toListOfMaps(
                SkillDecayPredictor.getReviewPriority(records, skills, refDate, learnerId));
    }

    // ========== 学习者列表 ==========

    public List<Map<String, Object>> getLearnerList() {
        return toListOfMaps(learners);
    }
}
