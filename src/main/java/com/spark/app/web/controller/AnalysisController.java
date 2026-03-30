package com.spark.app.web.controller;

import com.spark.app.web.service.AnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API 控制器 - 提供终身学习者分析系统的所有 API 端点。
 *
 * <p>API 分为六大模块，对应三个分析阶段：
 * <ul>
 *   <li>阶段一：能力画像、能力演进、路径推荐</li>
 *   <li>阶段二：学习者相似度、协同推荐</li>
 *   <li>阶段三：学习效率、技能衰退预测</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api")
public class AnalysisController {

    private final AnalysisService service;

    public AnalysisController(AnalysisService service) {
        this.service = service;
    }

    // ========== 概览 ==========

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getOverview() {
        return ResponseEntity.ok(service.getOverview());
    }

    @GetMapping("/learners")
    public ResponseEntity<List<Map<String, Object>>> getLearners() {
        return ResponseEntity.ok(service.getLearnerList());
    }

    // ========== 阶段一：能力画像 ==========

    @GetMapping("/profile/skills")
    public ResponseEntity<List<Map<String, Object>>> getSkillProfile(
            @RequestParam(required = false) String learnerId) {
        return ResponseEntity.ok(service.getSkillProfile(learnerId));
    }

    @GetMapping("/profile/categories")
    public ResponseEntity<List<Map<String, Object>>> getCategoryDistribution(
            @RequestParam(required = false) String learnerId) {
        return ResponseEntity.ok(service.getCategoryDistribution(learnerId));
    }

    @GetMapping("/profile/ratings")
    public ResponseEntity<List<Map<String, Object>>> getOverallRating() {
        return ResponseEntity.ok(service.getOverallRating());
    }

    // ========== 阶段一：能力演进 ==========

    @GetMapping("/evolution/timeline")
    public ResponseEntity<List<Map<String, Object>>> getEvolutionTimeline(
            @RequestParam(required = false) String learnerId) {
        return ResponseEntity.ok(service.getEvolutionTimeline(learnerId));
    }

    @GetMapping("/evolution/trend")
    public ResponseEntity<List<Map<String, Object>>> getProgressTrend(
            @RequestParam(required = false) String learnerId) {
        return ResponseEntity.ok(service.getProgressTrend(learnerId));
    }

    @GetMapping("/evolution/monthly")
    public ResponseEntity<List<Map<String, Object>>> getMonthlyActivity(
            @RequestParam(required = false) String learnerId) {
        return ResponseEntity.ok(service.getMonthlyActivity(learnerId));
    }

    // ========== 阶段一：路径推荐 ==========

    @GetMapping("/path/gaps/{learnerId}")
    public ResponseEntity<List<Map<String, Object>>> getSkillGaps(
            @PathVariable String learnerId) {
        return ResponseEntity.ok(service.getSkillGaps(learnerId));
    }

    @GetMapping("/path/courses/{learnerId}")
    public ResponseEntity<List<Map<String, Object>>> getRecommendedCourses(
            @PathVariable String learnerId) {
        return ResponseEntity.ok(service.getRecommendedCourses(learnerId));
    }

    @GetMapping("/path/learning/{learnerId}")
    public ResponseEntity<List<Map<String, Object>>> getLearningPath(
            @PathVariable String learnerId,
            @RequestParam(defaultValue = "5") int maxCourses) {
        return ResponseEntity.ok(service.getLearningPath(learnerId, maxCourses));
    }

    // ========== 阶段二：相似度 ==========

    @GetMapping("/similarity/all")
    public ResponseEntity<List<Map<String, Object>>> getAllSimilarities() {
        return ResponseEntity.ok(service.getAllSimilarities());
    }

    @GetMapping("/similarity/{learnerId}")
    public ResponseEntity<List<Map<String, Object>>> getSimilarLearners(
            @PathVariable String learnerId,
            @RequestParam(defaultValue = "5") int topN) {
        return ResponseEntity.ok(service.getSimilarLearners(learnerId, topN));
    }

    // ========== 阶段二：协同推荐 ==========

    @GetMapping("/collaborative/{learnerId}")
    public ResponseEntity<List<Map<String, Object>>> getCollaborativeRecommendations(
            @PathVariable String learnerId,
            @RequestParam(defaultValue = "3") int topK,
            @RequestParam(defaultValue = "5") int maxResults) {
        return ResponseEntity.ok(service.getCollaborativeRecommendations(learnerId, topK, maxResults));
    }

    @GetMapping("/popular-skills")
    public ResponseEntity<List<Map<String, Object>>> getPopularSkills(
            @RequestParam(defaultValue = "10") int topN) {
        return ResponseEntity.ok(service.getPopularSkills(topN));
    }

    // ========== 阶段三：效率 ==========

    @GetMapping("/efficiency/skills")
    public ResponseEntity<List<Map<String, Object>>> getSkillEfficiency(
            @RequestParam(required = false) String learnerId) {
        return ResponseEntity.ok(service.getSkillEfficiency(learnerId));
    }

    @GetMapping("/efficiency/difficulty")
    public ResponseEntity<List<Map<String, Object>>> getEfficiencyByDifficulty(
            @RequestParam(required = false) String learnerId) {
        return ResponseEntity.ok(service.getEfficiencyByDifficulty(learnerId));
    }

    @GetMapping("/efficiency/leaderboard")
    public ResponseEntity<List<Map<String, Object>>> getEfficiencyLeaderboard() {
        return ResponseEntity.ok(service.getEfficiencyLeaderboard());
    }

    // ========== 阶段三：衰退预测 ==========

    @GetMapping("/decay/predict")
    public ResponseEntity<List<Map<String, Object>>> getSkillDecay(
            @RequestParam(required = false) String learnerId) {
        return ResponseEntity.ok(service.getSkillDecay(learnerId));
    }

    @GetMapping("/decay/review")
    public ResponseEntity<List<Map<String, Object>>> getReviewNeeded(
            @RequestParam(required = false) String learnerId) {
        return ResponseEntity.ok(service.getReviewNeeded(learnerId));
    }

    @GetMapping("/decay/priority/{learnerId}")
    public ResponseEntity<List<Map<String, Object>>> getReviewPriority(
            @PathVariable String learnerId) {
        return ResponseEntity.ok(service.getReviewPriority(learnerId));
    }
}
