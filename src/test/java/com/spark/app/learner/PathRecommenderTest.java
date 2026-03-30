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

class PathRecommenderTest {

    private static SparkSession spark;

    @BeforeAll
    static void setUp() {
        spark = SparkSession.builder()
                .appName("PathRecommenderTest")
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
                "S004,Java高级,编程语言,2",
                "S006,Spark基础,大数据,2",
                "S007,机器学习基础,人工智能,2"
        ));
        return file;
    }

    private Path createCoursesCsv(Path tempDir) throws IOException {
        Path file = tempDir.resolve("courses.csv");
        Files.write(file, List.of(
                "course_id,course_name,skill_id,difficulty_level,prerequisites",
                "C001,Java编程入门,S001,1,",
                "C002,Python编程入门,S002,1,",
                "C003,SQL数据库入门,S003,1,",
                "C004,Java并发与设计模式,S004,2,S001",
                "C006,Spark核心技术,S006,2,S001;S003",
                "C007,机器学习实战,S007,2,S002"
        ));
        return file;
    }

    private Path createRecordsCsv(Path tempDir) throws IOException {
        // L001 has learned S001 and S003
        Path file = tempDir.resolve("records.csv");
        Files.write(file, List.of(
                "record_id,learner_id,skill_id,score,completion_date,study_hours",
                "R001,L001,S001,90,2023-03-01,40",
                "R002,L001,S003,85,2023-04-15,35"
        ));
        return file;
    }

    private Dataset<Row> loadSkills(Path file) {
        return spark.read().option("header", "true")
                .schema(SchemaDefinitions.getSkillSchema()).csv(file.toString());
    }

    private Dataset<Row> loadCourses(Path file) {
        return spark.read().option("header", "true")
                .schema(SchemaDefinitions.getCourseSchema()).csv(file.toString());
    }

    private Dataset<Row> loadRecords(Path file) {
        return spark.read().option("header", "true")
                .schema(SchemaDefinitions.getLearningRecordSchema()).csv(file.toString());
    }

    @Test
    void testIdentifySkillGaps(@TempDir Path tempDir) throws IOException {
        Dataset<Row> skills = loadSkills(createSkillsCsv(tempDir));
        Dataset<Row> records = loadRecords(createRecordsCsv(tempDir));

        Dataset<Row> gaps = PathRecommender.identifySkillGaps(skills, records, "L001");

        // L001 has S001 and S003, should be missing S002, S004, S006, S007
        assertEquals(4, gaps.count());

        // Verify missing skills are ordered by difficulty
        List<Row> gapList = gaps.collectAsList();
        assertEquals("S002", gapList.get(0).getString(gapList.get(0).fieldIndex("skill_id")));
    }

    @Test
    void testRecommendCourses(@TempDir Path tempDir) throws IOException {
        Dataset<Row> courses = loadCourses(createCoursesCsv(tempDir));
        Dataset<Row> records = loadRecords(createRecordsCsv(tempDir));

        Dataset<Row> recommended = PathRecommender.recommendCourses(courses, records, "L001");

        // L001 has S001 and S003 done, so remaining courses: C002(S002), C004(S004), C006(S006), C007(S007)
        assertEquals(4, recommended.count());

        // C004 needs S001 (met), C006 needs S001;S003 (both met) -> prereqs_met = true
        // C002 has no prereqs -> prereqs_met = true
        // C007 needs S002 (not met) -> prereqs_met = false
        Row sparkCourse = recommended.filter("course_id = 'C006'").first();
        assertTrue(sparkCourse.getBoolean(sparkCourse.fieldIndex("prereqs_met")));

        Row mlCourse = recommended.filter("course_id = 'C007'").first();
        assertFalse(mlCourse.getBoolean(mlCourse.fieldIndex("prereqs_met")));
    }

    @Test
    void testGenerateLearningPath(@TempDir Path tempDir) throws IOException {
        Dataset<Row> courses = loadCourses(createCoursesCsv(tempDir));
        Dataset<Row> records = loadRecords(createRecordsCsv(tempDir));

        Dataset<Row> path = PathRecommender.generateLearningPath(courses, records, "L001", 5);

        // Only courses with prereqs_met=true: C002(no prereqs), C004(S001 met), C006(S001;S003 met)
        assertEquals(3, path.count());

        // Should not contain C007 (needs S002)
        assertEquals(0, path.filter("course_id = 'C007'").count());
    }

    @Test
    void testGenerateLearningPathLimit(@TempDir Path tempDir) throws IOException {
        Dataset<Row> courses = loadCourses(createCoursesCsv(tempDir));
        Dataset<Row> records = loadRecords(createRecordsCsv(tempDir));

        Dataset<Row> path = PathRecommender.generateLearningPath(courses, records, "L001", 2);

        // Should return at most 2 courses
        assertEquals(2, path.count());
    }
}
