package com.spark.app.web.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * MOOC 数据自动检测与转换服务。
 *
 * <p>扫描 {@code mooc_raw/} 目录中的 CSV 文件，根据列名自动识别数据类型，
 * 然后转换为系统所需的四个标准 CSV（learners / skills / courses / learning_records）。</p>
 */
@Service
public class MoocDataConverter {

    @Value("${spark.data.dir}")
    private String dataDir;

    /* ---------- 列名映射表（中/英均支持） ---------- */

    private static final Set<String> USER_ID_COLS = Set.of(
            "user_id", "userid", "用户id", "用户编号", "learner_id", "student_id", "学号");
    private static final Set<String> USER_NAME_COLS = Set.of(
            "username", "name", "用户名", "姓名", "昵称", "nickname", "display_name", "student_name");
    private static final Set<String> REGISTER_DATE_COLS = Set.of(
            "register_time", "registration_date", "注册时间", "注册日期",
            "enroll_date", "enrollment_date", "create_time", "创建时间");

    private static final Set<String> COURSE_ID_COLS = Set.of(
            "course_id", "courseid", "课程id", "课程编号");
    private static final Set<String> COURSE_NAME_COLS = Set.of(
            "course_name", "coursename", "课程名", "课程名称", "课程");
    private static final Set<String> CATEGORY_COLS = Set.of(
            "category", "分类", "类别", "领域", "field", "domain", "subject");
    private static final Set<String> DIFFICULTY_COLS = Set.of(
            "difficulty", "difficulty_level", "难度", "难度等级", "level");

    private static final Set<String> SCORE_COLS = Set.of(
            "score", "grade", "成绩", "分数", "得分", "final_score", "exam_score");
    private static final Set<String> COMPLETION_DATE_COLS = Set.of(
            "completion_date", "complete_date", "完成时间", "完成日期",
            "finish_date", "submit_time", "提交时间", "end_date");
    private static final Set<String> STUDY_HOURS_COLS = Set.of(
            "study_hours", "学习时长", "hours", "duration", "学时",
            "total_hours", "learning_hours", "study_time");

    /** 转换结果摘要。 */
    public static class ConvertResult {
        public int learnerCount;
        public int skillCount;
        public int courseCount;
        public int recordCount;
        public List<String> detectedFiles = new ArrayList<>();
        public List<String> warnings = new ArrayList<>();
        public boolean success;
        public String message;
    }

    /* ===== 公共方法 ===== */

    /**
     * 检测 mooc_raw 目录下的文件状态。
     */
    public Map<String, Object> detectRawFiles() {
        Map<String, Object> result = new LinkedHashMap<>();
        Path rawDir = Paths.get(dataDir, "mooc_raw");

        if (!Files.isDirectory(rawDir)) {
            result.put("exists", false);
            result.put("message", "mooc_raw 目录不存在，请创建 " + rawDir.toAbsolutePath());
            return result;
        }

        try (Stream<Path> paths = Files.list(rawDir)) {
            List<Map<String, Object>> files = paths
                    .filter(p -> p.toString().toLowerCase().endsWith(".csv"))
                    .map(this::analyzeFile)
                    .collect(Collectors.toList());

            result.put("exists", true);
            result.put("directory", rawDir.toAbsolutePath().toString());
            result.put("csvFileCount", files.size());
            result.put("files", files);
        } catch (IOException e) {
            result.put("exists", true);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 执行转换：读取 mooc_raw/ 下的 CSV → 输出系统标准 CSV 到 data/ 。
     */
    public ConvertResult convert() {
        ConvertResult result = new ConvertResult();
        Path rawDir = Paths.get(dataDir, "mooc_raw");

        if (!Files.isDirectory(rawDir)) {
            result.success = false;
            result.message = "mooc_raw 目录不存在: " + rawDir.toAbsolutePath();
            return result;
        }

        try {
            // 1. 收集所有CSV文件
            List<Path> csvFiles;
            try (Stream<Path> paths = Files.list(rawDir)) {
                csvFiles = paths
                        .filter(p -> p.toString().toLowerCase().endsWith(".csv"))
                        .collect(Collectors.toList());
            }

            if (csvFiles.isEmpty()) {
                result.success = false;
                result.message = "mooc_raw 目录中没有找到CSV文件";
                return result;
            }

            // 2. 解析所有文件 → 合并到统一的数据池
            DataPool pool = new DataPool();
            for (Path csv : csvFiles) {
                result.detectedFiles.add(csv.getFileName().toString());
                parseFileIntoPool(csv, pool, result.warnings);
            }

            // 3. 自动生成技能映射（从课程名/分类中提取）
            pool.buildSkillMapping();

            // 4. 写出四个标准CSV
            result.learnerCount = writeLearnersCSV(pool);
            result.skillCount = writeSkillsCSV(pool);
            result.courseCount = writeCoursesCSV(pool);
            result.recordCount = writeRecordsCSV(pool);

            result.success = true;
            result.message = String.format(
                    "导入成功！转换了 %d 个学习者、%d 个技能、%d 门课程、%d 条学习记录",
                    result.learnerCount, result.skillCount, result.courseCount, result.recordCount);

        } catch (IOException e) {
            result.success = false;
            result.message = "转换失败: " + e.getMessage();
        }
        return result;
    }

    /* ===== 内部数据结构 ===== */

    private static class DataPool {
        /** learner_id → {name, registration_date} */
        Map<String, Map<String, String>> learners = new LinkedHashMap<>();
        /** course_id/course_name → {course_name, category, difficulty} */
        Map<String, Map<String, String>> courses = new LinkedHashMap<>();
        /** 学习记录列表 */
        List<Map<String, String>> records = new ArrayList<>();

        /** 自动生成的 course_name → skill_id 映射 */
        Map<String, String> courseToSkill = new LinkedHashMap<>();
        /** skill_id → {skill_name, category, difficulty} */
        Map<String, Map<String, String>> skills = new LinkedHashMap<>();

        int learnerSeq = 1;
        int courseSeq = 1;
        int skillSeq = 1;

        String getOrCreateLearnerId(String rawId, String name, String regDate) {
            if (rawId != null && !rawId.isEmpty()) {
                learners.computeIfAbsent(rawId, k -> {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("name", name != null ? name : "学习者" + rawId);
                    m.put("registration_date", regDate != null ? regDate : "2023-01-01");
                    return m;
                });
                return rawId;
            }
            return null;
        }

        String getOrCreateCourseId(String rawCourseId, String courseName,
                                    String category, String difficulty) {
            String key = rawCourseId != null && !rawCourseId.isEmpty()
                    ? rawCourseId
                    : (courseName != null ? courseName : null);
            if (key == null) return null;

            courses.computeIfAbsent(key, k -> {
                Map<String, String> m = new LinkedHashMap<>();
                String cid = rawCourseId != null && !rawCourseId.isEmpty()
                        ? rawCourseId
                        : "C" + String.format("%03d", courseSeq++);
                m.put("course_id", cid);
                m.put("course_name", courseName != null ? courseName : key);
                m.put("category", category != null ? category : "通用");
                m.put("difficulty", parseDifficulty(difficulty));
                return m;
            });
            return courses.get(key).get("course_id");
        }

        void buildSkillMapping() {
            // 从课程中提取技能：每门课程 → 一个技能
            for (Map.Entry<String, Map<String, String>> entry : courses.entrySet()) {
                Map<String, String> c = entry.getValue();
                String courseName = c.get("course_name");
                String category = c.get("category");
                String difficulty = c.get("difficulty");

                String skillId = "S" + String.format("%03d", skillSeq++);
                courseToSkill.put(c.get("course_id"), skillId);

                Map<String, String> skill = new LinkedHashMap<>();
                skill.put("skill_name", courseName);
                skill.put("category", category);
                skill.put("difficulty_level", difficulty);
                skills.put(skillId, skill);
            }
        }

        String getSkillId(String courseId) {
            return courseToSkill.getOrDefault(courseId, "S001");
        }
    }

    /* ===== CSV 解析 ===== */

    private void parseFileIntoPool(Path file, DataPool pool, List<String> warnings) throws IOException {
        List<String[]> rows = readCSV(file);
        if (rows.size() < 2) {
            warnings.add(file.getFileName() + ": 文件为空或只有表头");
            return;
        }

        String[] headers = rows.get(0);
        // 标准化列名（小写 + 去空格）
        String[] normalizedHeaders = Arrays.stream(headers)
                .map(h -> h.trim().toLowerCase().replace("\uFEFF", ""))
                .toArray(String[]::new);

        // 检测列映射
        ColumnMapping mapping = detectColumns(normalizedHeaders);

        if (mapping.hasRecordData()) {
            // 这是学习记录数据（可能也包含用户和课程信息）
            for (int i = 1; i < rows.size(); i++) {
                String[] row = rows.get(i);
                if (row.length < normalizedHeaders.length) {
                    row = Arrays.copyOf(row, normalizedHeaders.length);
                }
                processRecordRow(row, mapping, pool);
            }
        } else if (mapping.hasUserData() && !mapping.hasCourseData()) {
            // 纯用户数据
            for (int i = 1; i < rows.size(); i++) {
                String[] row = rows.get(i);
                if (row.length < normalizedHeaders.length) {
                    row = Arrays.copyOf(row, normalizedHeaders.length);
                }
                String uid = getVal(row, mapping.userIdIdx);
                String name = getVal(row, mapping.userNameIdx);
                String regDate = getVal(row, mapping.registerDateIdx);
                pool.getOrCreateLearnerId(uid, name, regDate);
            }
        } else if (mapping.hasCourseData()) {
            // 纯课程数据
            for (int i = 1; i < rows.size(); i++) {
                String[] row = rows.get(i);
                if (row.length < normalizedHeaders.length) {
                    row = Arrays.copyOf(row, normalizedHeaders.length);
                }
                String cid = getVal(row, mapping.courseIdIdx);
                String cname = getVal(row, mapping.courseNameIdx);
                String cat = getVal(row, mapping.categoryIdx);
                String diff = getVal(row, mapping.difficultyIdx);
                pool.getOrCreateCourseId(cid, cname, cat, diff);
            }
        } else {
            warnings.add(file.getFileName() + ": 无法识别的列结构 (" +
                    String.join(", ", headers) + ")");
        }
    }

    private void processRecordRow(String[] row, ColumnMapping mapping, DataPool pool) {
        String userId = getVal(row, mapping.userIdIdx);
        String userName = getVal(row, mapping.userNameIdx);
        String regDate = getVal(row, mapping.registerDateIdx);
        String courseId = getVal(row, mapping.courseIdIdx);
        String courseName = getVal(row, mapping.courseNameIdx);
        String category = getVal(row, mapping.categoryIdx);
        String difficulty = getVal(row, mapping.difficultyIdx);
        String score = getVal(row, mapping.scoreIdx);
        String compDate = getVal(row, mapping.completionDateIdx);
        String studyHours = getVal(row, mapping.studyHoursIdx);

        if (userId == null || userId.isEmpty()) return;

        // 注册学习者
        pool.getOrCreateLearnerId(userId, userName, regDate);

        // 注册课程
        String resolvedCourseId = pool.getOrCreateCourseId(courseId, courseName, category, difficulty);

        // 添加学习记录
        if (resolvedCourseId != null) {
            Map<String, String> record = new LinkedHashMap<>();
            record.put("learner_id", userId);
            record.put("course_id", resolvedCourseId);
            record.put("score", parseScore(score));
            record.put("completion_date", compDate != null ? normalizeDate(compDate) : "2023-06-01");
            record.put("study_hours", studyHours != null ? parseHours(studyHours) : estimateHours());
            pool.records.add(record);
        }
    }

    /* ===== 列检测 ===== */

    private static class ColumnMapping {
        int userIdIdx = -1;
        int userNameIdx = -1;
        int registerDateIdx = -1;
        int courseIdIdx = -1;
        int courseNameIdx = -1;
        int categoryIdx = -1;
        int difficultyIdx = -1;
        int scoreIdx = -1;
        int completionDateIdx = -1;
        int studyHoursIdx = -1;

        boolean hasUserData() {
            return userIdIdx >= 0;
        }

        boolean hasCourseData() {
            return courseIdIdx >= 0 || courseNameIdx >= 0;
        }

        boolean hasRecordData() {
            return hasUserData() && (hasCourseData() || scoreIdx >= 0);
        }
    }

    private ColumnMapping detectColumns(String[] headers) {
        ColumnMapping m = new ColumnMapping();
        for (int i = 0; i < headers.length; i++) {
            String h = headers[i];
            if (m.userIdIdx < 0 && USER_ID_COLS.contains(h)) m.userIdIdx = i;
            else if (m.userNameIdx < 0 && USER_NAME_COLS.contains(h)) m.userNameIdx = i;
            else if (m.registerDateIdx < 0 && REGISTER_DATE_COLS.contains(h)) m.registerDateIdx = i;
            else if (m.courseIdIdx < 0 && COURSE_ID_COLS.contains(h)) m.courseIdIdx = i;
            else if (m.courseNameIdx < 0 && COURSE_NAME_COLS.contains(h)) m.courseNameIdx = i;
            else if (m.categoryIdx < 0 && CATEGORY_COLS.contains(h)) m.categoryIdx = i;
            else if (m.difficultyIdx < 0 && DIFFICULTY_COLS.contains(h)) m.difficultyIdx = i;
            else if (m.scoreIdx < 0 && SCORE_COLS.contains(h)) m.scoreIdx = i;
            else if (m.completionDateIdx < 0 && COMPLETION_DATE_COLS.contains(h)) m.completionDateIdx = i;
            else if (m.studyHoursIdx < 0 && STUDY_HOURS_COLS.contains(h)) m.studyHoursIdx = i;
        }
        return m;
    }

    /* ===== CSV 写出 ===== */

    private int writeLearnersCSV(DataPool pool) throws IOException {
        Path out = Paths.get(dataDir, "learners.csv");
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            w.write("learner_id,name,registration_date");
            w.newLine();
            int count = 0;
            for (Map.Entry<String, Map<String, String>> e : pool.learners.entrySet()) {
                w.write(csvEscape(e.getKey()) + "," +
                        csvEscape(e.getValue().get("name")) + "," +
                        csvEscape(e.getValue().get("registration_date")));
                w.newLine();
                count++;
            }
            return count;
        }
    }

    private int writeSkillsCSV(DataPool pool) throws IOException {
        Path out = Paths.get(dataDir, "skills.csv");
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            w.write("skill_id,skill_name,category,difficulty_level");
            w.newLine();
            int count = 0;
            for (Map.Entry<String, Map<String, String>> e : pool.skills.entrySet()) {
                Map<String, String> s = e.getValue();
                w.write(csvEscape(e.getKey()) + "," +
                        csvEscape(s.get("skill_name")) + "," +
                        csvEscape(s.get("category")) + "," +
                        csvEscape(s.get("difficulty_level")));
                w.newLine();
                count++;
            }
            return count;
        }
    }

    private int writeCoursesCSV(DataPool pool) throws IOException {
        Path out = Paths.get(dataDir, "courses.csv");
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            w.write("course_id,course_name,skill_id,difficulty_level,prerequisites");
            w.newLine();
            int count = 0;
            for (Map.Entry<String, Map<String, String>> e : pool.courses.entrySet()) {
                Map<String, String> c = e.getValue();
                String courseId = c.get("course_id");
                String skillId = pool.getSkillId(courseId);
                w.write(csvEscape(courseId) + "," +
                        csvEscape(c.get("course_name")) + "," +
                        csvEscape(skillId) + "," +
                        csvEscape(c.get("difficulty")) + ",");
                w.newLine();
                count++;
            }
            return count;
        }
    }

    private int writeRecordsCSV(DataPool pool) throws IOException {
        Path out = Paths.get(dataDir, "learning_records.csv");
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            w.write("record_id,learner_id,skill_id,score,completion_date,study_hours");
            w.newLine();
            int count = 0;
            for (Map<String, String> r : pool.records) {
                count++;
                String courseId = r.get("course_id");
                String skillId = pool.getSkillId(courseId);
                w.write("R" + String.format("%04d", count) + "," +
                        csvEscape(r.get("learner_id")) + "," +
                        csvEscape(skillId) + "," +
                        csvEscape(r.get("score")) + "," +
                        csvEscape(r.get("completion_date")) + "," +
                        csvEscape(r.get("study_hours")));
                w.newLine();
            }
            return count;
        }
    }

    /* ===== 文件分析（用于检测接口） ===== */

    private Map<String, Object> analyzeFile(Path file) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("fileName", file.getFileName().toString());
        try {
            List<String[]> rows = readCSV(file);
            if (rows.isEmpty()) {
                info.put("status", "empty");
                return info;
            }
            String[] headers = rows.get(0);
            String[] normalized = Arrays.stream(headers)
                    .map(h -> h.trim().toLowerCase().replace("\uFEFF", ""))
                    .toArray(String[]::new);
            ColumnMapping mapping = detectColumns(normalized);

            info.put("rowCount", rows.size() - 1);
            info.put("columns", Arrays.asList(headers));

            String type;
            if (mapping.hasRecordData()) {
                type = "学习记录（含用户+课程信息）";
            } else if (mapping.hasUserData() && !mapping.hasCourseData()) {
                type = "用户/学习者数据";
            } else if (mapping.hasCourseData()) {
                type = "课程数据";
            } else {
                type = "未识别（列名不匹配）";
            }
            info.put("detectedType", type);
            info.put("status", "ok");
        } catch (IOException e) {
            info.put("status", "error");
            info.put("error", e.getMessage());
        }
        return info;
    }

    /* ===== 工具方法 ===== */

    private List<String[]> readCSV(Path file) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    rows.add(parseCSVLine(line));
                }
            }
        }
        return rows;
    }

    /**
     * 简单 CSV 行解析（支持双引号包裹含逗号的字段）。
     */
    private String[] parseCSVLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(current.toString().trim());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
        }
        fields.add(current.toString().trim());
        return fields.toArray(new String[0]);
    }

    private String getVal(String[] row, int idx) {
        if (idx < 0 || idx >= row.length) return null;
        String v = row[idx];
        return (v == null || v.isEmpty()) ? null : v.trim();
    }

    private static String parseDifficulty(String raw) {
        if (raw == null || raw.isEmpty()) return "1";
        try {
            int d = Integer.parseInt(raw.trim());
            return String.valueOf(Math.max(1, Math.min(4, d)));
        } catch (NumberFormatException e) {
            // 尝试中文匹配
            if (raw.contains("入门") || raw.contains("初级") || raw.contains("easy")) return "1";
            if (raw.contains("中级") || raw.contains("medium")) return "2";
            if (raw.contains("高级") || raw.contains("hard")) return "3";
            if (raw.contains("专家") || raw.contains("expert")) return "4";
            return "1";
        }
    }

    private static String parseScore(String raw) {
        if (raw == null || raw.isEmpty()) return "70";
        try {
            double d = Double.parseDouble(raw.trim());
            // 如果是百分比（0-1），转换为0-100
            if (d >= 0 && d <= 1.0) {
                return String.valueOf((int) Math.round(d * 100));
            }
            return String.valueOf((int) Math.round(Math.max(0, Math.min(100, d))));
        } catch (NumberFormatException e) {
            // 尝试等级转换
            String s = raw.trim().toUpperCase();
            if (s.startsWith("A") || s.equals("优") || s.equals("优秀")) return "92";
            if (s.startsWith("B") || s.equals("良") || s.equals("良好")) return "82";
            if (s.startsWith("C") || s.equals("中") || s.equals("及格")) return "72";
            if (s.startsWith("D") || s.equals("差") || s.equals("不及格")) return "55";
            if (s.equals("通过") || s.equals("PASS")) return "75";
            if (s.equals("未通过") || s.equals("FAIL")) return "40";
            return "70";
        }
    }

    private static String parseHours(String raw) {
        if (raw == null || raw.isEmpty()) return "30";
        try {
            double d = Double.parseDouble(raw.trim());
            return String.valueOf(Math.max(1, (int) Math.round(d)));
        } catch (NumberFormatException e) {
            return "30";
        }
    }

    private static String estimateHours() {
        return String.valueOf(20 + new Random().nextInt(41)); // 20-60
    }

    private static String normalizeDate(String raw) {
        if (raw == null) return "2023-06-01";
        // 处理常见日期格式
        String s = raw.trim()
                .replace("/", "-")
                .replace(".", "-");
        // 截取到日期部分（去掉时间）
        if (s.length() > 10) {
            s = s.substring(0, 10);
        }
        return s;
    }

    private static String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
