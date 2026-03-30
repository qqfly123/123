package com.spark.app.learner;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CollaborativeRecommenderTest {

    private static SparkSession spark;

    @BeforeAll
    static void setUp() {
        spark = SparkSession.builder()
                .appName("CollaborativeRecommenderTest")
                .master("local[*]")
                .getOrCreate();
    }

    @AfterAll
    static void tearDown() {
        if (spark != null) {
            spark.stop();
        }
    }

    private Path createRecordsCsv(Path tempDir) throws IOException {
        Path file = tempDir.resolve("records.csv");
        // L001 and L002 share S001 and S005 (similar learners)
        // L002 also learned S007 (which L001 hasn't)
        // L003 learned S001 and S007 (also knows S007)
        Files.write(file, List.of(
                "record_id,learner_id,skill_id,score,completion_date,study_hours",
                "R001,L001,S001,90,2023-03-01,40",
                "R002,L001,S005,85,2023-07-10,45",
                "R003,L002,S001,88,2023-04-01,38",
                "R004,L002,S005,82,2023-08-01,42",
                "R005,L002,S007,78,2023-10-01,50",
                "R006,L003,S001,85,2023-05-01,36",
                "R007,L003,S007,92,2023-09-01,48"
        ));
        return file;
    }

    private Path createSkillsCsv(Path tempDir) throws IOException {
        Path file = tempDir.resolve("skills.csv");
        Files.write(file, List.of(
                "skill_id,skill_name,category,difficulty_level",
                "S001,Java基础,编程语言,1",
                "S005,数据结构与算法,计算机基础,2",
                "S007,机器学习基础,人工智能,2"
        ));
        return file;
    }

    private Path createCoursesCsv(Path tempDir) throws IOException {
        Path file = tempDir.resolve("courses.csv");
        Files.write(file, List.of(
                "course_id,course_name,skill_id,difficulty_level,prerequisites",
                "C001,Java编程入门,S001,1,",
                "C005,数据结构与算法精讲,S005,2,S001",
                "C007,机器学习实战,S007,2,S005"
        ));
        return file;
    }

    private Dataset<Row> loadRecords(Path file) {
        return spark.read().option("header", "true")
                .schema(SchemaDefinitions.getLearningRecordSchema()).csv(file.toString());
    }

    private Dataset<Row> loadSkills(Path file) {
        return spark.read().option("header", "true")
                .schema(SchemaDefinitions.getSkillSchema()).csv(file.toString());
    }

    private Dataset<Row> loadCourses(Path file) {
        return spark.read().option("header", "true")
                .schema(SchemaDefinitions.getCourseSchema()).csv(file.toString());
    }

    @Test
    void testRecommendBySimilarity(@TempDir Path tempDir) throws IOException {
        Dataset<Row> records = loadRecords(createRecordsCsv(tempDir));
        Dataset<Row> courses = loadCourses(createCoursesCsv(tempDir));

        // L001 knows S001, S005 → should be recommended S007 (learned by similar L002 and L003)
        Dataset<Row> recs = CollaborativeRecommender.recommendBySimilarity(
                records, courses, "L001", 3, 5);

        assertEquals(1, recs.count());
        Row rec = recs.first();
        assertEquals("C007", rec.getString(rec.fieldIndex("course_id")));
        // 2 similar learners recommend this skill
        assertEquals(2L, rec.getLong(rec.fieldIndex("recommended_by_count")));
    }

    @Test
    void testRecommendBySimilarityNoRecommendation(@TempDir Path tempDir) throws IOException {
        Dataset<Row> records = loadRecords(createRecordsCsv(tempDir));
        Dataset<Row> courses = loadCourses(createCoursesCsv(tempDir));

        // L002 knows S001, S005, S007 → similar learners (L001, L003) don't have skills L002 doesn't know
        Dataset<Row> recs = CollaborativeRecommender.recommendBySimilarity(
                records, courses, "L002", 3, 5);

        assertEquals(0, recs.count());
    }

    @Test
    void testDiscoverPopularSkills(@TempDir Path tempDir) throws IOException {
        Dataset<Row> records = loadRecords(createRecordsCsv(tempDir));
        Dataset<Row> skills = loadSkills(createSkillsCsv(tempDir));

        Dataset<Row> popular = CollaborativeRecommender.discoverPopularSkills(records, skills, 3);

        assertEquals(3, popular.count());

        // S001 is learned by 3 learners → most popular
        Row top = popular.first();
        assertEquals("S001", top.getString(top.fieldIndex("skill_id")));
        assertEquals(3L, top.getLong(top.fieldIndex("learner_count")));
    }

    @Test
    void testDiscoverPopularSkillsWithLimit(@TempDir Path tempDir) throws IOException {
        Dataset<Row> records = loadRecords(createRecordsCsv(tempDir));
        Dataset<Row> skills = loadSkills(createSkillsCsv(tempDir));

        Dataset<Row> popular = CollaborativeRecommender.discoverPopularSkills(records, skills, 1);
        assertEquals(1, popular.count());
    }
}
