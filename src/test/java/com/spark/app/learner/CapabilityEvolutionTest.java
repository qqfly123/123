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

class CapabilityEvolutionTest {

    private static SparkSession spark;

    @BeforeAll
    static void setUp() {
        spark = SparkSession.builder()
                .appName("CapabilityEvolutionTest")
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
                "S002,Python基础,编程语言,1"
        ));
        return file;
    }

    private Path createRecordsCsv(Path tempDir) throws IOException {
        Path file = tempDir.resolve("records.csv");
        Files.write(file, List.of(
                "record_id,learner_id,skill_id,score,completion_date,study_hours",
                "R001,L001,S001,70,2023-03-01,30",
                "R002,L001,S001,82,2023-06-01,20",
                "R003,L001,S001,90,2023-09-01,15",
                "R004,L001,S002,85,2023-05-10,25",
                "R005,L002,S001,88,2023-04-01,35"
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
    void testBuildEvolutionTimeline(@TempDir Path tempDir) throws IOException {
        Dataset<Row> skills = loadSkills(createSkillsCsv(tempDir));
        Dataset<Row> records = loadRecords(createRecordsCsv(tempDir));

        Dataset<Row> timeline = CapabilityEvolution.buildEvolutionTimeline(records, skills);

        // L001 has 3 records for S001, 1 for S002; L002 has 1 for S001 = 5 total
        assertEquals(5, timeline.count());

        // Check first record of L001's Java journey has score_change = 0 (no previous)
        Row first = timeline
                .filter("learner_id = 'L001' AND skill_id = 'S001' AND completion_date = '2023-03-01'")
                .first();
        assertEquals(0, first.getInt(first.fieldIndex("score_change")));

        // Check second record should have positive change (82-70=12)
        Row second = timeline
                .filter("learner_id = 'L001' AND skill_id = 'S001' AND completion_date = '2023-06-01'")
                .first();
        assertEquals(12, second.getInt(second.fieldIndex("score_change")));
    }

    @Test
    void testAnalyzeProgressTrend(@TempDir Path tempDir) throws IOException {
        Dataset<Row> skills = loadSkills(createSkillsCsv(tempDir));
        Dataset<Row> records = loadRecords(createRecordsCsv(tempDir));

        Dataset<Row> trends = CapabilityEvolution.analyzeProgressTrend(records, skills);

        // L001: S001 (3 attempts, improving), S002 (1 attempt, stable)
        // L002: S001 (1 attempt, stable)
        assertEquals(3, trends.count());

        // L001's Java should show improvement trend
        Row javaTrend = trends
                .filter("learner_id = 'L001' AND skill_id = 'S001'")
                .first();
        assertEquals(70, javaTrend.getInt(javaTrend.fieldIndex("first_score")));
        assertEquals(90, javaTrend.getInt(javaTrend.fieldIndex("last_score")));
        assertEquals(20, javaTrend.getInt(javaTrend.fieldIndex("score_improvement")));
        assertEquals("提升", javaTrend.getString(javaTrend.fieldIndex("trend")));
        assertEquals(3L, javaTrend.getLong(javaTrend.fieldIndex("attempts")));

        // L002's Java has single attempt, should be stable
        Row l002Trend = trends
                .filter("learner_id = 'L002' AND skill_id = 'S001'")
                .first();
        assertEquals("稳定", l002Trend.getString(l002Trend.fieldIndex("trend")));
    }

    @Test
    void testGetMonthlyActivity(@TempDir Path tempDir) throws IOException {
        Dataset<Row> records = loadRecords(createRecordsCsv(tempDir));

        Dataset<Row> monthly = CapabilityEvolution.getMonthlyActivity(records);

        // L001: 2023-03(1), 2023-05(1), 2023-06(1), 2023-09(1)
        // L002: 2023-04(1)
        assertEquals(5, monthly.count());

        Row l001March = monthly
                .filter("learner_id = 'L001' AND month = '2023-03'")
                .first();
        assertEquals(1L, l001March.getLong(l001March.fieldIndex("courses_completed")));
        assertEquals(30, l001March.getLong(l001March.fieldIndex("monthly_hours")));
    }
}
