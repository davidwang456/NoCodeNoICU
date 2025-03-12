package com.davidwang456.excel.service.impl;

import com.davidwang456.excel.model.ExamPaper;
import com.davidwang456.excel.model.ExamQuestion;
import com.davidwang456.excel.model.OCRResult;
import com.davidwang456.excel.service.OCRService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
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
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

/**
 * OCR服务实现类
 */
@Service
public class OCRServiceImpl implements OCRService {
    private static final Logger LOGGER = LoggerFactory.getLogger(OCRServiceImpl.class);
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private OCRImageProcessor imageProcessor;
    
    private Tesseract tesseract;
    
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
            
            LOGGER.info("OCR相关表创建/更新成功");
            
            // 初始化Tesseract
            initTesseract();
            
        } catch (Exception e) {
            LOGGER.error("创建/更新OCR相关表失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 初始化Tesseract OCR引擎
     */
    private void initTesseract() {
        try {
            tesseract = new Tesseract();
            
            // 检查tessdata目录是否存在，不存在则创建
            Path tessdataPath = Paths.get("tessdata");
            if (!Files.exists(tessdataPath)) {
                Files.createDirectories(tessdataPath);
                LOGGER.info("创建tessdata目录: {}", tessdataPath.toAbsolutePath());
            }
            
            // 设置tessdata路径
            tesseract.setDatapath(tessdataPath.toAbsolutePath().toString());
            
            // 设置语言为中文和英文
            tesseract.setLanguage("chi_sim+eng");
            
            // 设置OCR引擎模式
            tesseract.setOcrEngineMode(1);
            
            // 设置页面分割模式
            tesseract.setPageSegMode(3);
            
            // 设置白名单字符
            tesseract.setTessVariable("tessedit_char_whitelist", "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz.,;:()[]{}?!+-*/=<>\"'\\|_@#$%^&~`");
            
            LOGGER.info("Tesseract OCR引擎初始化成功");
        } catch (Exception e) {
            LOGGER.error("Tesseract OCR引擎初始化失败: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public OCRResult processOCR(MultipartFile file, String paperName, String year) {
        OCRResult result = new OCRResult();
        
        try {
            LOGGER.info("接收到OCR识别请求: 文件={}, 试卷名称={}, 年份={}", file.getOriginalFilename(), paperName, year);
            
            // 读取文件内容
            byte[] fileContent = file.getBytes();
            String fileType = file.getContentType();
            
            // 根据文件类型选择不同的处理方式
            List<ExamQuestion> questions = new ArrayList<>();
            
            if ("application/pdf".equals(fileType)) {
                // 处理PDF文件
                questions = processPdfFileWithOCR(fileContent, paperName, year);
            } else if (fileType != null && fileType.startsWith("image/")) {
                // 处理图片文件
                questions = processImageFileWithOCR(fileContent, paperName, year);
            } else {
                result.setSuccess(false);
                result.setErrorMessage("不支持的文件类型: " + fileType);
                return result;
            }
            
            result.setSuccess(true);
            result.setQuestions(questions);
            
            LOGGER.info("文件处理完成，识别出 {} 道题目", questions.size());
            
        } catch (Exception e) {
            LOGGER.error("OCR处理失败: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setErrorMessage("OCR处理失败: " + e.getMessage());
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
     * 识别题目信息
     * @param image 图像
     * @param pageIndex 页码索引
     * @return 题目信息列表
     */
    private List<SimpleQuestionInfo> identifyQuestionInfo(BufferedImage image, int pageIndex) {
        List<SimpleQuestionInfo> questionInfoList = new ArrayList<>();
        
        try {
            // 将图像分割成多个区域进行OCR识别
            int regionHeight = 100; // 每个区域的高度
            int overlap = 20; // 重叠区域的高度，避免题目被分割
            
            for (int y = 0; y < image.getHeight(); y += (regionHeight - overlap)) {
                int height = Math.min(regionHeight, image.getHeight() - y);
                if (height < 30) continue; // 忽略太小的区域
                
                BufferedImage region = image.getSubimage(0, y, image.getWidth(), height);
                
                // 对区域进行OCR识别
                String text = tesseract.doOCR(region);
                
                // 在识别的文本中查找题目编号
                Matcher matcher = QUESTION_NUMBER_PATTERN.matcher(text);
                while (matcher.find()) {
                    String questionNumber = matcher.group(1);
                    
                    // 查找题目类型
                    String questionType = "未知";
                    Matcher typeMatcher = QUESTION_TYPE_PATTERN.matcher(text);
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
            }
            
            // 对识别出的题目信息进行排序
            if (!questionInfoList.isEmpty()) {
                // 按题目编号排序
                questionInfoList.sort(Comparator.comparing(q -> Integer.parseInt(q.getQuestionNumber())));
            }
            
        } catch (Exception e) {
            LOGGER.error("识别题目信息失败: {}", e.getMessage(), e);
        }
        
        return questionInfoList;
    }
    
    /**
     * 处理PDF文件 - 结合OCR识别题目编号和图像切分
     */
    private List<ExamQuestion> processPdfFileWithOCR(byte[] fileContent, String paperName, String year) {
        List<ExamQuestion> questions = new ArrayList<>();
        
        try {
            // 使用PDFBox加载PDF文件
            PDDocument document = PDDocument.load(new ByteArrayInputStream(fileContent));
            PDFRenderer renderer = new PDFRenderer(document);
            
            // 存储所有页面的图像和题目信息
            List<BufferedImage> allPageImages = new ArrayList<>();
            List<List<SimpleQuestionInfo>> allPageQuestionInfo = new ArrayList<>();
            
            // 渲染每一页并识别题目信息
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                // 渲染页面为图像
                BufferedImage image = renderer.renderImageWithDPI(i, 300);
                allPageImages.add(image);
                
                // 识别题目信息
                List<SimpleQuestionInfo> pageQuestionInfo = identifyQuestionInfo(image, i);
                allPageQuestionInfo.add(pageQuestionInfo);
                
                LOGGER.info("处理PDF第{}页，识别出{}个题目", i + 1, pageQuestionInfo.size());
            }
            
            // 关闭文档
            document.close();
            
            // 检查是否识别出题目信息
            boolean hasQuestionInfo = allPageQuestionInfo.stream().anyMatch(list -> !list.isEmpty());
            if (!hasQuestionInfo) {
                LOGGER.warn("未能识别出题目信息，使用直接切图方式");
                return processPdfFileByImage(fileContent, paperName, year);
            }
            
            // 处理识别出的题目信息，生成题目
            int questionCount = 0;
            for (int pageIndex = 0; pageIndex < allPageImages.size(); pageIndex++) {
                BufferedImage pageImage = allPageImages.get(pageIndex);
                List<SimpleQuestionInfo> pageQuestionInfo = allPageQuestionInfo.get(pageIndex);
                
                for (SimpleQuestionInfo questionInfo : pageQuestionInfo) {
                    try {
                        // 直接使用原始图像
                        BufferedImage questionImage = pageImage;
                        
                        // 为了提高性能，我们可以只处理图像的一部分，而不是整个页面
                        // 例如，我们可以根据页面高度估计每个题目的大致区域
                        int estimatedHeight = pageImage.getHeight() / Math.max(1, pageQuestionInfo.size());
                        int infoIndex = pageQuestionInfo.indexOf(questionInfo);
                        int startY = Math.max(0, infoIndex * estimatedHeight - 50); // 向上扩展50像素
                        int endY = Math.min(pageImage.getHeight(), (infoIndex + 1) * estimatedHeight + 50); // 向下扩展50像素
                        
                        // 提取题目区域
                        BufferedImage questionRegion;
                        try {
                            questionRegion = pageImage.getSubimage(0, startY, pageImage.getWidth(), endY - startY);
                        } catch (Exception e) {
                            LOGGER.warn("无法提取题目区域，使用整个页面: {}", e.getMessage());
                            questionRegion = pageImage;
                        }
                        
                        // 增强图像质量 - 只处理题目区域，而不是整个页面
                        BufferedImage enhancedImage = enhanceMathImage(questionRegion);
                        
                        // 保存图像为Base64
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write(enhancedImage, "png", baos);
                        String base64Image = Base64.getEncoder().encodeToString(baos.toByteArray());
                        
                        // 创建题目
                        ExamQuestion question = new ExamQuestion();
                        question.setQuestionNumber(questionInfo.getQuestionNumber());
                        question.setQuestionType(questionInfo.getQuestionType());
                        question.setContent("题目 " + questionInfo.getQuestionNumber());
                        question.setImageData("data:image/png;base64," + base64Image);
                        question.setYear(year);
                        question.setCreateTime(new Date());
                        question.setUpdateTime(new Date());
                        question.setUseImageOnly(true);
                        
                        questions.add(question);
                        questionCount++;
                    } catch (Exception e) {
                        LOGGER.error("处理题目时出错: 页码={}, 题号={}, 错误={}", 
                                pageIndex + 1, questionInfo.getQuestionNumber(), e.getMessage());
                    }
                }
            }
            
            // 如果没有识别出题目信息，则使用均匀切分的方式
            if (questions.isEmpty()) {
                LOGGER.warn("未能识别出题目信息，使用均匀切分方式");
                return processPdfFileByImage(fileContent, paperName, year);
            }
            
        } catch (Exception e) {
            LOGGER.error("处理PDF文件失败: {}", e.getMessage(), e);
        }
        
        return questions;
    }
    
    /**
     * 处理图片文件 - 结合OCR识别题目编号和图像切分
     */
    private List<ExamQuestion> processImageFileWithOCR(byte[] fileContent, String paperName, String year) {
        List<ExamQuestion> questions = new ArrayList<>();
        
        try {
            // 读取图片
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(fileContent));
            
            if (image == null) {
                LOGGER.error("无法读取图片文件");
                return questions;
            }
            
            // 识别题目信息
            List<SimpleQuestionInfo> questionInfoList = identifyQuestionInfo(image, 0);
            LOGGER.info("识别出 {} 个题目信息", questionInfoList.size());
            
            // 处理识别出的题目信息，生成题目
            for (SimpleQuestionInfo questionInfo : questionInfoList) {
                try {
                    // 直接使用原始图像
                    BufferedImage questionImage = image;
                    
                    // 增强图像质量
                    BufferedImage enhancedImage = enhanceMathImage(questionImage);
                    
                    // 保存图像为Base64
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(enhancedImage, "png", baos);
                    String base64Image = Base64.getEncoder().encodeToString(baos.toByteArray());
                    
                    // 创建题目
                    ExamQuestion question = new ExamQuestion();
                    question.setQuestionNumber(questionInfo.getQuestionNumber());
                    question.setQuestionType(questionInfo.getQuestionType());
                    question.setContent("题目 " + questionInfo.getQuestionNumber());
                    question.setImageData("data:image/png;base64," + base64Image);
                    question.setYear(year);
                    question.setCreateTime(new Date());
                    question.setUpdateTime(new Date());
                    question.setUseImageOnly(true);
                    
                    questions.add(question);
                } catch (Exception e) {
                    LOGGER.error("处理题目时出错: 题号={}, 错误={}", 
                            questionInfo.getQuestionNumber(), e.getMessage());
                }
            }
            
            // 如果没有识别出题目信息，则使用均匀切分的方式
            if (questions.isEmpty()) {
                LOGGER.warn("未能识别出题目信息，使用均匀切分方式");
                return processImageFileByImage(fileContent, paperName, year);
            }
            
        } catch (Exception e) {
            LOGGER.error("处理图片文件失败: {}", e.getMessage(), e);
        }
        
        return questions;
    }
    
    /**
     * 处理PDF文件 - 直接切图方式
     */
    private List<ExamQuestion> processPdfFileByImage(byte[] fileContent, String paperName, String year) {
        // 使用OCRImageProcessor处理PDF文件
        return imageProcessor.processPdfFileByImage(fileContent, paperName, year);
    }
    
    /**
     * 处理图片文件 - 直接切图方式
     */
    private List<ExamQuestion> processImageFileByImage(byte[] fileContent, String paperName, String year) {
        // 使用OCRImageProcessor处理图片文件
        return imageProcessor.processImageFileByImage(fileContent, paperName, year);
    }
    
    /**
     * 增强数学图像的处理
     */
    private BufferedImage enhanceMathImage(BufferedImage image) {
        // 使用OCRImageProcessor中的enhanceMathImage方法
        return imageProcessor.enhanceMathImage(image);
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