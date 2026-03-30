package com.spark.app.web.config;

import org.apache.spark.sql.SparkSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spark 会话配置。
 */
@Configuration
public class SparkConfig {

    @Bean(destroyMethod = "stop")
    public SparkSession sparkSession() {
        return SparkSession.builder()
                .appName("LearnerAnalysisWeb")
                .master("local[*]")
                .config("spark.ui.enabled", "false")
                .config("spark.sql.shuffle.partitions", "2")
                .getOrCreate();
    }
}
