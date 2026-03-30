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

class LearnerSimilarityTest {

    private static SparkSession spark;

    @BeforeAll
    static void setUp() {
        spark = SparkSession.builder()
                .appName("LearnerSimilarityTest")
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
        // L001 和 L002 都学了 S001 和 S005，且得分相近 → 高相似度
        // L003 只学了 S002，与 L001/L002 无共同技能 → 无法比较
        // L004 学了 S001 和 S005，但得分差异大 → 中等相似度
        Files.write(file, List.of(
                "record_id,learner_id,skill_id,score,completion_date,study_hours",
                "R001,L001,S001,90,2023-03-01,40",
                "R002,L001,S005,85,2023-07-10,45",
                "R003,L002,S001,88,2023-04-01,38",
                "R004,L002,S005,82,2023-08-01,42",
                "R005,L003,S002,95,2023-05-10,30",
                "R006,L004,S001,70,2023-06-01,50",
                "R007,L004,S005,60,2023-09-01,55"
        ));
        return file;
    }

    private Dataset<Row> loadRecords(Path file) {
        return spark.read().option("header", "true")
                .schema(SchemaDefinitions.getLearningRecordSchema()).csv(file.toString());
    }

    @Test
    void testBuildScoreMatrix(@TempDir Path tempDir) throws IOException {
        Dataset<Row> records = loadRecords(createRecordsCsv(tempDir));

        Dataset<Row> matrix = LearnerSimilarity.buildScoreMatrix(records);

        // 4 learners with distinct skill records: L001(2), L002(2), L003(1), L004(2) = 7
        assertEquals(7, matrix.count());

        // Verify L001's scores
        Row l001s001 = matrix.filter("learner_id = 'L001' AND skill_id = 'S001'").first();
        assertEquals(90, l001s001.getInt(l001s001.fieldIndex("score")));
    }

    @Test
    void testComputeAllSimilarities(@TempDir Path tempDir) throws IOException {
        Dataset<Row> records = loadRecords(createRecordsCsv(tempDir));

        Dataset<Row> similarities = LearnerSimilarity.computeAllSimilarities(records);

        // Pairs with common skills: (L001,L002), (L001,L004), (L002,L004) = 3 pairs
        // L003 only has S002, no overlap with others
        assertEquals(3, similarities.count());

        // L001 and L002 should have highest similarity (scores are closest)
        Row top = similarities.first();
        assertEquals("L001", top.getString(top.fieldIndex("learner_a")));
        assertEquals("L002", top.getString(top.fieldIndex("learner_b")));
        assertEquals(2L, top.getLong(top.fieldIndex("common_skills")));

        // Similarity should be close to 1.0 (very similar profiles)
        double sim = top.getDecimal(top.fieldIndex("similarity")).doubleValue();
        assertTrue(sim > 0.99, "L001 and L002 should have very high similarity, got: " + sim);
    }

    @Test
    void testFindSimilarLearners(@TempDir Path tempDir) throws IOException {
        Dataset<Row> records = loadRecords(createRecordsCsv(tempDir));

        Dataset<Row> similar = LearnerSimilarity.findSimilarLearners(records, "L001", 2);

        // L001 should find L002 and L004 as similar (both share S001, S005)
        assertEquals(2, similar.count());

        // L002 should be most similar
        Row first = similar.first();
        assertEquals("L002", first.getString(first.fieldIndex("similar_learner")));
    }

    @Test
    void testFindSimilarLearnersNoMatch(@TempDir Path tempDir) throws IOException {
        Dataset<Row> records = loadRecords(createRecordsCsv(tempDir));

        // L003 has no common skills with anyone
        Dataset<Row> similar = LearnerSimilarity.findSimilarLearners(records, "L003", 5);
        assertEquals(0, similar.count());
    }
}
