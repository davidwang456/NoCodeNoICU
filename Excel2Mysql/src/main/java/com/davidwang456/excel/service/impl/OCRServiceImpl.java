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
import org.springframework.dao.EmptyResultDataAccessException;
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
import java.sql.SQLException;
import net.sourceforge.tess4j.ITessAPI;

/**
 * OCR服务实现类
 */
@Service
public class OCRServiceImpl implements OCRService {
    private static final Logger LOGGER = LoggerFactory.getLogger(OCRServiceImpl.class);
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
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
                    "question_count INT DEFAULT 0, " +
                    "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                    ")");
            
            // 创建exam_question表
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS exam_question (" +
                    "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                    "paper_id BIGINT NOT NULL, " +
                    "page_number INT NOT NULL, " +
                    "content TEXT NOT NULL, " +
                    "image_data LONGTEXT, " +
                    "paper_name VARCHAR(255), " +
                    "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (paper_id) REFERENCES exam_paper(id) ON DELETE CASCADE" +
                    ")");
            
            // 检查是否需要移除exam_paper表中的year列
            try {
                jdbcTemplate.execute("SELECT year FROM exam_paper LIMIT 1");
                // 如果没有抛出异常，说明列存在，需要移除
                LOGGER.info("移除exam_paper表中的year列");
                jdbcTemplate.execute("ALTER TABLE exam_paper DROP COLUMN year");
            } catch (Exception e) {
                // 列不存在，无需处理
            }
            
            // 检查是否需要移除exam_question表中的year列
            try {
                jdbcTemplate.execute("SELECT year FROM exam_question LIMIT 1");
                // 如果没有抛出异常，说明列存在，需要移除
                LOGGER.info("移除exam_question表中的year列");
                jdbcTemplate.execute("ALTER TABLE exam_question DROP COLUMN year");
            } catch (Exception e) {
                // 列不存在，无需处理
            }
            
            // 检查是否需要移除exam_question表中的question_number列
            try {
                jdbcTemplate.execute("SELECT question_number FROM exam_question LIMIT 1");
                // 如果没有抛出异常，说明列存在，需要移除
                LOGGER.info("移除exam_question表中的question_number列");
                jdbcTemplate.execute("ALTER TABLE exam_question DROP COLUMN question_number");
            } catch (Exception e) {
                // 列不存在，无需处理
            }
            
            // 检查是否需要移除exam_question表中的question_type列
            try {
                jdbcTemplate.execute("SELECT question_type FROM exam_question LIMIT 1");
                // 如果没有抛出异常，说明列存在，需要移除
                LOGGER.info("移除exam_question表中的question_type列");
                jdbcTemplate.execute("ALTER TABLE exam_question DROP COLUMN question_type");
            } catch (Exception e) {
                // 列不存在，无需处理
            }
            
            // 检查是否需要移除exam_question表中的use_image_only列
            try {
                jdbcTemplate.execute("SELECT use_image_only FROM exam_question LIMIT 1");
                // 如果没有抛出异常，说明列存在，需要移除
                LOGGER.info("移除exam_question表中的use_image_only列");
                jdbcTemplate.execute("ALTER TABLE exam_question DROP COLUMN use_image_only");
            } catch (Exception e) {
                // 列不存在，无需处理
            }
            
            // 检查是否需要添加page_number列
            try {
                jdbcTemplate.execute("SELECT page_number FROM exam_question LIMIT 1");
            } catch (Exception e) {
                // 列不存在，添加它
                LOGGER.info("添加page_number列到exam_question表");
                jdbcTemplate.execute("ALTER TABLE exam_question ADD COLUMN page_number INT NOT NULL DEFAULT 1");
            }
            
            // 检查paper_id是否为NOT NULL
            try {
                jdbcTemplate.execute("ALTER TABLE exam_question MODIFY paper_id BIGINT NOT NULL");
                LOGGER.info("修改paper_id为NOT NULL约束");
            } catch (Exception e) {
                // 忽略错误
            }
            
            // 检查是否需要添加paper_name列
            try {
                jdbcTemplate.execute("ALTER TABLE exam_question ADD COLUMN IF NOT EXISTS paper_name VARCHAR(255)");
            } catch (Exception e) {
                LOGGER.warn("添加paper_name列失败，可能已存在: {}", e.getMessage());
            }
            
            LOGGER.info("OCR相关表创建/更新成功");
            
            // 初始化Tesseract
            tesseract = new Tesseract();
            
            // 设置Tesseract数据目录，可以根据实际情况修改
            String datapath = "tessdata";
            tesseract.setDatapath(datapath);
            
            // 设置OCR语言，chi_sim是中文简体，eng是英文
            tesseract.setLanguage("chi_sim+eng");
            
            // 设置识别模式为完整页面
            tesseract.setPageSegMode(ITessAPI.TessPageSegMode.PSM_AUTO);
            
            LOGGER.info("Tesseract OCR引擎初始化成功");
        } catch (Exception e) {
            LOGGER.error("初始化OCR相关表失败: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public OCRResult processOCR(MultipartFile file, String paperName, String year) {
        OCRResult result = new OCRResult();
        
        try {
            LOGGER.info("接收到OCR识别请求: 文件={}, 文件名称={}, 年份={}", file.getOriginalFilename(), paperName, year);
            
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
     * 处理PDF文件 - 结合OCR识别题目编号和图像切分
     */
    private List<ExamQuestion> processPdfFileWithOCR(byte[] fileContent, String paperName, String year) {
        List<ExamQuestion> questions = new ArrayList<>();
        
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(fileContent))) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();
            
            LOGGER.info("PDF文件共有 {} 页", pageCount);
            
            // 处理每一页
            List<BufferedImage> allPageImages = new ArrayList<>();
            List<List<QuestionBoundary>> allPageBoundaries = new ArrayList<>();
            
            for (int i = 0; i < pageCount; i++) {
                LOGGER.info("处理第 {} 页", i + 1);
                
                try {
                    // 将PDF页面渲染为图像
                    BufferedImage image = pdfRenderer.renderImageWithDPI(i, 300);
                    allPageImages.add(image);
                    
                    // 识别当前页的题目边界
                    List<QuestionBoundary> pageBoundaries = identifyQuestionBoundaries(image, i);
                    allPageBoundaries.add(pageBoundaries);
                    
                    LOGGER.info("第 {} 页识别出 {} 个题目边界", i + 1, pageBoundaries.size());
                } catch (Exception e) {
                    LOGGER.error("处理第 {} 页时出错: {}", i + 1, e.getMessage(), e);
                    // 添加一个空的边界列表，确保索引一致性
                    allPageBoundaries.add(new ArrayList<>());
                    // 添加一个空白图像，确保索引一致性
                    BufferedImage blankImage = new BufferedImage(800, 1000, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g2d = blankImage.createGraphics();
                    g2d.setColor(Color.WHITE);
                    g2d.fillRect(0, 0, 800, 1000);
                    g2d.dispose();
                    allPageImages.add(blankImage);
                }
            }
            
            // 处理识别出的题目边界，生成题目
            int questionIndex = 0;
            for (int pageIndex = 0; pageIndex < allPageImages.size(); pageIndex++) {
                BufferedImage pageImage = allPageImages.get(pageIndex);
                List<QuestionBoundary> pageBoundaries = allPageBoundaries.get(pageIndex);
                
                for (QuestionBoundary boundary : pageBoundaries) {
                    try {
                        // 根据边界切割图像
                        BufferedImage questionImage = extractQuestionImage(pageImage, boundary);
                        
                        // 增强图像质量
                        BufferedImage enhancedImage = enhanceMathImage(questionImage);
                        
                        // 保存图像为Base64
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write(enhancedImage, "png", baos);
                        String base64Image = Base64.getEncoder().encodeToString(baos.toByteArray());
                        
                        // 创建题目
                        ExamQuestion question = new ExamQuestion();
                        question.setPageNumber(Integer.parseInt(boundary.getQuestionNumber()));
                        question.setContent("题目 " + boundary.getQuestionNumber());
                        question.setImageData("data:image/png;base64," + base64Image);
                        question.setCreateTime(new Date());
                        question.setUpdateTime(new Date());
                        
                        questions.add(question);
                        questionIndex++;
                    } catch (Exception e) {
                        LOGGER.error("处理题目时出错: 页码={}, 题号={}, 错误={}", 
                                pageIndex + 1, boundary.getQuestionNumber(), e.getMessage());
                    }
                }
            }
            
            // 如果没有识别出题目边界，则使用均匀切分的方式
            if (questions.isEmpty()) {
                LOGGER.warn("未能识别出题目边界，使用均匀切分方式");
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
            
            // 识别题目边界
            List<QuestionBoundary> boundaries = identifyQuestionBoundaries(image, 0);
            LOGGER.info("识别出 {} 个题目边界", boundaries.size());
            
            // 处理识别出的题目边界，生成题目
            for (QuestionBoundary boundary : boundaries) {
                try {
                    // 根据边界切割图像
                    BufferedImage questionImage = extractQuestionImage(image, boundary);
                    
                    // 增强图像质量
                    BufferedImage enhancedImage = enhanceMathImage(questionImage);
                    
                    // 保存图像为Base64
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(enhancedImage, "png", baos);
                    String base64Image = Base64.getEncoder().encodeToString(baos.toByteArray());
                    
                    // 创建题目
                    ExamQuestion question = new ExamQuestion();
                    question.setPageNumber(Integer.parseInt(boundary.getQuestionNumber()));
                    question.setContent("题目 " + boundary.getQuestionNumber());
                    question.setImageData("data:image/png;base64," + base64Image);
                    question.setCreateTime(new Date());
                    question.setUpdateTime(new Date());
                    
                    questions.add(question);
                } catch (Exception e) {
                    LOGGER.error("处理题目时出错: 题号={}, 错误={}", 
                            boundary.getQuestionNumber(), e.getMessage());
                }
            }
            
            // 如果没有识别出题目边界，则使用均匀切分的方式
            if (questions.isEmpty()) {
                LOGGER.warn("未能识别出题目边界，使用均匀切分方式");
                return processImageFileByImage(fileContent, paperName, year);
            }
            
        } catch (Exception e) {
            LOGGER.error("处理图片文件失败: {}", e.getMessage(), e);
        }
        
        return questions;
    }
    
    /**
     * 识别题目边界
     * @param image 图像
     * @param pageIndex 页码索引
     * @return 题目边界列表
     */
    private List<QuestionBoundary> identifyQuestionBoundaries(BufferedImage image, int pageIndex) {
        List<QuestionBoundary> boundaries = new ArrayList<>();
        
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
                    
                    // 创建题目边界
                    QuestionBoundary boundary = new QuestionBoundary();
                    boundary.setQuestionNumber(questionNumber);
                    boundary.setQuestionType(questionType);
                    boundary.setStartY(y);
                    boundary.setEndY(y + height);
                    boundary.setPageIndex(pageIndex);
                    
                    // 检查是否已存在相同编号的题目
                    boolean exists = false;
                    for (QuestionBoundary existing : boundaries) {
                        if (existing.getQuestionNumber().equals(questionNumber)) {
                            exists = true;
                            break;
                        }
                    }
                    
                    if (!exists) {
                        boundaries.add(boundary);
                        LOGGER.info("识别到题目: 编号={}, 类型={}, 位置={}~{}", 
                                questionNumber, questionType, boundary.getStartY(), boundary.getEndY());
                    }
                }
            }
            
            // 对识别出的边界进行排序和处理
            if (!boundaries.isEmpty()) {
                // 按题目编号排序
                boundaries.sort(Comparator.comparing(b -> Integer.parseInt(b.getQuestionNumber())));
                
                // 计算每个题目的结束位置（下一个题目的开始位置）
                for (int i = 0; i < boundaries.size() - 1; i++) {
                    QuestionBoundary current = boundaries.get(i);
                    QuestionBoundary next = boundaries.get(i + 1);
                    
                    // 如果在同一页，则当前题目的结束位置是下一个题目的开始位置
                    if (current.getPageIndex() == next.getPageIndex()) {
                        current.setEndY(next.getStartY());
                    } else {
                        // 如果不在同一页，则当前题目的结束位置是当前页的底部
                        current.setEndY(image.getHeight());
                    }
                }
                
                // 最后一个题目的结束位置是图像的底部
                QuestionBoundary last = boundaries.get(boundaries.size() - 1);
                last.setEndY(image.getHeight());
            }
            
        } catch (Exception e) {
            LOGGER.error("识别题目边界失败: {}", e.getMessage(), e);
        }
        
        return boundaries;
    }
    
    /**
     * 根据边界提取题目图像
     * @param image 原始图像
     * @param boundary 题目边界
     * @return 题目图像
     */
    private BufferedImage extractQuestionImage(BufferedImage image, QuestionBoundary boundary) {
        int startY = boundary.getStartY();
        int endY = boundary.getEndY();
        
        // 确保边界在图像范围内
        startY = Math.max(0, startY);
        endY = Math.min(image.getHeight(), endY);
        
        // 确保高度大于0
        if (endY <= startY) {
            LOGGER.warn("题目边界无效: startY={}, endY={}, 使用默认高度", startY, endY);
            endY = Math.min(startY + 100, image.getHeight());
        }
        
        // 再次检查边界是否有效
        if (startY >= image.getHeight() || endY <= 0 || endY - startY <= 0) {
            LOGGER.warn("无法提取题目图像: 边界超出图像范围, 返回空白图像");
            // 返回一个小的空白图像
            return new BufferedImage(image.getWidth(), 100, image.getType());
        }
        
        try {
            // 提取题目图像
            return image.getSubimage(0, startY, image.getWidth(), endY - startY);
        } catch (Exception e) {
            LOGGER.error("提取题目图像失败: {}, startY={}, endY={}, imageHeight={}", 
                    e.getMessage(), startY, endY, image.getHeight());
            // 返回一个小的空白图像
            return new BufferedImage(image.getWidth(), 100, image.getType());
        }
    }
    
    /**
     * 题目边界类
     */
    private static class QuestionBoundary {
        private String questionNumber;
        private String questionType;
        private int startY;
        private int endY;
        private int pageIndex;
        
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
        
        public int getStartY() {
            return startY;
        }
        
        public void setStartY(int startY) {
            this.startY = startY;
        }
        
        public int getEndY() {
            return endY;
        }
        
        public void setEndY(int endY) {
            this.endY = endY;
        }
        
        public int getPageIndex() {
            return pageIndex;
        }
        
        public void setPageIndex(int pageIndex) {
            this.pageIndex = pageIndex;
        }
    }
    
    /**
     * 处理PDF文件 - 直接切图方式
     */
    private List<ExamQuestion> processPdfFileByImage(byte[] fileContent, String paperName, String year) {
        List<ExamQuestion> questions = new ArrayList<>();
        
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(fileContent))) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();
            
            LOGGER.info("PDF文件共有 {} 页", pageCount);
            
            // 估计题目数量
            int estimatedQuestionCount = estimateQuestionCount(pageCount);
            LOGGER.info("估计题目数量: {}", estimatedQuestionCount);
            
            // 处理每一页
            List<BufferedImage> allPageImages = new ArrayList<>();
            for (int i = 0; i < pageCount; i++) {
                LOGGER.info("处理第 {} 页", i + 1);
                
                // 将PDF页面渲染为图像
                BufferedImage image = pdfRenderer.renderImageWithDPI(i, 300);
                allPageImages.add(image);
            }
            
            // 将所有页面合并为一个大图像
            BufferedImage mergedImage = mergeImages(allPageImages);
            
            // 增强图像质量
            BufferedImage enhancedImage = enhanceMathImage(mergedImage);
            
            // 按题目数量均匀切分图像
            List<BufferedImage> questionImages = splitImageEvenly(enhancedImage, estimatedQuestionCount);
            
            // 为每个切分的图像创建题目
            for (int i = 0; i < questionImages.size(); i++) {
                BufferedImage questionImage = questionImages.get(i);
                
                // 保存图像为Base64
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(questionImage, "png", baos);
                String base64Image = Base64.getEncoder().encodeToString(baos.toByteArray());
                
                // 创建题目
                ExamQuestion question = new ExamQuestion();
                question.setPageNumber(i + 1);
                question.setContent("题目 " + (i + 1));
                question.setImageData("data:image/png;base64," + base64Image);
                question.setCreateTime(new Date());
                question.setUpdateTime(new Date());
                
                questions.add(question);
            }
            
        } catch (Exception e) {
            LOGGER.error("处理PDF文件失败: {}", e.getMessage(), e);
        }
        
        return questions;
    }
    
    /**
     * 处理图片文件 - 直接切图方式
     */
    private List<ExamQuestion> processImageFileByImage(byte[] fileContent, String paperName, String year) {
        List<ExamQuestion> questions = new ArrayList<>();
        
        try {
            // 读取图片
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(fileContent));
            
            if (image == null) {
                LOGGER.error("无法读取图片文件");
                return questions;
            }
            
            // 增强图像质量
            BufferedImage enhancedImage = enhanceMathImage(image);
            
            // 估计题目数量
            int estimatedQuestionCount = estimateQuestionCount(1);
            LOGGER.info("估计题目数量: {}", estimatedQuestionCount);
            
            // 按题目数量均匀切分图像
            List<BufferedImage> questionImages = splitImageEvenly(enhancedImage, estimatedQuestionCount);
            
            // 为每个切分的图像创建题目
            for (int i = 0; i < questionImages.size(); i++) {
                BufferedImage questionImage = questionImages.get(i);
                
                // 保存图像为Base64
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(questionImage, "png", baos);
                String base64Image = Base64.getEncoder().encodeToString(baos.toByteArray());
                
                // 创建题目
                ExamQuestion question = new ExamQuestion();
                question.setPageNumber(i + 1);
                question.setContent("题目 " + (i + 1));
                question.setImageData("data:image/png;base64," + base64Image);
                question.setCreateTime(new Date());
                question.setUpdateTime(new Date());
                
                questions.add(question);
            }
            
        } catch (Exception e) {
            LOGGER.error("处理图片文件失败: {}", e.getMessage(), e);
        }
        
        return questions;
    }
    
    /**
     * 估计题目数量
     */
    private int estimateQuestionCount(int pageCount) {
        // 根据页数估计题目数量
        // 一般来说，一页文件包含5-10道题目
        return pageCount * 7; // 平均每页7道题
    }
    
    /**
     * 合并多个图像为一个大图像
     */
    private BufferedImage mergeImages(List<BufferedImage> images) {
        if (images.isEmpty()) {
            return null;
        }
        
        // 计算合并后的图像尺寸
        int maxWidth = 0;
        int totalHeight = 0;
        
        for (BufferedImage image : images) {
            maxWidth = Math.max(maxWidth, image.getWidth());
            totalHeight += image.getHeight();
        }
        
        // 创建合并后的图像
        BufferedImage mergedImage = new BufferedImage(maxWidth, totalHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = mergedImage.createGraphics();
        
        // 设置白色背景
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, maxWidth, totalHeight);
        
        // 绘制每个图像
        int y = 0;
        for (BufferedImage image : images) {
            g2d.drawImage(image, 0, y, null);
            y += image.getHeight();
        }
        
        g2d.dispose();
        
        return mergedImage;
    }
    
    /**
     * 均匀切分图像为指定数量的小图像
     */
    private List<BufferedImage> splitImageEvenly(BufferedImage image, int count) {
        List<BufferedImage> result = new ArrayList<>();
        
        int width = image.getWidth();
        int height = image.getHeight();
        
        // 每个题目的高度
        int questionHeight = height / count;
        
        // 确保最小高度
        questionHeight = Math.max(questionHeight, 100);
        
        // 实际可以切分的题目数量
        int actualCount = height / questionHeight;
        
        LOGGER.info("图像尺寸: {}x{}, 每题高度: {}, 实际题目数: {}", width, height, questionHeight, actualCount);
        
        // 切分图像
        for (int i = 0; i < actualCount; i++) {
            int y = i * questionHeight;
            int h = (i == actualCount - 1) ? (height - y) : questionHeight;
            
            BufferedImage questionImage = image.getSubimage(0, y, width, h);
            result.add(questionImage);
        }
        
        return result;
    }
    
    /**
     * 增强数学图像的处理
     */
    private BufferedImage enhanceMathImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        // 创建新图像
        BufferedImage enhancedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        // 应用自适应阈值处理，提高数学符号的清晰度
        int blockSize = 15; // 局部区域大小
        double c = 8; // 常数，用于调整阈值
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // 计算局部区域的平均值
                int sum = 0;
                int count = 0;
                
                for (int ny = Math.max(0, y - blockSize/2); ny < Math.min(height, y + blockSize/2 + 1); ny++) {
                    for (int nx = Math.max(0, x - blockSize/2); nx < Math.min(width, x + blockSize/2 + 1); nx++) {
                        int rgb = image.getRGB(nx, ny);
                        int gray = (rgb >> 16) & 0xff; // 假设是灰度图像
                        sum += gray;
                        count++;
                    }
                }
                
                double mean = sum / (double)count;
                
                // 获取当前像素值
                int rgb = image.getRGB(x, y);
                int gray = (rgb >> 16) & 0xff;
                
                // 应用自适应阈值
                int newRgb;
                if (gray < mean - c) {
                    newRgb = 0x000000; // 黑色
                } else {
                    newRgb = 0xffffff; // 白色
                }
                
                enhancedImage.setRGB(x, y, newRgb);
            }
        }
        
        return enhancedImage;
    }
    
    @Override
    @Transactional
    public Long savePaperAndQuestions(String paperName, String year, List<ExamQuestion> questions) {
        try {
            // 保存文件
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO exam_paper (paper_name, question_count) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS
                );
                ps.setString(1, paperName);
                ps.setInt(2, questions.size());
                return ps;
            }, keyHolder);
            
            Long paperId = keyHolder.getKey().longValue();
            
            // 保存题目
            for (ExamQuestion question : questions) {
                // 设置paperId和paperName
                question.setPaperId(paperId);
                question.setPaperName(paperName);
                
                jdbcTemplate.update(
                    "INSERT INTO exam_question (paper_id, page_number, content, image_data, paper_name, create_time, update_time) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    paperId,
                    question.getPageNumber(),
                    question.getContent(),
                    question.getImageData(),
                    paperName,
                    new Timestamp(System.currentTimeMillis()),
                    new Timestamp(System.currentTimeMillis())
                );
            }
            
            LOGGER.info("保存文件成功: id={}, 文件名称={}, 题目数量={}", paperId, paperName, questions.size());
            
            return paperId;
        } catch (Exception e) {
            LOGGER.error("保存文件失败: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    @Override
    public List<ExamPaper> getAllPapers() {
        try {
            return jdbcTemplate.query(
                "SELECT * FROM exam_paper ORDER BY create_time DESC",
                (rs, rowNum) -> {
                    ExamPaper paper = new ExamPaper();
                    paper.setId(rs.getLong("id"));
                    paper.setPaperName(rs.getString("paper_name"));
                    paper.setQuestionCount(rs.getInt("question_count"));
                    paper.setCreateTime(rs.getTimestamp("create_time"));
                    paper.setUpdateTime(rs.getTimestamp("update_time"));
                    return paper;
                }
            );
        } catch (Exception e) {
            LOGGER.error("获取文件列表失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public ExamPaper getPaperById(Long paperId) {
        try {
            ExamPaper paper = jdbcTemplate.queryForObject(
                "SELECT * FROM exam_paper WHERE id = ?",
                new Object[]{paperId},
                (rs, rowNum) -> {
                    ExamPaper p = new ExamPaper();
                    p.setId(rs.getLong("id"));
                    p.setPaperName(rs.getString("paper_name"));
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
                        q.setPageNumber(rs.getInt("page_number"));
                        q.setContent(rs.getString("content"));
                        q.setImageData(rs.getString("image_data"));
                        q.setPaperId(rs.getLong("paper_id"));
                        q.setPaperName(rs.getString("paper_name"));
                        q.setCreateTime(rs.getTimestamp("create_time"));
                        q.setUpdateTime(rs.getTimestamp("update_time"));
                        return q;
                    }
                );
                
                // 按照页码进行排序
                questions.sort(Comparator.comparingInt(ExamQuestion::getPageNumber));
                
                paper.setQuestions(questions);
            }
            
            return paper;
        } catch (EmptyResultDataAccessException e) {
            LOGGER.warn("未找到文件: id={}", paperId);
            return null;
        } catch (Exception e) {
            LOGGER.error("获取文件详情失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public List<ExamQuestion> processImageFile(byte[] fileContent, String paperName, String year) {
        // 使用已存在的方法处理图片文件
        return processImageFileByImage(fileContent, paperName, year);
    }
    
    @Override
    public List<ExamQuestion> processPdfFile(byte[] fileContent, String paperName, String year) {
        // 使用已存在的方法处理PDF文件
        return processPdfFileByImage(fileContent, paperName, year);
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
                "UPDATE exam_question SET page_number = ?, content = ?, " +
                "image_data = ?, paper_id = ?, paper_name = ?, update_time = ? WHERE id = ?",
                question.getPageNumber(),
                question.getContent(),
                question.getImageData(),
                question.getPaperId(),
                question.getPaperName(),
                new Timestamp(System.currentTimeMillis()),
                question.getId()
            );
            
            LOGGER.info("更新题目成功: id={}", question.getId());
            
            return result > 0;
        } catch (Exception e) {
            LOGGER.error("更新题目失败: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    @Override
    @Transactional
    public boolean deleteQuestion(Long questionId) {
        try {
            // 获取题目所属的文件ID
            Long paperId = jdbcTemplate.queryForObject(
                "SELECT paper_id FROM exam_question WHERE id = ?",
                new Object[]{questionId},
                Long.class
            );
            
            // 删除题目
            int result = jdbcTemplate.update("DELETE FROM exam_question WHERE id = ?", questionId);
            
            if (result > 0 && paperId != null) {
                // 更新文件的题目数量
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
                    q.setPageNumber(rs.getInt("page_number"));
                    q.setContent(rs.getString("content"));
                    q.setImageData(rs.getString("image_data"));
                    q.setPaperId(rs.getLong("paper_id"));
                    q.setPaperName(rs.getString("paper_name"));
                    q.setCreateTime(rs.getTimestamp("create_time"));
                    q.setUpdateTime(rs.getTimestamp("update_time"));
                    return q;
                }
            );
        } catch (Exception e) {
            LOGGER.error("获取题目失败: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public List<ExamPaper> searchPapersByName(String query) {
        String likeQuery = "%" + query + "%";
        try {
            return jdbcTemplate.query(
                "SELECT * FROM exam_paper WHERE paper_name LIKE ?",
                new Object[]{likeQuery},
                (rs, rowNum) -> {
                    ExamPaper paper = new ExamPaper();
                    paper.setId(rs.getLong("id"));
                    paper.setPaperName(rs.getString("paper_name"));
                    paper.setQuestionCount(rs.getInt("question_count"));
                    paper.setCreateTime(rs.getTimestamp("create_time"));
                    paper.setUpdateTime(rs.getTimestamp("update_time"));
                    return paper;
                }
            );
        } catch (Exception e) {
            LOGGER.error("搜索文件失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<ExamQuestion> searchQuestionsByContent(String query) {
        String likeQuery = "%" + query + "%";
        try {
            return jdbcTemplate.query(
                "SELECT * FROM exam_question WHERE content LIKE ?",
                new Object[]{likeQuery},
                (rs, rowNum) -> {
                    ExamQuestion question = new ExamQuestion();
                    question.setId(rs.getLong("id"));
                    question.setPaperId(rs.getLong("paper_id"));
                    question.setPageNumber(rs.getInt("page_number"));
                    question.setContent(rs.getString("content"));
                    question.setImageData(rs.getString("image_data"));
                    question.setPaperName(rs.getString("paper_name"));
                    question.setCreateTime(rs.getTimestamp("create_time"));
                    question.setUpdateTime(rs.getTimestamp("update_time"));
                    return question;
                }
            );
        } catch (Exception e) {
            LOGGER.error("搜索题目失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
} 