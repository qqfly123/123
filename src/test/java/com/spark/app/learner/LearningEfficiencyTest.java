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

class LearningEfficiencyTest {

    private static SparkSession spark;

    @BeforeAll
    static void setUp() {
        spark = SparkSession.builder()
                .appName("LearningEfficiencyTest")
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
                "S006,Spark基础,大数据,2",
                "S008,Spark高级,大数据,3"
        ));
        return file;
    }

    private Path createRecordsCsv(Path tempDir) throws IOException {
        Path file = tempDir.resolve("records.csv");
        // L001: S001 high score (90) in 40hrs → eff=2.25; S006 high score in 60hrs → eff=1.50
        // L002: S001 score 70 in 50hrs → eff=1.40 (lower efficiency)
        Files.write(file, List.of(
                "record_id,learner_id,skill_id,score,completion_date,study_hours",
                "R001,L001,S001,85,2023-03-01,40",
                "R002,L001,S001,90,2023-06-01,10",
                "R003,L001,S006,90,2023-09-01,60",
                "R004,L002,S001,70,2023-04-01,50",
                "R005,L002,S008,60,2023-10-01,70"
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
    void testComputeSkillEfficiency(@TempDir Path tempDir) throws IOException {
        Dataset<Row> skills = loadSkills(createSkillsCsv(tempDir));
        Dataset<Row> records = loadRecords(createRecordsCsv(tempDir));

        Dataset<Row> efficiency = LearningEfficiency.computeSkillEfficiency(records, skills);

        // L001: S001(2 attempts), S006(1 attempt); L002: S001(1 attempt), S008(1 attempt) = 4 rows
        assertEquals(4, efficiency.count());

        // L001's S001: latest=90, total_hours=50, efficiency=90/50=1.80, attempts=2
        Row l001s001 = efficiency.filter("learner_id = 'L001' AND skill_id = 'S001'").first();
        assertEquals(90, l001s001.getInt(l001s001.fieldIndex("latest_score")));
        assertEquals(2L, l001s001.getLong(l001s001.fieldIndex("attempts")));

        // L001's S001: best_single_efficiency should be max(85/40, 90/10) = 9.00
        double bestEff = l001s001.getDecimal(l001s001.fieldIndex("best_single_efficiency")).doubleValue();
        assertEquals(9.0, bestEff, 0.01);
    }

    @Test
    void testEfficiencyByDifficulty(@TempDir Path tempDir) throws IOException {
        Dataset<Row> skills = loadSkills(createSkillsCsv(tempDir));
        Dataset<Row> records = loadRecords(createRecordsCsv(tempDir));

        Dataset<Row> byDiff = LearningEfficiency.efficiencyByDifficulty(records, skills);

        // L001: difficulty 1 (S001), difficulty 2 (S006) = 2 rows
        // L002: difficulty 1 (S001), difficulty 3 (S008) = 2 rows
        assertEquals(4, byDiff.count());

        // L001 difficulty 1 should have the S001 efficiency
        Row l001d1 = byDiff.filter("learner_id = 'L001' AND difficulty_level = 1").first();
        assertNotNull(l001d1.getDecimal(l001d1.fieldIndex("avg_efficiency")));
    }

    @Test
    void testGetEfficiencyLeaderboard(@TempDir Path tempDir) throws IOException {
        Dataset<Row> skills = loadSkills(createSkillsCsv(tempDir));
        Dataset<Row> records = loadRecords(createRecordsCsv(tempDir));

        Dataset<Row> leaderboard = LearningEfficiency.getEfficiencyLeaderboard(records, skills);

        assertEquals(2, leaderboard.count());

        // L001 should rank higher (better efficiency overall)
        Row first = leaderboard.first();
        assertEquals("L001", first.getString(first.fieldIndex("learner_id")));
        assertTrue(first.getDecimal(first.fieldIndex("overall_efficiency")).doubleValue() > 0);
    }
}
