package com.davidwang456.excel.controller;

import com.davidwang456.excel.model.ExamPaper;
import com.davidwang456.excel.model.ExamQuestion;
import com.davidwang456.excel.service.DocTextService;
import com.davidwang456.excel.service.OCRService;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 文档处理控制器
 * 支持多种格式的文档：.doc .docx .eml .xls .xlsx .ppt .pptx .pdf .txt .json .csv等
 */
@Api(tags = "文档处理接口")
@RestController
@RequestMapping("/api/doc")
@Slf4j
public class DocController {
    
    @Autowired
    private DocTextService docTextService;
    
    @Autowired
    private OCRService ocrService; // 用于搜索功能
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 上传并处理文档
     * @param file 文件
     * @param paperName 可选的文档名称
     * @return 处理结果
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiOperation("上传并处理文档")
    public Map<String, Object> uploadFile(
            @ApiParam(value = "文件", required = true) @RequestParam("file") MultipartFile file,
            @ApiParam(value = "文档名称") @RequestParam(value = "paperName", required = false) String paperName) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            log.info("接收到文档: {}", file.getOriginalFilename());
            
            // 如果未提供文档名称，则使用文件名（不含扩展名）
            if (paperName == null || paperName.isEmpty()) {
                String fileName = file.getOriginalFilename();
                if (fileName != null && fileName.contains(".")) {
                    paperName = fileName.substring(0, fileName.lastIndexOf("."));
                } else {
                    paperName = "未命名文档";
                }
            }
            
            log.info("处理文档: {}", paperName);
            
            // 使用Apache Tika处理文档
            List<ExamQuestion> questions = docTextService.processDocumentWithTika(
                file.getBytes(), 
                file.getOriginalFilename(), // 传递原始文件名
                paperName // 传递文档名称
            );
            
            log.info("文档 {} 处理完成，共 {} 页", paperName, questions.size());
            
            result.put("success", true);
            result.put("paperName", paperName);
            result.put("questionCount", questions.size());
            result.put("questions", questions);
            
        } catch (Exception e) {
            log.error("处理文档失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "处理文档失败: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 保存处理结果
     * @param request 包含paperName和questions的请求体
     * @return 保存结果
     */
    @PostMapping("/save")
    @ApiOperation("保存处理结果")
    public Map<String, Object> saveResult(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 验证请求
            if (!request.containsKey("paperName") || !request.containsKey("questions")) {
                result.put("success", false);
                result.put("message", "请求缺少paperName或questions字段");
                return result;
            }
            
            String paperName = (String) request.get("paperName");
            List<Map<String, Object>> questionsMap = (List<Map<String, Object>>) request.get("questions");
            
            log.info("保存文档: {}, 页数: {}", paperName, questionsMap.size());
            
            // 转换Map为ExamQuestion对象
            List<ExamQuestion> questions = questionsMap.stream()
                    .map(this::convertToExamQuestion)
                    .collect(Collectors.toList());
            
            // 保存文档和页面
            int paperId = docTextService.savePaperAndQuestions(paperName, questions);
            
            result.put("success", true);
            result.put("message", "保存成功");
            result.put("paperId", paperId);
            
        } catch (Exception e) {
            log.error("保存文档失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "保存失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 获取文档列表
     * @return 文档列表
     */
    @GetMapping("/papers")
    @ApiOperation("获取文档列表")
    public Map<String, Object> getPapers() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            List<ExamPaper> papers = new ArrayList<>();
            
            // 从数据库查询文档列表
            String sql = "SELECT id, paper_name, question_count, create_time FROM exam_paper ORDER BY create_time DESC";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            
            for (Map<String, Object> row : rows) {
                ExamPaper paper = new ExamPaper();
                paper.setId(((Number) row.get("id")).longValue());
                paper.setPaperName((String) row.get("paper_name"));
                paper.setQuestionCount((Integer) row.get("question_count"));
                paper.setCreateTime((Date) row.get("create_time"));
                papers.add(paper);
            }
            
            result.put("success", true);
            result.put("papers", papers);
            
        } catch (Exception e) {
            log.error("获取文档列表失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "获取文档列表失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 获取文档详情
     * @param id 文档ID
     * @return 文档详情
     */
    @GetMapping("/paper/{id}")
    @ApiOperation("获取文档详情")
    public Map<String, Object> getPaper(@PathVariable("id") int id) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 查询文档信息
            String paperSql = "SELECT id, paper_name, question_count, create_time FROM exam_paper WHERE id = ?";
            Map<String, Object> paperRow = jdbcTemplate.queryForMap(paperSql, id);
            
            ExamPaper paper = new ExamPaper();
            paper.setId(((Number) paperRow.get("id")).longValue());
            paper.setPaperName((String) paperRow.get("paper_name"));
            paper.setQuestionCount((Integer) paperRow.get("question_count"));
            paper.setCreateTime((Date) paperRow.get("create_time"));
            
            // 查询文档的所有页面
            String questionSql = "SELECT id, paper_id, page_number, content, image_data FROM exam_question WHERE paper_id = ? ORDER BY page_number ASC";
            List<Map<String, Object>> questionRows = jdbcTemplate.queryForList(questionSql, id);
            
            List<ExamQuestion> questions = new ArrayList<>();
            for (Map<String, Object> row : questionRows) {
                ExamQuestion question = new ExamQuestion();
                question.setId(((Number) row.get("id")).longValue());
                question.setPaperId(((Number) row.get("paper_id")).longValue());
                question.setPageNumber((Integer) row.get("page_number"));
                question.setContent((String) row.get("content"));
                question.setImageData((String) row.get("image_data"));
                question.setPaperName(paper.getPaperName());
                questions.add(question);
            }
            
            result.put("success", true);
            result.put("paper", paper);
            result.put("questions", questions);
            
        } catch (Exception e) {
            log.error("获取文档详情失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "获取文档详情失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 更新页面
     * @param question 页面对象
     * @return 更新结果
     */
    @PostMapping("/updateQuestion")
    @ApiOperation("更新页面")
    public Map<String, Object> updateQuestion(@RequestBody ExamQuestion question) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            if (question.getId() == null) {
                result.put("success", false);
                result.put("message", "缺少页面ID");
                return result;
            }
            
            // 更新页面
            String sql = "UPDATE exam_question SET page_number = ?, content = ? WHERE id = ?";
            int rows = jdbcTemplate.update(sql, question.getPageNumber(), question.getContent(), question.getId());
            
            if (rows > 0) {
                result.put("success", true);
                result.put("message", "更新成功");
            } else {
                result.put("success", false);
                result.put("message", "未找到指定ID的页面");
            }
            
        } catch (Exception e) {
            log.error("更新页面失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "更新页面失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 删除页面
     * @param request 包含页面ID的请求体
     * @return 删除结果
     */
    @PostMapping("/deleteQuestion")
    @ApiOperation("删除页面")
    public Map<String, Object> deleteQuestion(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Number id = (Number) request.get("id");
            if (id == null) {
                result.put("success", false);
                result.put("message", "缺少页面ID");
                return result;
            }
            
            // 获取页面所属的文档ID
            String getPaperIdSql = "SELECT paper_id FROM exam_question WHERE id = ?";
            Long paperId = jdbcTemplate.queryForObject(getPaperIdSql, Long.class, id.longValue());
            
            // 删除页面
            String sql = "DELETE FROM exam_question WHERE id = ?";
            int rows = jdbcTemplate.update(sql, id.longValue());
            
            if (rows > 0) {
                // 更新文档的页面数量
                String updatePaperSql = "UPDATE exam_paper SET question_count = question_count - 1 WHERE id = ?";
                jdbcTemplate.update(updatePaperSql, paperId);
                
                result.put("success", true);
                result.put("message", "删除成功");
            } else {
                result.put("success", false);
                result.put("message", "未找到指定ID的页面");
            }
            
        } catch (Exception e) {
            log.error("删除页面失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "删除页面失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 删除文档
     * @param request 包含文档ID的请求体
     * @return 删除结果
     */
    @PostMapping("/deletePaper")
    @ApiOperation("删除文档")
    public Map<String, Object> deletePaper(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Number id = (Number) request.get("id");
            if (id == null) {
                result.put("success", false);
                result.put("message", "缺少文档ID");
                return result;
            }
            
            // 先删除该文档的所有页面
            String deleteQuestionsSql = "DELETE FROM exam_question WHERE paper_id = ?";
            jdbcTemplate.update(deleteQuestionsSql, id.longValue());
            
            // 再删除文档
            String deletePaperSql = "DELETE FROM exam_paper WHERE id = ?";
            int rows = jdbcTemplate.update(deletePaperSql, id.longValue());
            
            if (rows > 0) {
                result.put("success", true);
                result.put("message", "删除成功");
            } else {
                result.put("success", false);
                result.put("message", "未找到指定ID的文档");
            }
            
        } catch (Exception e) {
            log.error("删除文档失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "删除文档失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 搜索文档或页面
     * @param query 搜索关键词
     * @return 搜索结果
     */
    @GetMapping("/search")
    @ApiOperation("搜索文档或页面")
    public Map<String, Object> search(@RequestParam("query") String query) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            if (query == null || query.trim().isEmpty()) {
                result.put("success", false);
                result.put("message", "搜索关键词不能为空");
                return result;
            }
            
            query = "%" + query.trim() + "%";
            
            // 先查找匹配的文档
            String paperSql = "SELECT id, paper_name, question_count, create_time FROM exam_paper WHERE paper_name LIKE ? ORDER BY create_time DESC";
            List<Map<String, Object>> paperRows = jdbcTemplate.queryForList(paperSql, query);
            
            List<ExamPaper> papers = new ArrayList<>();
            for (Map<String, Object> row : paperRows) {
                ExamPaper paper = new ExamPaper();
                paper.setId(((Number) row.get("id")).longValue());
                paper.setPaperName((String) row.get("paper_name"));
                paper.setQuestionCount((Integer) row.get("question_count"));
                paper.setCreateTime((Date) row.get("create_time"));
                papers.add(paper);
            }
            
            // 再查找内容匹配的页面
            String questionSql = "SELECT q.id, q.paper_id, q.page_number, q.content, p.paper_name FROM exam_question q " +
                                "JOIN exam_paper p ON q.paper_id = p.id " +
                                "WHERE q.content LIKE ? " +
                                "ORDER BY q.paper_id, q.page_number";
            List<Map<String, Object>> questionRows = jdbcTemplate.queryForList(questionSql, query);
            
            // 将找到的页面按文档分组
            Map<Long, List<ExamQuestion>> questionsByPaper = new HashMap<>();
            for (Map<String, Object> row : questionRows) {
                Long paperId = ((Number) row.get("paper_id")).longValue();
                
                if (!questionsByPaper.containsKey(paperId)) {
                    questionsByPaper.put(paperId, new ArrayList<>());
                }
                
                ExamQuestion question = new ExamQuestion();
                question.setId(((Number) row.get("id")).longValue());
                question.setPaperId(paperId);
                question.setPageNumber((Integer) row.get("page_number"));
                question.setContent((String) row.get("content"));
                question.setPaperName((String) row.get("paper_name"));
                
                questionsByPaper.get(paperId).add(question);
            }
            
            // 查找包含匹配页面的文档
            for (Map.Entry<Long, List<ExamQuestion>> entry : questionsByPaper.entrySet()) {
                Long paperId = entry.getKey();
                List<ExamQuestion> questions = entry.getValue();
                
                // 查找这个文档是否已经在papers列表中
                boolean paperExists = false;
                for (ExamPaper paper : papers) {
                    if (paper.getId().equals(paperId)) {
                        // 文档已存在，添加匹配的页面
                        paper.setQuestions(questions);
                        paperExists = true;
                        break;
                    }
                }
                
                // 如果文档不在papers列表中，则添加
                if (!paperExists) {
                    String singlePaperSql = "SELECT id, paper_name, question_count, create_time FROM exam_paper WHERE id = ?";
                    Map<String, Object> paperRow = jdbcTemplate.queryForMap(singlePaperSql, paperId);
                    
                    ExamPaper paper = new ExamPaper();
                    paper.setId(((Number) paperRow.get("id")).longValue());
                    paper.setPaperName((String) paperRow.get("paper_name"));
                    paper.setQuestionCount((Integer) paperRow.get("question_count"));
                    paper.setCreateTime((Date) paperRow.get("create_time"));
                    paper.setQuestions(questions);
                    
                    papers.add(paper);
                }
            }
            
            result.put("success", true);
            result.put("results", papers);
            result.put("count", papers.size());
            
        } catch (Exception e) {
            log.error("搜索失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "搜索失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 将Map转换为ExamQuestion对象
     * @param map Map对象
     * @return ExamQuestion对象
     */
    private ExamQuestion convertToExamQuestion(Map<String, Object> map) {
        ExamQuestion question = new ExamQuestion();
        
        if (map.containsKey("id") && map.get("id") != null) {
            Object idObj = map.get("id");
            if (idObj instanceof Number) {
                question.setId(((Number) idObj).longValue());
            } else {
                question.setId(Long.valueOf(idObj.toString()));
            }
        }
        
        if (map.containsKey("paperId") && map.get("paperId") != null) {
            Object paperIdObj = map.get("paperId");
            if (paperIdObj instanceof Number) {
                question.setPaperId(((Number) paperIdObj).longValue());
            } else {
                question.setPaperId(Long.valueOf(paperIdObj.toString()));
            }
        }
        
        if (map.containsKey("pageNumber") && map.get("pageNumber") != null) {
            Object pageNumberObj = map.get("pageNumber");
            if (pageNumberObj instanceof Number) {
                question.setPageNumber(((Number) pageNumberObj).intValue());
            } else {
                try {
                    question.setPageNumber(Integer.valueOf(pageNumberObj.toString()));
                } catch (NumberFormatException e) {
                    log.warn("页码格式不正确: {}", pageNumberObj);
                    question.setPageNumber(0);
                }
            }
        }
        
        if (map.containsKey("content")) {
            question.setContent((String) map.get("content"));
        }
        
        if (map.containsKey("imageData")) {
            question.setImageData((String) map.get("imageData"));
        }
        
        if (map.containsKey("paperName")) {
            question.setPaperName((String) map.get("paperName"));
        }
        
        return question;
    }
} 