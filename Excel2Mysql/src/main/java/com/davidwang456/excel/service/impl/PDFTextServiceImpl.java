package com.davidwang456.excel.service.impl;

import com.davidwang456.excel.model.ExamPaper;
import com.davidwang456.excel.model.ExamQuestion;
import com.davidwang456.excel.model.PDFResult;
import com.davidwang456.excel.service.PDFTextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import com.itextpdf.text.pdf.parser.SimpleTextExtractionStrategy;
import com.itextpdf.text.pdf.parser.TextExtractionStrategy;

/**
 * PDF文本提取服务实现类
 */
@Service
public class PDFTextServiceImpl implements PDFTextService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PDFTextServiceImpl.class);
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    // 题目类型的正则表达式
    private static final Pattern QUESTION_TYPE_PATTERN = Pattern.compile("\\[(选择题|填空题|解答题|判断题)\\]");
    private static final Pattern QUESTION_NUMBER_PATTERN = Pattern.compile("^(\\d+)[.、]\\s*");
    
    @PostConstruct
    public void init() {
        try {
            // 创建exam_paper表
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS exam_paper (" +
                    "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                    "paper_name VARCHAR(255) NOT NULL, " +
                    "year VARCHAR(10) NOT NULL, " +
                    "question_count INT DEFAULT 0, " +
                    "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                    ")");
            
            // 创建exam_question表
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS exam_question (" +
                    "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                    "paper_id BIGINT NOT NULL, " +
                    "question_number VARCHAR(20) NOT NULL, " +
                    "question_type VARCHAR(50) NOT NULL, " +
                    "content TEXT NOT NULL, " +
                    "image_data LONGTEXT, " +
                    "year VARCHAR(10) NOT NULL, " +
                    "use_image_only BOOLEAN DEFAULT FALSE, " +
                    "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (paper_id) REFERENCES exam_paper(id) ON DELETE CASCADE" +
                    ")");
            
            // 检查是否需要移除paper_name列
            try {
                jdbcTemplate.execute("SELECT paper_name FROM exam_question LIMIT 1");
                // 如果没有抛出异常，说明列存在，需要移除
                LOGGER.info("移除exam_question表中的paper_name列");
                jdbcTemplate.execute("ALTER TABLE exam_question DROP COLUMN paper_name");
            } catch (Exception e) {
                // 列不存在，无需处理
            }
            
            // 检查是否需要添加use_image_only列
            try {
                jdbcTemplate.execute("SELECT use_image_only FROM exam_question LIMIT 1");
            } catch (Exception e) {
                // 列不存在，添加它
                LOGGER.info("添加use_image_only列到exam_question表");
                jdbcTemplate.execute("ALTER TABLE exam_question ADD COLUMN use_image_only BOOLEAN DEFAULT FALSE");
            }
            
            // 检查paper_id是否为NOT NULL
            try {
                jdbcTemplate.execute("ALTER TABLE exam_question MODIFY paper_id BIGINT NOT NULL");
                LOGGER.info("修改paper_id为NOT NULL约束");
            } catch (Exception e) {
                LOGGER.error("修改paper_id约束失败: {}", e.getMessage());
            }
            
            LOGGER.info("相关表创建/更新成功");
            
        } catch (Exception e) {
            LOGGER.error("创建/更新相关表失败: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public PDFResult processPDF(MultipartFile file, String paperName, String year) {
        PDFResult result = new PDFResult();
        
        try {
            LOGGER.info("接收到PDF处理请求: 文件={}, 试卷名称={}, 年份={}", file.getOriginalFilename(), paperName, year);
            
            // 读取文件内容
            byte[] fileContent = file.getBytes();
            String fileType = file.getContentType();
            
            // 根据文件类型选择不同的处理方式
            List<ExamQuestion> questions = new ArrayList<>();
            
            if ("application/pdf".equals(fileType)) {
                // 处理PDF文件 - 使用iText直接提取文本
                questions = processPdfFileWithIText(fileContent, paperName, year);
                
                if (questions.isEmpty()) {
                    result.setSuccess(false);
                    result.setErrorMessage("未能从PDF中提取到题目");
                    return result;
                }
            } else {
                result.setSuccess(false);
                result.setErrorMessage("不支持的文件类型: " + fileType + "，只支持PDF文件");
                return result;
            }
            
            result.setSuccess(true);
            result.setQuestions(questions);
            
            LOGGER.info("文件处理完成，识别出 {} 道题目", questions.size());
            
        } catch (Exception e) {
            LOGGER.error("PDF处理失败: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setErrorMessage("PDF处理失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 简单题目信息类
     */
    private static class SimpleQuestionInfo {
        private String questionNumber;
        private String questionType;
        
        public String getQuestionNumber() {
            return questionNumber;
        }
        
        public void setQuestionNumber(String questionNumber) {
            this.questionNumber = questionNumber;
        }
        
        public String getQuestionType() {
            return questionType;
        }
        
        public void setQuestionType(String questionType) {
            this.questionType = questionType;
        }
    }
    
    /**
     * 使用iText从PDF文件中提取文本并按页码保存
     * @param fileContent PDF文件内容
     * @param paperName 试卷名称
     * @param year 年份
     * @return 按页码保存的内容列表
     */
    private List<ExamQuestion> processPdfFileWithIText(byte[] fileContent, String paperName, String year) {
        List<ExamQuestion> questions = new ArrayList<>();
        
        try {
            // 创建PdfReader对象
            PdfReader reader = new PdfReader(fileContent);
            
            // 提取每一页的文本并作为单独的"题目"保存
            for (int i = 1; i <= reader.getNumberOfPages(); i++) {
                TextExtractionStrategy strategy = new SimpleTextExtractionStrategy();
                String pageText = PdfTextExtractor.getTextFromPage(reader, i, strategy);
                
                LOGGER.info("提取PDF第{}页文本，长度: {}", i, pageText.length());
                
                // 创建题目对象，使用页码作为题号
                ExamQuestion question = new ExamQuestion();
                question.setQuestionNumber("页面" + i);
                question.setQuestionType("页面内容");
                question.setContent(pageText);
                question.setYear(year);
                question.setCreateTime(new Date());
                question.setUpdateTime(new Date());
                question.setUseImageOnly(false);
                
                questions.add(question);
            }
            
            // 关闭reader
            reader.close();
            
            LOGGER.info("文件处理完成，共提取 {} 页内容", questions.size());
            
        } catch (Exception e) {
            LOGGER.error("使用iText处理PDF文件失败: {}", e.getMessage(), e);
        }
        
        return questions;
    }
    
    /**
     * 从文本中识别题目信息
     * @param text 文本内容
     * @return 题目信息列表
     */
    private List<SimpleQuestionInfo> identifyQuestionsFromText(String text) {
        List<SimpleQuestionInfo> questionInfoList = new ArrayList<>();
        
        try {
            // 使用正则表达式匹配题目编号
            Matcher matcher = QUESTION_NUMBER_PATTERN.matcher(text);
            
            while (matcher.find()) {
                String questionNumber = matcher.group(1);
                
                // 查找题目类型
                String questionType = "未知";
                int startPos = matcher.start();
                int endPos = Math.min(startPos + 50, text.length());
                String nearbyText = text.substring(startPos, endPos);
                
                Matcher typeMatcher = QUESTION_TYPE_PATTERN.matcher(nearbyText);
                if (typeMatcher.find()) {
                    questionType = typeMatcher.group(1);
                }
                
                // 创建题目信息
                SimpleQuestionInfo questionInfo = new SimpleQuestionInfo();
                questionInfo.setQuestionNumber(questionNumber);
                questionInfo.setQuestionType(questionType);
                
                // 检查是否已存在相同编号的题目
                boolean exists = false;
                for (SimpleQuestionInfo existing : questionInfoList) {
                    if (existing.getQuestionNumber().equals(questionNumber)) {
                        exists = true;
                        break;
                    }
                }
                
                if (!exists) {
                    questionInfoList.add(questionInfo);
                    LOGGER.info("识别到题目: 编号={}, 类型={}", 
                            questionNumber, questionType);
                }
            }
            
            // 对识别出的题目信息进行排序
            if (!questionInfoList.isEmpty()) {
                // 按题目编号排序
                questionInfoList.sort(Comparator.comparing(q -> Integer.parseInt(q.getQuestionNumber())));
            }
            
        } catch (Exception e) {
            LOGGER.error("从文本中识别题目失败: {}", e.getMessage(), e);
        }
        
        return questionInfoList;
    }
    
    /**
     * 提取每个题目的内容
     * @param fullText 完整文本
     * @param questionInfoList 题目信息列表
     * @return 题目编号到内容的映射
     */
    private Map<String, String> extractQuestionContents(String fullText, List<SimpleQuestionInfo> questionInfoList) {
        Map<String, String> questionContents = new HashMap<>();
        
        try {
            // 如果没有题目，直接返回空映射
            if (questionInfoList.isEmpty()) {
                return questionContents;
            }
            
            // 查找所有题目编号在文本中的位置
            List<Integer> positions = new ArrayList<>();
            List<String> numbers = new ArrayList<>();
            
            for (SimpleQuestionInfo questionInfo : questionInfoList) {
                String pattern = "\\b" + questionInfo.getQuestionNumber() + "[.、]\\s*";
                Matcher matcher = Pattern.compile(pattern).matcher(fullText);
                
                if (matcher.find()) {
                    positions.add(matcher.start());
                    numbers.add(questionInfo.getQuestionNumber());
                }
            }
            
            // 按位置排序
            List<Integer> sortedIndices = new ArrayList<>();
            for (int i = 0; i < positions.size(); i++) {
                sortedIndices.add(i);
            }
            sortedIndices.sort(Comparator.comparing(positions::get));
            
            // 提取每个题目的内容
            for (int i = 0; i < sortedIndices.size(); i++) {
                int index = sortedIndices.get(i);
                String questionNumber = numbers.get(index);
                int startPos = positions.get(index);
                
                // 确定结束位置（下一个题目的开始位置或文本结束）
                int endPos;
                if (i < sortedIndices.size() - 1) {
                    int nextIndex = sortedIndices.get(i + 1);
                    endPos = positions.get(nextIndex);
                } else {
                    endPos = fullText.length();
                }
                
                // 提取题目内容
                String content = fullText.substring(startPos, endPos).trim();
                
                // 存储题目内容
                questionContents.put(questionNumber, content);
            }
            
        } catch (Exception e) {
            LOGGER.error("提取题目内容失败: {}", e.getMessage(), e);
        }
        
        return questionContents;
    }
    
    @Override
    @Transactional
    public Long savePaperAndQuestions(String paperName, String year, List<ExamQuestion> questions) {
        try {
            // 保存试卷
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO exam_paper (paper_name, year, question_count) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
                );
                ps.setString(1, paperName);
                ps.setString(2, year);
                ps.setInt(3, questions.size());
                return ps;
            }, keyHolder);
            
            Long paperId = keyHolder.getKey().longValue();
            
            // 保存题目
            for (ExamQuestion question : questions) {
                // 设置paperId
                question.setPaperId(paperId);
                
                jdbcTemplate.update(
                    "INSERT INTO exam_question (paper_id, question_number, question_type, content, image_data, year, use_image_only) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    paperId,
                    question.getQuestionNumber(),
                    question.getQuestionType(),
                    question.getContent(),
                    question.getImageData(),
                    year,
                    question.getUseImageOnly() != null ? question.getUseImageOnly() : false
                );
            }
            
            LOGGER.info("保存试卷成功: id={}, 试卷名称={}, 年份={}, 题目数量={}", paperId, paperName, year, questions.size());
            
            return paperId;
        } catch (Exception e) {
            LOGGER.error("保存试卷失败: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    @Override
    public List<ExamPaper> getPaperList() {
        return jdbcTemplate.query(
            "SELECT * FROM exam_paper ORDER BY create_time DESC",
            (rs, rowNum) -> {
                ExamPaper paper = new ExamPaper();
                paper.setId(rs.getLong("id"));
                paper.setPaperName(rs.getString("paper_name"));
                paper.setYear(rs.getString("year"));
                paper.setQuestionCount(rs.getInt("question_count"));
                paper.setCreateTime(rs.getTimestamp("create_time"));
                paper.setUpdateTime(rs.getTimestamp("update_time"));
                return paper;
            }
        );
    }
    
    @Override
    public ExamPaper getPaperDetail(Long paperId) {
        // 获取试卷信息
        ExamPaper paper = jdbcTemplate.queryForObject(
            "SELECT * FROM exam_paper WHERE id = ?",
            new Object[]{paperId},
            (rs, rowNum) -> {
                ExamPaper p = new ExamPaper();
                p.setId(rs.getLong("id"));
                p.setPaperName(rs.getString("paper_name"));
                p.setYear(rs.getString("year"));
                p.setQuestionCount(rs.getInt("question_count"));
                p.setCreateTime(rs.getTimestamp("create_time"));
                p.setUpdateTime(rs.getTimestamp("update_time"));
                return p;
            }
        );
        
        if (paper != null) {
            // 获取题目列表
            List<ExamQuestion> questions = jdbcTemplate.query(
                "SELECT * FROM exam_question WHERE paper_id = ?",
                new Object[]{paperId},
                (rs, rowNum) -> {
                    ExamQuestion q = new ExamQuestion();
                    q.setId(rs.getLong("id"));
                    q.setQuestionNumber(rs.getString("question_number"));
                    q.setQuestionType(rs.getString("question_type"));
                    q.setContent(rs.getString("content"));
                    q.setImageData(rs.getString("image_data"));
                    q.setPaperId(rs.getLong("paper_id"));
                    q.setYear(rs.getString("year"));
                    q.setCreateTime(rs.getTimestamp("create_time"));
                    q.setUpdateTime(rs.getTimestamp("update_time"));
                    q.setUseImageOnly(rs.getBoolean("use_image_only"));
                    return q;
                }
            );
            
            // 按照题目编号进行数字感知排序
            questions.sort((q1, q2) -> {
                try {
                    // 尝试提取数字部分进行比较
                    int num1 = extractNumber(q1.getQuestionNumber());
                    int num2 = extractNumber(q2.getQuestionNumber());
                    return Integer.compare(num1, num2);
                } catch (Exception e) {
                    // 如果提取失败，则按字符串比较
                    return q1.getQuestionNumber().compareTo(q2.getQuestionNumber());
                }
            });
            
            paper.setQuestions(questions);
        }
        
        return paper;
    }
    
    /**
     * 从字符串中提取数字部分
     * @param str 包含数字的字符串
     * @return 提取出的数字
     */
    private int extractNumber(String str) {
        if (str == null || str.isEmpty()) {
            return 0;
        }
        
        // 提取字符串中的数字部分
        StringBuilder sb = new StringBuilder();
        for (char c : str.toCharArray()) {
            if (Character.isDigit(c)) {
                sb.append(c);
            }
        }
        
        if (sb.length() > 0) {
            return Integer.parseInt(sb.toString());
        }
        
        return 0;
    }
    
    @Override
    @Transactional
    public boolean deletePaper(Long paperId) {
        int result = jdbcTemplate.update("DELETE FROM exam_paper WHERE id = ?", paperId);
        return result > 0;
    }
    
    @Override
    @Transactional
    public boolean updateQuestion(ExamQuestion question) {
        try {
            int result = jdbcTemplate.update(
                "UPDATE exam_question SET question_number = ?, question_type = ?, content = ?, " +
                "image_data = ?, paper_id = ?, year = ?, use_image_only = ?, update_time = ? WHERE id = ?",
                question.getQuestionNumber(),
                question.getQuestionType(),
                question.getContent(),
                question.getImageData(),
                question.getPaperId(),
                question.getYear(),
                question.getUseImageOnly() != null ? question.getUseImageOnly() : false,
                new Timestamp(System.currentTimeMillis()),
                question.getId()
            );
            
            LOGGER.info("更新题目{}: id={}", result > 0 ? "成功" : "失败", question.getId());
            
            return result > 0;
        } catch (Exception e) {
            LOGGER.error("更新题目失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    @Transactional
    public boolean deleteQuestion(Long questionId) {
        try {
            // 获取题目所属的试卷ID
            Long paperId = jdbcTemplate.queryForObject(
                "SELECT paper_id FROM exam_question WHERE id = ?",
                new Object[]{questionId},
                Long.class
            );
            
            // 删除题目
            int result = jdbcTemplate.update("DELETE FROM exam_question WHERE id = ?", questionId);
            
            if (result > 0 && paperId != null) {
                // 更新试卷的题目数量
                jdbcTemplate.update(
                    "UPDATE exam_paper SET question_count = (SELECT COUNT(*) FROM exam_question WHERE paper_id = ?) WHERE id = ?",
                    paperId, paperId
                );
            }
            
            LOGGER.info("删除题目{}: id={}", result > 0 ? "成功" : "失败", questionId);
            return result > 0;
        } catch (Exception e) {
            LOGGER.error("删除题目失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public ExamQuestion getQuestionById(Long questionId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT * FROM exam_question WHERE id = ?",
                new Object[]{questionId},
                (rs, rowNum) -> {
                    ExamQuestion q = new ExamQuestion();
                    q.setId(rs.getLong("id"));
                    q.setQuestionNumber(rs.getString("question_number"));
                    q.setQuestionType(rs.getString("question_type"));
                    q.setContent(rs.getString("content"));
                    q.setImageData(rs.getString("image_data"));
                    q.setPaperId(rs.getLong("paper_id"));
                    q.setYear(rs.getString("year"));
                    q.setCreateTime(rs.getTimestamp("create_time"));
                    q.setUpdateTime(rs.getTimestamp("update_time"));
                    q.setUseImageOnly(rs.getBoolean("use_image_only"));
                    return q;
                }
            );
        } catch (Exception e) {
            LOGGER.error("获取题目失败: {}", e.getMessage(), e);
            return null;
        }
    }
} 