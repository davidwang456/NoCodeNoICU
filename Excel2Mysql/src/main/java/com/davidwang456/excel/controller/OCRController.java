package com.davidwang456.excel.controller;

import com.davidwang456.excel.model.ExamPaper;
import com.davidwang456.excel.model.ExamQuestion;
import com.davidwang456.excel.model.OCRResult;
import com.davidwang456.excel.service.OCRService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OCR控制器
 */
@Api(tags = "OCR图片识别")
@RestController
@RequestMapping("/api/ocr")
public class OCRController {
    private static final Logger LOGGER = LoggerFactory.getLogger(OCRController.class);
    
    @Autowired
    private OCRService ocrService;
    
    @ApiOperation("上传文件进行OCR识别")
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("paperName") String paperName,
            @RequestParam("year") String year) {
        
        try {
            LOGGER.info("接收到OCR识别请求: 文件={}, 试卷名称={}, 年份={}", file.getOriginalFilename(), paperName, year);
            
            OCRResult result = ocrService.processOCR(file, paperName, year);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOGGER.error("OCR识别失败: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("errorMessage", "OCR识别失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    @ApiOperation("保存识别结果")
    @PostMapping("/save")
    public ResponseEntity<?> saveResult(@RequestBody Map<String, Object> requestBody) {
        
        try {
            String paperName = (String) requestBody.get("paperName");
            String year = (String) requestBody.get("year");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> questionsMapList = (List<Map<String, Object>>) requestBody.get("questions");
            
            if (paperName == null || year == null || questionsMapList == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("errorMessage", "缺少必要参数: paperName, year 或 questions");
                return ResponseEntity.badRequest().body(error);
            }
            
            LOGGER.info("接收到保存识别结果请求: 试卷名称={}, 年份={}, 题目数量={}", paperName, year, questionsMapList.size());
            
            // 将Map转换为ExamQuestion对象
            List<ExamQuestion> questions = new ArrayList<>();
            for (Map<String, Object> questionMap : questionsMapList) {
                ExamQuestion question = new ExamQuestion();
                
                // 设置基本属性
                if (questionMap.containsKey("id")) {
                    Object idObj = questionMap.get("id");
                    if (idObj instanceof Number) {
                        question.setId(((Number) idObj).longValue());
                    } else if (idObj instanceof String) {
                        try {
                            question.setId(Long.parseLong((String) idObj));
                        } catch (NumberFormatException e) {
                            // 忽略无效的ID
                        }
                    }
                }
                
                // 设置paperId，如果存在
                if (questionMap.containsKey("paperId")) {
                    Object paperIdObj = questionMap.get("paperId");
                    if (paperIdObj instanceof Number) {
                        question.setPaperId(((Number) paperIdObj).longValue());
                    } else if (paperIdObj instanceof String) {
                        try {
                            question.setPaperId(Long.parseLong((String) paperIdObj));
                        } catch (NumberFormatException e) {
                            // 忽略无效的paperId
                        }
                    }
                }
                
                question.setQuestionNumber((String) questionMap.get("questionNumber"));
                question.setQuestionType((String) questionMap.get("questionType"));
                question.setContent((String) questionMap.get("content"));
                question.setImageData((String) questionMap.get("imageData"));
                question.setYear((String) questionMap.get("year"));
                
                // 处理useImageOnly字段
                Object useImageOnlyObj = questionMap.get("useImageOnly");
                if (useImageOnlyObj != null) {
                    if (useImageOnlyObj instanceof Boolean) {
                        question.setUseImageOnly((Boolean) useImageOnlyObj);
                    } else if (useImageOnlyObj instanceof String) {
                        question.setUseImageOnly(Boolean.parseBoolean((String) useImageOnlyObj));
                    }
                } else {
                    question.setUseImageOnly(false);
                }
                
                // 设置时间字段
                question.setCreateTime(new Date());
                question.setUpdateTime(new Date());
                
                questions.add(question);
            }
            
            Long paperId = ocrService.savePaperAndQuestions(paperName, year, questions);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", paperId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LOGGER.error("保存识别结果失败: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("errorMessage", "保存识别结果失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    @ApiOperation("获取试卷列表")
    @GetMapping("/papers")
    public ResponseEntity<?> getPaperList() {
        try {
            List<ExamPaper> papers = ocrService.getPaperList();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", papers);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LOGGER.error("获取试卷列表失败: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("errorMessage", "获取试卷列表失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    @ApiOperation("获取试卷详情")
    @GetMapping("/papers/{paperId}")
    public ResponseEntity<?> getPaperDetail(@PathVariable Long paperId) {
        try {
            ExamPaper paper = ocrService.getPaperDetail(paperId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", paper);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LOGGER.error("获取试卷详情失败: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("errorMessage", "获取试卷详情失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    @ApiOperation("删除试卷")
    @DeleteMapping("/papers/{paperId}")
    public ResponseEntity<?> deletePaper(@PathVariable Long paperId) {
        try {
            boolean result = ocrService.deletePaper(paperId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", result);
            if (!result) {
                response.put("errorMessage", "删除试卷失败");
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LOGGER.error("删除试卷失败: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("errorMessage", "删除试卷失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    @ApiOperation("更新题目")
    @PutMapping("/questions/{questionId}")
    public ResponseEntity<?> updateQuestion(
            @PathVariable Long questionId,
            @RequestBody ExamQuestion question) {
        
        try {
            // 确保ID一致
            question.setId(questionId);
            
            // 获取当前题目信息
            ExamQuestion currentQuestion = ocrService.getQuestionById(questionId);
            if (currentQuestion == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("errorMessage", "题目不存在: " + questionId);
                return ResponseEntity.badRequest().body(error);
            }
            
            // 保留paperId
            question.setPaperId(currentQuestion.getPaperId());
            
            // 更新题目
            boolean success = ocrService.updateQuestion(question);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            if (!success) {
                response.put("errorMessage", "更新题目失败");
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LOGGER.error("更新题目失败: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("errorMessage", "更新题目失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    @ApiOperation("删除题目")
    @DeleteMapping("/questions/{questionId}")
    public ResponseEntity<?> deleteQuestion(@PathVariable Long questionId) {
        try {
            boolean result = ocrService.deleteQuestion(questionId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", result);
            if (!result) {
                response.put("errorMessage", "删除题目失败");
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LOGGER.error("删除题目失败: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("errorMessage", "删除题目失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
} 