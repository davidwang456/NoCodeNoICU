package com.davidwang456.excel.controller;

import com.davidwang456.excel.model.ExamPaper;
import com.davidwang456.excel.model.ExamQuestion;
import com.davidwang456.excel.model.PDFResult;
import com.davidwang456.excel.service.PDFTextService;
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
 * PDF文本提取控制器
 */
@Api(tags = "PDF文本提取")
@RestController
@RequestMapping("/api/pdf")
public class PDFController {
    private static final Logger LOGGER = LoggerFactory.getLogger(PDFController.class);
    
    @Autowired
    private PDFTextService pdfTextService;
    
    @ApiOperation("上传PDF文件进行文本提取")
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "paperName", required = false) String paperName) {
        
        try {
            // 如果paperName为空，则使用文件名（不含扩展名）
            if (paperName == null || paperName.trim().isEmpty()) {
                String originalFilename = file.getOriginalFilename();
                if (originalFilename != null) {
                    // 移除扩展名
                    paperName = originalFilename.replaceFirst("[.][^.]+$", "");
                } else {
                    paperName = "未命名文档";
                }
            }
            
            LOGGER.info("接收到PDF处理请求: 文件={}, 试卷名称={}", file.getOriginalFilename(), paperName);
            
            // 从文件名中提取年份（如果文件名包含年份格式如2023、2023年等）
            String year = extractYearFromFileName(file.getOriginalFilename());
            
            PDFResult result = pdfTextService.processPDF(file, paperName, year);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOGGER.error("PDF处理失败: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("errorMessage", "PDF处理失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * 从文件名中提取年份
     * 支持的格式：包含4位数字的年份（如2023、2023年）
     * @param fileName 文件名
     * @return 提取的年份，如果未找到则返回当前年份
     */
    private String extractYearFromFileName(String fileName) {
        if (fileName == null) {
            return String.valueOf(java.time.Year.now().getValue());
        }
        
        // 匹配4位数字年份
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(20\\d{2})");
        java.util.regex.Matcher matcher = pattern.matcher(fileName);
        
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        // 如果未找到年份，返回当前年份
        return String.valueOf(java.time.Year.now().getValue());
    }
    
    @ApiOperation("保存提取结果")
    @PostMapping("/save")
    public ResponseEntity<?> saveResult(@RequestBody Map<String, Object> requestBody) {
        
        try {
            LOGGER.info("接收到保存请求: {}", requestBody.keySet());
            
            String paperName = (String) requestBody.get("paperName");
            String year = (String) requestBody.get("year");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> questionsMapList = (List<Map<String, Object>>) requestBody.get("questions");
            
            LOGGER.info("解析请求参数: paperName={}, year={}, questions={}", 
                    paperName, 
                    year, 
                    questionsMapList != null ? questionsMapList.size() : "null");
            
            if (paperName == null || paperName.trim().isEmpty()) {
                LOGGER.error("缺少必要参数: paperName");
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("errorMessage", "缺少必要参数: paperName");
                return ResponseEntity.badRequest().body(error);
            }
            
            if (questionsMapList == null || questionsMapList.isEmpty()) {
                LOGGER.error("缺少必要参数: questions 或 questions为空");
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("errorMessage", "缺少必要参数: questions 或 questions为空");
                return ResponseEntity.badRequest().body(error);
            }
            
            // 如果year为空，则使用当前年份
            if (year == null || year.trim().isEmpty()) {
                year = String.valueOf(java.time.Year.now().getValue());
                LOGGER.info("年份为空，使用当前年份: {}", year);
            }
            
            LOGGER.info("接收到保存提取结果请求: 试卷名称={}, 年份={}, 题目数量={}", paperName, year, questionsMapList.size());
            
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
                
                // 确保year字段有值
                String questionYear = (String) questionMap.get("year");
                if (questionYear == null || questionYear.trim().isEmpty()) {
                    questionYear = year;
                }
                question.setYear(questionYear);
                
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
            
            LOGGER.info("转换后的题目数量: {}", questions.size());
            
            Long paperId = pdfTextService.savePaperAndQuestions(paperName, year, questions);
            
            LOGGER.info("保存成功，试卷ID: {}", paperId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", paperId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LOGGER.error("保存提取结果失败: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("errorMessage", "保存提取结果失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    @ApiOperation("获取试卷列表")
    @GetMapping("/papers")
    public ResponseEntity<?> getPaperList() {
        try {
            List<ExamPaper> papers = pdfTextService.getPaperList();
            
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
            ExamPaper paper = pdfTextService.getPaperDetail(paperId);
            
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
            boolean result = pdfTextService.deletePaper(paperId);
            
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
            ExamQuestion currentQuestion = pdfTextService.getQuestionById(questionId);
            if (currentQuestion == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("errorMessage", "题目不存在: " + questionId);
                return ResponseEntity.badRequest().body(error);
            }
            
            // 保留paperId
            question.setPaperId(currentQuestion.getPaperId());
            
            // 更新题目
            boolean success = pdfTextService.updateQuestion(question);
            
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
            boolean result = pdfTextService.deleteQuestion(questionId);
            
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