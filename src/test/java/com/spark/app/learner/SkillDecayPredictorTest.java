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

class SkillDecayPredictorTest {

    private static SparkSession spark;

    @BeforeAll
    static void setUp() {
        spark = SparkSession.builder()
                .appName("SkillDecayPredictorTest")
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
                "S003,SQL基础,数据库,1",
                "S006,Spark基础,大数据,2"
        ));
        return file;
    }

    private Path createRecordsCsv(Path tempDir) throws IOException {
        Path file = tempDir.resolve("records.csv");
        // L001: S001 last studied 2023-03-01 (long ago → high decay)
        //       S003 last studied 2024-01-01 (recent → low decay)
        //       S006 scored 65, last studied 2023-06-01 (moderate decay, low base)
        Files.write(file, List.of(
                "record_id,learner_id,skill_id,score,completion_date,study_hours",
                "R001,L001,S001,90,2023-03-01,40",
                "R002,L001,S001,95,2023-06-01,15",
                "R003,L001,S003,88,2024-01-01,35",
                "R004,L001,S006,65,2023-06-01,55",
                "R005,L002,S001,80,2024-06-01,30"
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
    void testPredictSkillDecay(@TempDir Path tempDir) throws IOException {
        Dataset<Row> skills = loadSkills(createSkillsCsv(tempDir));
        Dataset<Row> records = loadRecords(createRecordsCsv(tempDir));

        // Reference date: 2024-07-01 (about 1 year after some records)
        Dataset<Row> decay = SkillDecayPredictor.predictSkillDecay(
                records, skills, "2024-07-01");

        // L001 has latest records for S001, S003, S006; L002 has S001 = 4 rows
        assertEquals(4, decay.count());

        // L001's S001: latest=95 (from 2023-06-01), days_since≈396
        // predicted ≈ 95 * e^(-0.002 * 396) ≈ 95 * 0.453 ≈ 43.0
        Row l001s001 = decay.filter("learner_id = 'L001' AND skill_id = 'S001'").first();
        assertEquals(95, l001s001.getInt(l001s001.fieldIndex("latest_score")));
        double predicted = l001s001.getDouble(l001s001.fieldIndex("predicted_score"));
        assertTrue(predicted < 95, "Predicted score should be less than latest score");
        assertTrue(predicted > 0, "Predicted score should be positive");

        // L001's S003: latest=88, last studied 2024-01-01 (182 days ago)
        // predicted ≈ 88 * e^(-0.002 * 182) ≈ 88 * 0.695 ≈ 61.2 → less decay
        Row l001s003 = decay.filter("learner_id = 'L001' AND skill_id = 'S003'").first();
        double predictedS003 = l001s003.getDouble(l001s003.fieldIndex("predicted_score"));
        assertTrue(predictedS003 > predicted,
                "More recent skill should have higher predicted score");
    }

    @Test
    void testPredictSkillDecayWithCustomRate(@TempDir Path tempDir) throws IOException {
        Dataset<Row> skills = loadSkills(createSkillsCsv(tempDir));
        Dataset<Row> records = loadRecords(createRecordsCsv(tempDir));

        // Higher decay rate → lower predicted scores
        Dataset<Row> highDecay = SkillDecayPredictor.predictSkillDecay(
                records, skills, "2024-07-01", 0.01);
        Dataset<Row> lowDecay = SkillDecayPredictor.predictSkillDecay(
                records, skills, "2024-07-01", 0.001);

        Row highRow = highDecay.filter("learner_id = 'L001' AND skill_id = 'S001'").first();
        Row lowRow = lowDecay.filter("learner_id = 'L001' AND skill_id = 'S001'").first();

        double highPredicted = highRow.getDouble(highRow.fieldIndex("predicted_score"));
        double lowPredicted = lowRow.getDouble(lowRow.fieldIndex("predicted_score"));

        assertTrue(highPredicted < lowPredicted,
                "Higher decay rate should produce lower predicted scores");
    }

    @Test
    void testIdentifyReviewNeeded(@TempDir Path tempDir) throws IOException {
        Dataset<Row> skills = loadSkills(createSkillsCsv(tempDir));
        Dataset<Row> records = loadRecords(createRecordsCsv(tempDir));

        // With threshold 70, skills with predicted_score < 70 should need review
        Dataset<Row> needReview = SkillDecayPredictor.identifyReviewNeeded(
                records, skills, "2024-07-01", 70);

        // L001's S001 and S006 should be below 70 (significant decay)
        long l001ReviewCount = needReview.filter("learner_id = 'L001'").count();
        assertTrue(l001ReviewCount >= 1,
                "L001 should have at least 1 skill needing review");
    }

    @Test
    void testGetReviewPriority(@TempDir Path tempDir) throws IOException {
        Dataset<Row> skills = loadSkills(createSkillsCsv(tempDir));
        Dataset<Row> records = loadRecords(createRecordsCsv(tempDir));

        Dataset<Row> priority = SkillDecayPredictor.getReviewPriority(
                records, skills, "2024-07-01", "L001");

        // L001 has 3 skills, all should appear in priority list
        assertEquals(3, priority.count());

        // Review priority should be ordered descending
        List<Row> rows = priority.collectAsList();
        double firstPriority = rows.get(0).getDouble(rows.get(0).fieldIndex("review_priority"));
        double lastPriority = rows.get(rows.size() - 1).getDouble(
                rows.get(rows.size() - 1).fieldIndex("review_priority"));
        assertTrue(firstPriority >= lastPriority,
                "Results should be ordered by review_priority descending");
    }
}
