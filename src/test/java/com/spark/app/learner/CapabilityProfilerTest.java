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

class CapabilityProfilerTest {

    private static SparkSession spark;

    @BeforeAll
    static void setUp() {
        spark = SparkSession.builder()
                .appName("CapabilityProfilerTest")
                .master("local[*]")
                .getOrCreate();
    }

    @AfterAll
    static void tearDown() {
        if (spark != null) {
            spark.stop();
        }
    }

    private Path createSkillsCsv(Path tempDir) throws IOException {
        Path file = tempDir.resolve("skills.csv");
        Files.write(file, List.of(
                "skill_id,skill_name,category,difficulty_level",
                "S001,Java基础,编程语言,1",
                "S002,Python基础,编程语言,1",
                "S003,SQL基础,数据库,1",
                "S006,Spark基础,大数据,2"
        ));
        return file;
    }

    private Path createRecordsCsv(Path tempDir) throws IOException {
        Path file = tempDir.resolve("records.csv");
        Files.write(file, List.of(
                "record_id,learner_id,skill_id,score,completion_date,study_hours",
                "R001,L001,S001,85,2023-03-01,40",
                "R002,L001,S003,88,2023-04-15,35",
                "R003,L001,S001,92,2023-06-01,15",
                "R004,L001,S006,90,2023-09-01,60",
                "R005,L002,S002,95,2023-05-10,30",
                "R006,L002,S001,78,2023-06-20,40"
        ));
        return file;
    }

    private Dataset<Row> loadSkills(Path file) {
        return spark.read().option("header", "true")
                .schema(SchemaDefinitions.getSkillSchema()).csv(file.toString());
    }

    private Dataset<Row> loadRecords(Path file) {
        return spark.read().option("header", "true")
                .schema(SchemaDefinitions.getLearningRecordSchema()).csv(file.toString());
    }

    @Test
    void testBuildSkillProfile(@TempDir Path tempDir) throws IOException {
        Dataset<Row> skills = loadSkills(createSkillsCsv(tempDir));
        Dataset<Row> records = loadRecords(createRecordsCsv(tempDir));

        Dataset<Row> profile = CapabilityProfiler.buildSkillProfile(records, skills);

        // L001 has S001(x2), S003(x1), S006(x1); L002 has S002(x1), S001(x1)
        assertEquals(5, profile.count());

        // Check L001's Java基础 profile (2 attempts)
        Row javaProfile = profile
                .filter("learner_id = 'L001' AND skill_id = 'S001'")
                .first();
        assertEquals(92, javaProfile.getInt(javaProfile.fieldIndex("latest_score")));
        assertEquals(92, javaProfile.getInt(javaProfile.fieldIndex("max_score")));
        assertEquals(2L, javaProfile.getLong(javaProfile.fieldIndex("attempts")));
    }

    @Test
    void testGetCategoryDistribution(@TempDir Path tempDir) throws IOException {
        Dataset<Row> skills = loadSkills(createSkillsCsv(tempDir));
        Dataset<Row> records = loadRecords(createRecordsCsv(tempDir));
        Dataset<Row> profile = CapabilityProfiler.buildSkillProfile(records, skills);

        Dataset<Row> distribution = CapabilityProfiler.getCategoryDistribution(profile);

        // L001 has skills in 编程语言, 数据库, 大数据
        // L002 has skills in 编程语言
        long l001Categories = distribution.filter("learner_id = 'L001'").count();
        assertEquals(3, l001Categories);

        long l002Categories = distribution.filter("learner_id = 'L002'").count();
        assertEquals(1, l002Categories);
    }

    @Test
    void testGetOverallRating(@TempDir Path tempDir) throws IOException {
        Dataset<Row> skills = loadSkills(createSkillsCsv(tempDir));
        Dataset<Row> records = loadRecords(createRecordsCsv(tempDir));
        Dataset<Row> profile = CapabilityProfiler.buildSkillProfile(records, skills);

        Dataset<Row> ratings = CapabilityProfiler.getOverallRating(profile);

        assertEquals(2, ratings.count());

        // Both learners should have a rating column
        Row l001Rating = ratings.filter("learner_id = 'L001'").first();
        assertNotNull(l001Rating.getString(l001Rating.fieldIndex("rating")));
        assertTrue(l001Rating.getLong(l001Rating.fieldIndex("total_skills")) > 0);
    }
}
