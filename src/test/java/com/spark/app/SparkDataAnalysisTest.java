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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SparkDataAnalysisTest {

    private static SparkSession spark;

    @BeforeAll
    static void setUp() {
        spark = SparkSession.builder()
                .appName("SparkDataAnalysisTest")
                .master("local[*]")
                .getOrCreate();
    }

    @AfterAll
    static void tearDown() {
        if (spark != null) {
            spark.stop();
        }
    }

    private Path createSampleCsv(Path tempDir) throws IOException {
        Path csvFile = tempDir.resolve("sales.csv");
        List<String> lines = List.of(
                "date,product,category,quantity,price",
                "2024-01-01,iPhone,电子产品,2,6999.0",
                "2024-01-01,iPad,电子产品,1,3999.0",
                "2024-01-02,T恤,服装,5,99.0",
                "2024-01-02,iPhone,电子产品,3,6999.0",
                "2024-01-03,运动鞋,服装,2,599.0"
        );
        Files.write(csvFile, lines);
        return csvFile;
    }

    @Test
    void testLoadData(@TempDir Path tempDir) throws IOException {
        Path csvFile = createSampleCsv(tempDir);
        Dataset<Row> data = SparkDataAnalysis.loadData(spark, csvFile.toString());

        assertEquals(5, data.count(), "应该有5条记录");
        assertArrayEquals(
                new String[]{"date", "product", "category", "quantity", "price"},
                data.columns(),
                "列名应该匹配"
        );
    }

    @Test
    void testAnalyzeSalesByCategory(@TempDir Path tempDir) throws IOException {
        Path csvFile = createSampleCsv(tempDir);
        Dataset<Row> data = SparkDataAnalysis.loadData(spark, csvFile.toString());
        Dataset<Row> result = SparkDataAnalysis.analyzeSalesByCategory(data);

        assertEquals(2, result.count(), "应该有2个类别");

        Row firstRow = result.first();
        assertEquals("电子产品", firstRow.getString(firstRow.fieldIndex("category")));
    }

    @Test
    void testAnalyzeSalesByDate(@TempDir Path tempDir) throws IOException {
        Path csvFile = createSampleCsv(tempDir);
        Dataset<Row> data = SparkDataAnalysis.loadData(spark, csvFile.toString());
        Dataset<Row> result = SparkDataAnalysis.analyzeSalesByDate(data);

        assertEquals(3, result.count(), "应该有3个不同日期");

        Row firstRow = result.first();
        assertEquals("2024-01-01", firstRow.getString(firstRow.fieldIndex("date")));
    }

    @Test
    void testGetTopProducts(@TempDir Path tempDir) throws IOException {
        Path csvFile = createSampleCsv(tempDir);
        Dataset<Row> data = SparkDataAnalysis.loadData(spark, csvFile.toString());
        Dataset<Row> result = SparkDataAnalysis.getTopProducts(data, 3);

        assertEquals(3, result.count(), "应该返回3个产品");

        Row firstRow = result.first();
        assertEquals("iPhone", firstRow.getString(firstRow.fieldIndex("product")),
                "销售额最高的产品应该是 iPhone");
    }
}
