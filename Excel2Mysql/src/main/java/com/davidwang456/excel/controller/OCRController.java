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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
            LOGGER.info("接收到OCR识别请求: 文件={}, 文件名称={}, 年份={}", file.getOriginalFilename(), paperName, year);
            
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
            
            LOGGER.info("接收到保存识别结果请求: 文档名称={}, 年份={}, 页面数量={}", paperName, year, questionsMapList.size());
            
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
                
                // 设置页码
                if (questionMap.containsKey("pageNumber")) {
                    Object pageNumberObj = questionMap.get("pageNumber");
                    if (pageNumberObj instanceof Number) {
                        question.setPageNumber(((Number) pageNumberObj).intValue());
                    } else if (pageNumberObj instanceof String) {
                        try {
                            question.setPageNumber(Integer.parseInt((String) pageNumberObj));
                        } catch (NumberFormatException e) {
                            // 默认设置为1
                            question.setPageNumber(1);
                        }
                    }
                } else {
                    // 如果没有页码，使用questionNumber作为页码（如果存在）
                    if (questionMap.containsKey("questionNumber")) {
                        Object questionNumberObj = questionMap.get("questionNumber");
                        if (questionNumberObj instanceof String) {
                            try {
                                question.setPageNumber(Integer.parseInt((String) questionNumberObj));
                            } catch (NumberFormatException e) {
                                // 默认设置为1
                                question.setPageNumber(1);
                            }
                        }
                    } else {
                        // 默认设置为1
                        question.setPageNumber(1);
                    }
                }
                
                question.setContent((String) questionMap.get("content"));
                question.setImageData((String) questionMap.get("imageData"));
                
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
    
    @ApiOperation("获取文件列表")
    @GetMapping("/papers")
    public ResponseEntity<Map<String, Object>> getPapers() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<ExamPaper> papers = ocrService.getAllPapers();
            
            response.put("success", true);
            response.put("data", papers);
        } catch (Exception e) {
            LOGGER.error("获取文件列表失败: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("errorMessage", "获取文件列表失败: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    @ApiOperation("获取文件详情")
    @GetMapping("/papers/{paperId}")
    public ResponseEntity<Map<String, Object>> getPaper(@PathVariable Long paperId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            ExamPaper paper = ocrService.getPaperById(paperId);
            
            if (paper == null) {
                response.put("success", false);
                response.put("errorMessage", "文件不存在");
                return ResponseEntity.badRequest().body(response);
            }
            
            response.put("success", true);
            response.put("data", paper);
        } catch (Exception e) {
            LOGGER.error("获取文件详情失败: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("errorMessage", "获取文件详情失败: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    @ApiOperation("删除文件")
    @DeleteMapping("/papers/{paperId}")
    public ResponseEntity<?> deletePaper(@PathVariable Long paperId) {
        try {
            boolean result = ocrService.deletePaper(paperId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", result);
            if (!result) {
                response.put("errorMessage", "删除文件失败");
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LOGGER.error("删除文件失败: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("errorMessage", "删除文件失败: " + e.getMessage());
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

    /**
     * 搜索文件或题目
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchPapersOrQuestions(@RequestParam String query) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (query == null || query.trim().isEmpty()) {
                response.put("success", false);
                response.put("errorMessage", "搜索关键词不能为空");
                return ResponseEntity.badRequest().body(response);
            }
            
            LOGGER.info("接收到搜索请求，关键词: {}", query);
            
            // 先搜索文件名
            List<ExamPaper> papers = ocrService.searchPapersByName(query);
            
            if (papers.isEmpty()) {
                // 如果没有找到匹配的文件名，则搜索题目内容
                List<ExamQuestion> questions = ocrService.searchQuestionsByContent(query);
                
                if (questions.isEmpty()) {
                    LOGGER.info("未找到任何匹配的内容，关键词: {}", query);
                    response.put("success", true);
                    response.put("data", Collections.emptyList());
                    response.put("message", "未找到匹配的内容");
                } else {
                    // 找到匹配的题目，将它们按文件分组
                    Map<Long, List<ExamQuestion>> questionsByPaperId = questions.stream()
                            .collect(Collectors.groupingBy(ExamQuestion::getPaperId));
                    
                    // 获取这些题目对应的文件信息
                    List<ExamPaper> papersByQuestion = new ArrayList<>();
                    for (Long paperId : questionsByPaperId.keySet()) {
                        ExamPaper paper = ocrService.getPaperById(paperId);
                        if (paper != null) {
                            // 只包含匹配的题目
                            paper.setQuestions(questionsByPaperId.get(paperId));
                            papersByQuestion.add(paper);
                        }
                    }
                    
                    LOGGER.info("通过内容搜索找到 {} 个文件，共 {} 个匹配页面，关键词: {}", 
                              papersByQuestion.size(), questions.size(), query);
                    
                    response.put("success", true);
                    response.put("data", papersByQuestion);
                    response.put("searchType", "question");
                    response.put("matchCount", questions.size());
                    response.put("message", String.format("找到 %d 个匹配页面", questions.size()));
                }
            } else {
                LOGGER.info("通过文件名搜索找到 {} 个文件，关键词: {}", papers.size(), query);
                
                // 计算总页数
                int totalPages = papers.stream()
                    .mapToInt(p -> p.getQuestions() != null ? p.getQuestions().size() : 0)
                    .sum();
                
                response.put("success", true);
                response.put("data", papers);
                response.put("searchType", "paper");
                response.put("matchCount", papers.size());
                response.put("totalPages", totalPages);
                response.put("message", String.format("找到 %d 个匹配文件，共 %d 页", papers.size(), totalPages));
            }
        } catch (Exception e) {
            LOGGER.error("搜索失败: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("errorMessage", "搜索失败: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
} 