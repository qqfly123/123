package com.spark.app.learner;

import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

/**
 * SchemaDefinitions - 定义终身学习系统中所有数据实体的 Schema。
 */
public final class SchemaDefinitions {

    private SchemaDefinitions() {
    }

    /** 学习者信息 Schema：learner_id, name, registration_date */
    public static StructType getLearnerSchema() {
        return new StructType(new StructField[]{
                DataTypes.createStructField("learner_id", DataTypes.StringType, false),
                DataTypes.createStructField("name", DataTypes.StringType, false),
                DataTypes.createStructField("registration_date", DataTypes.StringType, false)
        });
    }

    /** 技能信息 Schema：skill_id, skill_name, category, difficulty_level */
    public static StructType getSkillSchema() {
        return new StructType(new StructField[]{
                DataTypes.createStructField("skill_id", DataTypes.StringType, false),
                DataTypes.createStructField("skill_name", DataTypes.StringType, false),
                DataTypes.createStructField("category", DataTypes.StringType, false),
                DataTypes.createStructField("difficulty_level", DataTypes.IntegerType, false)
        });
    }

    /** 课程信息 Schema：course_id, course_name, skill_id, difficulty_level, prerequisites */
    public static StructType getCourseSchema() {
        return new StructType(new StructField[]{
                DataTypes.createStructField("course_id", DataTypes.StringType, false),
                DataTypes.createStructField("course_name", DataTypes.StringType, false),
                DataTypes.createStructField("skill_id", DataTypes.StringType, false),
                DataTypes.createStructField("difficulty_level", DataTypes.IntegerType, false),
                DataTypes.createStructField("prerequisites", DataTypes.StringType, true)
        });
    }

    /** 学习记录 Schema：record_id, learner_id, skill_id, score, completion_date, study_hours */
    public static StructType getLearningRecordSchema() {
        return new StructType(new StructField[]{
                DataTypes.createStructField("record_id", DataTypes.StringType, false),
                DataTypes.createStructField("learner_id", DataTypes.StringType, false),
                DataTypes.createStructField("skill_id", DataTypes.StringType, false),
                DataTypes.createStructField("score", DataTypes.IntegerType, false),
                DataTypes.createStructField("completion_date", DataTypes.StringType, false),
                DataTypes.createStructField("study_hours", DataTypes.IntegerType, false)
        });
    }
}
