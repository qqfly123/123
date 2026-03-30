package com.spark.app.web.controller;

import com.spark.app.web.service.AnalysisService;
import com.spark.app.web.service.MoocDataConverter;
import com.spark.app.web.service.MoocDataConverter.ConvertResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MOOC 数据导入控制器。
 *
 * <p>提供检测原始数据和执行导入转换的 API 端点。</p>
 */
@RestController
@RequestMapping("/api/import")
public class ImportController {

    private final MoocDataConverter converter;
    private final AnalysisService analysisService;

    public ImportController(MoocDataConverter converter, AnalysisService analysisService) {
        this.converter = converter;
        this.analysisService = analysisService;
    }

    /**
     * 检测 mooc_raw 目录（或自定义路径）下的原始数据文件。
     *
     * @param sourcePath 可选的自定义数据目录路径
     */
    @GetMapping("/detect")
    public ResponseEntity<Map<String, Object>> detectRawFiles(
            @RequestParam(required = false) String sourcePath) {
        return ResponseEntity.ok(converter.detectRawFiles(sourcePath));
    }

    /**
     * 执行 MOOC 数据导入：转换源目录 CSV → data/ 标准 CSV，并重新加载 Spark 数据。
     *
     * @param sourcePath 可选的自定义数据目录路径
     */
    @PostMapping("/mooc")
    public ResponseEntity<Map<String, Object>> importMoocData(
            @RequestParam(required = false) String sourcePath) {
        ConvertResult result = converter.convert(sourcePath);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", result.success);
        response.put("message", result.message);
        response.put("detectedFiles", result.detectedFiles);

        if (result.success) {
            response.put("learnerCount", result.learnerCount);
            response.put("skillCount", result.skillCount);
            response.put("courseCount", result.courseCount);
            response.put("recordCount", result.recordCount);

            // 转换成功后重新加载 Spark 数据
            try {
                analysisService.reloadData();
                response.put("reloaded", true);
            } catch (Exception e) {
                response.put("reloaded", false);
                response.put("reloadError", e.getMessage());
            }
        }

        if (!result.warnings.isEmpty()) {
            response.put("warnings", result.warnings);
        }

        return ResponseEntity.ok(response);
    }
}
