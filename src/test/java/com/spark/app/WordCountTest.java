package com.spark.app;

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

import static org.junit.jupiter.api.Assertions.*;

class WordCountTest {

    private static SparkSession spark;

    @BeforeAll
    static void setUp() {
        spark = SparkSession.builder()
                .appName("WordCountTest")
                .master("local[*]")
                .getOrCreate();
    }

    @AfterAll
    static void tearDown() {
        if (spark != null) {
            spark.stop();
        }
    }

    @Test
    void testCountWords(@TempDir Path tempDir) throws IOException {
        Path inputFile = tempDir.resolve("test_input.txt");
        Files.writeString(inputFile, "hello world\nhello spark\nspark is great\nhello world spark");

        Dataset<Row> result = WordCount.countWords(spark, inputFile.toString());

        assertEquals(5, result.count(), "应该有5个不同的单词");

        Row firstRow = result.first();
        String topWord = firstRow.getString(firstRow.fieldIndex("word"));
        long topCount = firstRow.getLong(firstRow.fieldIndex("count"));

        // "hello" 和 "spark" 各出现3次，排在最前面
        assertTrue(topCount == 3, "出现次数最多的单词应该出现3次");
        assertTrue("hello".equals(topWord) || "spark".equals(topWord),
                "出现次数最多的单词应该是 hello 或 spark");
    }

    @Test
    void testCountWordsEmptyFile(@TempDir Path tempDir) throws IOException {
        Path inputFile = tempDir.resolve("empty.txt");
        Files.writeString(inputFile, "");

        Dataset<Row> result = WordCount.countWords(spark, inputFile.toString());
        assertEquals(0, result.count(), "空文件应该返回0个单词");
    }

    @Test
    void testCountWordsSingleWord(@TempDir Path tempDir) throws IOException {
        Path inputFile = tempDir.resolve("single.txt");
        Files.writeString(inputFile, "spark");

        Dataset<Row> result = WordCount.countWords(spark, inputFile.toString());
        assertEquals(1, result.count(), "应该只有1个单词");

        Row row = result.first();
        assertEquals("spark", row.getString(row.fieldIndex("word")));
        assertEquals(1L, row.getLong(row.fieldIndex("count")));
    }
}
