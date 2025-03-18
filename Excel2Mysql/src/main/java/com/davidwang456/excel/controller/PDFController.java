package com.davidwang456.excel.controller;

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
            
            LOGGER.info("接收到PDF处理请求: 文件={}, 文档名称={}", file.getOriginalFilename(), paperName);
            
            // 处理PDF文件，仅进行分页识别
            List<ExamQuestion> pages = pdfTextService.processPdfFileWithIText(file.getBytes(), paperName);
            
            // 构建返回结果
            PDFResult result = new PDFResult();
            result.setSuccess(true);
            result.setQuestions(pages);
            
            LOGGER.info("PDF处理完成，共识别 {} 页", pages.size());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOGGER.error("PDF处理失败: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("errorMessage", "PDF处理失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    @ApiOperation("保存提取结果")
    @PostMapping("/save")
    public ResponseEntity<?> saveResult(@RequestBody Map<String, Object> requestBody) {
        try {
            String paperName = (String) requestBody.get("paperName");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> questionsMapList = (List<Map<String, Object>>) requestBody.get("questions");
            
            if (paperName == null || questionsMapList == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("errorMessage", "缺少必要参数: paperName 或 questions");
                return ResponseEntity.badRequest().body(error);
            }
            
            LOGGER.info("接收到保存提取结果请求: 文档名称={}, 页面数量={}", paperName, questionsMapList.size());
            
            // 将Map转换为ExamQuestion对象
            List<ExamQuestion> questions = new ArrayList<>();
            
            for (Map<String, Object> questionMap : questionsMapList) {
                ExamQuestion question = new ExamQuestion();
                
                // 设置页码
                if (questionMap.containsKey("pageNumber")) {
                    Object pageNumberObj = questionMap.get("pageNumber");
                    if (pageNumberObj instanceof Number) {
                        question.setPageNumber(((Number) pageNumberObj).intValue());
                    } else if (pageNumberObj instanceof String) {
                        try {
                            question.setPageNumber(Integer.parseInt((String) pageNumberObj));
                        } catch (NumberFormatException e) {
                            // 忽略无效的页码
                            question.setPageNumber(0);
                        }
                    }
                }
                
                question.setContent((String) questionMap.get("content"));
                question.setImageData((String) questionMap.get("imageData"));
                question.setPaperName(paperName);
                
                // 设置时间字段
                question.setCreateTime(new Date());
                question.setUpdateTime(new Date());
                
                questions.add(question);
            }
            
            LOGGER.info("转换后的题目数量: {}", questions.size());
            
            Long paperId = pdfTextService.savePaperAndQuestions(paperName, null, questions);
            
            LOGGER.info("保存成功，文件ID: {}", paperId);
            
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
} 