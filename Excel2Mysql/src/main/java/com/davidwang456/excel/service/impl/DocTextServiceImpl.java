package com.davidwang456.excel.service.impl;

import com.davidwang456.excel.model.ExamPaper;
import com.davidwang456.excel.model.ExamQuestion;
import com.davidwang456.excel.service.DocTextService;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ToXMLContentHandler;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档文本提取服务实现类
 * 支持多种文档格式：.doc .docx .eml .xls .xlsx .ppt .pptx .pdf .txt .json .csv等
 */
@Service
public class DocTextServiceImpl implements DocTextService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DocTextServiceImpl.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;
    
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
            
            LOGGER.info("文档处理相关表创建/更新成功");
        } catch (Exception e) {
            LOGGER.error("文档处理相关表创建/更新失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 使用Tika处理所有文档类型的通用方法
     */
    @Override
    public List<ExamQuestion> processDocumentWithTika(byte[] fileContent, String fileName, String paperName) {
        List<ExamQuestion> questions = new ArrayList<>();
        
        try {
            // 不使用Tika检测文件类型，改为通过文件扩展名判断
            String mimeType = "application/octet-stream";
            String lowerFileName = fileName.toLowerCase();
            
            LOGGER.info("处理文件: {}, 文档名称: {}", fileName, paperName);
            
            // 根据文件扩展名选择合适的处理方法
            if (lowerFileName.endsWith(".pdf")) {
                mimeType = "application/pdf";
                questions = processPdfDocument(fileContent, paperName);
            } else if (lowerFileName.endsWith(".doc") || lowerFileName.endsWith(".docx")) {
                mimeType = "application/msword";
                // 处理Word文档
                List<ExamQuestion> wordQuestions = processWordDocument(fileContent, fileName);
                
                // 确保设置了正确的paperName
                for (ExamQuestion question : wordQuestions) {
                    question.setPaperName(paperName);
                }
                
                questions = wordQuestions;
            } else if (lowerFileName.endsWith(".xls") || lowerFileName.endsWith(".xlsx")) {
                mimeType = "application/vnd.ms-excel";
                questions = processExcelDocument(fileContent, paperName);
            } else if (lowerFileName.endsWith(".ppt") || lowerFileName.endsWith(".pptx")) {
                mimeType = "application/vnd.ms-powerpoint";
                questions = processPowerPointDocument(fileContent, paperName);
            } else if (lowerFileName.endsWith(".txt") || lowerFileName.endsWith(".json") || lowerFileName.endsWith(".csv")) {
                mimeType = "text/plain";
                questions = processTextDocument(fileContent, paperName);
            } else if (lowerFileName.endsWith(".eml")) {
                mimeType = "message/rfc822";
                questions = processEmailDocument(fileContent, paperName);
            } else {
                // 非特定格式的文档使用通用处理方法
                LOGGER.info("未识别的文件类型，使用通用方法处理: {}", fileName);
                
                try {
                    // 使用纯文本提取器
                    BodyContentHandler handler = new BodyContentHandler(-1);
                    Metadata metadata = new Metadata();
                    metadata.set("resourceName", fileName);
                    
                    // 创建解析上下文
                    ParseContext context = new ParseContext();
                    
                    // 使用通用解析器
                    Parser parser = new AutoDetectParser();
                    
                    // 解析文档内容
                    try (InputStream stream = new ByteArrayInputStream(fileContent)) {
                        parser.parse(stream, handler, metadata, context);
                        
                        String content = handler.toString();
                        if (content != null && !content.isEmpty()) {
                            // 分页处理
                            int pageSize = 3000; // 每页字符数
                            int totalChars = content.length();
                            int pageCount = Math.max(1, (int) Math.ceil((double) totalChars / pageSize));
                            
                            LOGGER.info("文档文本提取成功，总字符数: {}, 将分为 {} 页", totalChars, pageCount);
                            
                            for (int i = 0; i < pageCount; i++) {
                                int start = i * pageSize;
                                int end = Math.min(start + pageSize, totalChars);
                                String pageContent = content.substring(start, end);
                                
                                ExamQuestion question = new ExamQuestion();
                                question.setPageNumber(i + 1);
                                question.setContent(pageContent);
                                question.setPaperName(paperName);
                                question.setImageData(generatePlaceholderImage(i + 1));
                                question.setCreateTime(new Date());
                                question.setUpdateTime(new Date());
                                
                                questions.add(question);
                            }
                        } else {
                            throw new Exception("提取的内容为空");
                        }
                    }
                } catch (Exception innerEx) {
                    LOGGER.error("通用处理方法失败: {}", innerEx.getMessage(), innerEx);
                    // 使用备用方法
                    questions = processDocumentFallbackBasic(fileContent, paperName);
                }
            }
            
            LOGGER.info("文档处理完成，共 {} 页", questions.size());
            
        } catch (Exception e) {
            LOGGER.error("处理文档失败: {}, 文档名称: {}", fileName, paperName, e);
            // 使用备用方法处理
            questions = processDocumentFallbackBasic(fileContent, paperName);
        }
        
        return questions;
    }
    
    /**
     * 使用Tika处理所有文档类型的通用方法（简化版，不需要文件名）
     */
    @Override
    public List<ExamQuestion> processDocumentWithTika(byte[] fileContent, String paperName) {
        // 使用默认文件名调用完整方法
        return processDocumentWithTika(fileContent, "document.txt", paperName);
    }

    /**
     * 处理PDF文档
     */
    private List<ExamQuestion> processPdfDocument(byte[] fileContent, String paperName) {
        List<ExamQuestion> pages = new ArrayList<>();
        
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(fileContent))) {
            int pageCount = document.getNumberOfPages();
            PDFRenderer renderer = new PDFRenderer(document);
            PDFTextStripper stripper = new PDFTextStripper();
            
            LOGGER.info("PDF文档共 {} 页", pageCount);
            
            for (int i = 0; i < pageCount; i++) {
                stripper.setStartPage(i + 1);
                stripper.setEndPage(i + 1);
                String pageText = stripper.getText(document);
                
                LOGGER.info("提取PDF第 {} 页文本，长度: {}", i + 1, pageText.length());
                
                // 创建页面对象
                ExamQuestion page = new ExamQuestion();
                page.setPageNumber(i + 1);
                page.setContent(pageText);
                page.setPaperName(paperName);
                
                // 提取页面图像
                try {
                    BufferedImage image = renderer.renderImageWithDPI(i, 150);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(image, "png", baos);
                    String base64Image = "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
                    page.setImageData(base64Image);
                } catch (Exception e) {
                    LOGGER.warn("无法提取PDF第 {} 页图像: {}", i + 1, e.getMessage());
                    page.setImageData(generatePlaceholderImage(i + 1));
                }
                
                pages.add(page);
            }
        } catch (Exception e) {
            LOGGER.error("处理PDF文档失败: {}", e.getMessage(), e);
        }
        
        return pages;
    }

    /**
     * 处理Word文档
     */
    private List<ExamQuestion> processWordDocument(byte[] fileContent, String fileName) {
        List<ExamQuestion> questions = new ArrayList<>();
        try {
            // 直接使用纯文本提取，完全避开任何可能的Tika OOXML解析器
            LOGGER.info("处理Word文档: {}, 使用纯文本提取方式", fileName);
            
            // 尝试直接读取文本内容
            String content = extractPlainText(fileContent);
            
            if (content != null && !content.isEmpty() && content.length() > 50) {
                LOGGER.info("使用纯文本提取成功获取内容，长度: {} 字符", content.length());
                
                // 分页处理
                int pageSize = 3000; // 每页字符数
                int totalChars = content.length();
                int pageCount = Math.max(1, (int) Math.ceil((double) totalChars / pageSize));
                
                for (int i = 0; i < pageCount; i++) {
                    int start = i * pageSize;
                    int end = Math.min(start + pageSize, totalChars);
                    String pageContent = content.substring(start, end);
                    
                    ExamQuestion question = new ExamQuestion();
                    question.setPageNumber(i + 1);
                    question.setContent(pageContent);
                    question.setImageData(generatePlaceholderImage(i + 1));
                    question.setCreateTime(new Date());
                    question.setUpdateTime(new Date());
                    
                    questions.add(question);
                }
                
                LOGGER.info("Word文档处理完成，共生成 {} 页", questions.size());
                return questions;
            } else {
                // 如果纯文本提取失败，使用基本方法处理
                LOGGER.warn("Word文档内容提取为空，使用备用方法处理");
                questions = processDocumentFallbackBasic(fileContent, fileName);
            }
        } catch (Exception e) {
            LOGGER.error("处理Word文档失败: {}，错误：{}", fileName, e.getMessage(), e);
            // 使用最基本的文本提取方法
            questions = processDocumentFallbackBasic(fileContent, fileName);
        }
        return questions;
    }
    
    /**
     * 最基本的文本内容提取，不依赖任何高级库
     */
    private List<ExamQuestion> processDocumentFallbackBasic(byte[] fileContent, String paperName) {
        List<ExamQuestion> pages = new ArrayList<>();
        
        ExamQuestion page = new ExamQuestion();
        page.setPageNumber(1);
        page.setPaperName(paperName);
        
        StringBuilder content = new StringBuilder();
        content.append("文档解析遇到问题，使用基本信息展示。\n\n");
        content.append("文档名称: ").append(paperName).append("\n");
        content.append("文件大小: ").append(fileContent.length).append(" 字节\n");
        content.append("上传时间: ").append(new Date()).append("\n\n");
        
        // 尝试作为文本读取
        try {
            String text = new String(fileContent, "UTF-8");
            // 只保留可打印字符
            text = text.replaceAll("[^\\p{Print}\\p{Space}]", " ");
            
            if (text.length() > 100) {
                content.append("文本预览 (前100字符):\n");
                content.append(text.substring(0, 100));
                content.append("...");
            }
        } catch (Exception e) {
            content.append("无法提取文本内容。");
        }
        
        page.setContent(content.toString());
        page.setImageData(generatePlaceholderImage(1));
        pages.add(page);
        
        LOGGER.info("使用基本方法处理文档完成");
        return pages;
    }

    /**
     * 处理Excel文档
     */
    private List<ExamQuestion> processExcelDocument(byte[] fileContent, String paperName) {
        List<ExamQuestion> pages = new ArrayList<>();
        
        try {
            // 使用Tika解析Excel文档
            Parser parser = new AutoDetectParser();
            ContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            
            parser.parse(new ByteArrayInputStream(fileContent), handler, metadata, context);
            
            String content = handler.toString();
            
            // 尝试检测工作表
            Workbook workbook = null;
            try {
                workbook = new XSSFWorkbook(new ByteArrayInputStream(fileContent)); // XLSX
            } catch (Exception e) {
                try {
                    workbook = new HSSFWorkbook(new ByteArrayInputStream(fileContent)); // XLS
                } catch (Exception ex) {
                    LOGGER.warn("无法识别Excel格式，将使用通用提取的内容");
                }
            }
            
            if (workbook != null) {
                // 如果能够识别Excel格式，则按工作表分页
                int sheetCount = workbook.getNumberOfSheets();
                for (int i = 0; i < sheetCount; i++) {
                    Sheet sheet = workbook.getSheetAt(i);
                    StringBuilder sheetContent = new StringBuilder();
                    sheetContent.append("工作表: ").append(sheet.getSheetName()).append("\n\n");
                    
                    // 提取工作表内容的逻辑...
                    // 这里简化处理，使用Tika提取的总内容并按照工作表数量平均分配
                    int startPos = content.length() * i / sheetCount;
                    int endPos = content.length() * (i + 1) / sheetCount;
                    if (i == sheetCount - 1) {
                        endPos = content.length();
                    }
                    
                    String sheetPart = (startPos < endPos) ? content.substring(startPos, endPos) : "";
                    sheetContent.append(sheetPart);
                    
                    ExamQuestion page = new ExamQuestion();
                    page.setPageNumber(i + 1);
                    page.setContent(sheetContent.toString());
                    page.setPaperName(paperName);
                    page.setImageData(generatePlaceholderImage(i + 1));
                    pages.add(page);
                }
                
                workbook.close();
            } else {
                // 如果无法识别Excel格式，则按固定大小分页
                int pageSize = 3000;
                int pageCount = (int) Math.ceil((double) content.length() / pageSize);
                
                for (int i = 0; i < pageCount; i++) {
                    int startPos = i * pageSize;
                    int endPos = Math.min((i + 1) * pageSize, content.length());
                    String pageContent = content.substring(startPos, endPos);
                    
                    ExamQuestion page = new ExamQuestion();
                    page.setPageNumber(i + 1);
                    page.setContent(pageContent);
                    page.setPaperName(paperName);
                    page.setImageData(generatePlaceholderImage(i + 1));
                    pages.add(page);
                }
            }
            
            LOGGER.info("Excel文档处理完成，共分割为 {} 页", pages.size());
        } catch (Exception e) {
            LOGGER.error("处理Excel文档失败: {}", e.getMessage(), e);
        }
        
        return pages;
    }

    /**
     * 处理PowerPoint文档
     */
    private List<ExamQuestion> processPowerPointDocument(byte[] fileContent, String paperName) {
        List<ExamQuestion> pages = new ArrayList<>();
        
        try {
            // 使用Tika解析PowerPoint文档
            ToXMLContentHandler handler = new ToXMLContentHandler();
            ParseContext context = new ParseContext();
            Metadata metadata = new Metadata();
            
            AutoDetectParser parser = new AutoDetectParser();
            parser.parse(new ByteArrayInputStream(fileContent), handler, metadata, context);
            
            String xmlContent = handler.toString();
            
            // 从XML中提取幻灯片内容
            Pattern slidePattern = Pattern.compile("<div class=\"slide\">(.*?)</div>", Pattern.DOTALL);
            Matcher slideMatcher = slidePattern.matcher(xmlContent);
            
            int slideNumber = 1;
            
            while (slideMatcher.find()) {
                String slideContent = slideMatcher.group(1);
                // 清理HTML标签
                slideContent = slideContent.replaceAll("<[^>]+>", "").trim();
                
                if (!slideContent.isEmpty()) {
                    ExamQuestion page = new ExamQuestion();
                    page.setPageNumber(slideNumber++);
                    page.setContent("幻灯片 " + (slideNumber - 1) + ":\n" + slideContent);
                    page.setPaperName(paperName);
                    page.setImageData(generatePlaceholderImage(slideNumber - 1));
                    pages.add(page);
                }
            }
            
            // 如果没有匹配到幻灯片，则使用普通文本提取
            if (pages.isEmpty()) {
                String content = xmlContent.replaceAll("<[^>]+>", "").trim();
                
                // 按固定大小分页
                int pageSize = 3000;
                int pageCount = (int) Math.ceil((double) content.length() / pageSize);
                
                for (int i = 0; i < pageCount; i++) {
                    int startPos = i * pageSize;
                    int endPos = Math.min((i + 1) * pageSize, content.length());
                    String pageContent = content.substring(startPos, endPos);
                    
                    ExamQuestion page = new ExamQuestion();
                    page.setPageNumber(i + 1);
                    page.setContent(pageContent);
                    page.setPaperName(paperName);
                    page.setImageData(generatePlaceholderImage(i + 1));
                    pages.add(page);
                }
            }
            
            LOGGER.info("PowerPoint文档处理完成，共提取 {} 页", pages.size());
        } catch (Exception e) {
            LOGGER.error("处理PowerPoint文档失败: {}", e.getMessage(), e);
        }
        
        return pages;
    }

    /**
     * 处理文本文档（TXT、CSV、JSON等）
     */
    private List<ExamQuestion> processTextDocument(byte[] fileContent, String paperName) {
        List<ExamQuestion> pages = new ArrayList<>();
        
        try {
            // 解析文本内容
            String content = new String(fileContent, "UTF-8");
            
            // 按行分割文本
            String[] lines = content.split("\\r?\\n");
            
            // 每页行数
            int linesPerPage = 50;
            int pageCount = (int) Math.ceil((double) lines.length / linesPerPage);
            
            for (int i = 0; i < pageCount; i++) {
                StringBuilder pageContent = new StringBuilder();
                int startLine = i * linesPerPage;
                int endLine = Math.min((i + 1) * linesPerPage, lines.length);
                
                for (int j = startLine; j < endLine; j++) {
                    pageContent.append(lines[j]).append("\n");
                }
                
                ExamQuestion page = new ExamQuestion();
                page.setPageNumber(i + 1);
                page.setContent(pageContent.toString());
                page.setPaperName(paperName);
                page.setImageData(generatePlaceholderImage(i + 1));
                pages.add(page);
            }
            
            LOGGER.info("文本文档处理完成，共分割为 {} 页", pages.size());
        } catch (Exception e) {
            LOGGER.error("处理文本文档失败: {}", e.getMessage(), e);
        }
        
        return pages;
    }

    /**
     * 处理邮件文档(EML)
     */
    private List<ExamQuestion> processEmailDocument(byte[] fileContent, String paperName) {
        List<ExamQuestion> pages = new ArrayList<>();
        
        try {
            // 使用Tika解析邮件
            Parser parser = new AutoDetectParser();
            ContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            
            parser.parse(new ByteArrayInputStream(fileContent), handler, metadata, context);
            
            // 提取元数据
            StringBuilder emailContent = new StringBuilder();
            emailContent.append("邮件主题: ").append(metadata.get("subject")).append("\n");
            emailContent.append("发件人: ").append(metadata.get("from")).append("\n");
            emailContent.append("收件人: ").append(metadata.get("to")).append("\n");
            emailContent.append("日期: ").append(metadata.get("date")).append("\n\n");
            emailContent.append("正文:\n").append(handler.toString());
            
            // 创建邮件页面
            ExamQuestion page = new ExamQuestion();
            page.setPageNumber(1);
            page.setContent(emailContent.toString());
            page.setPaperName(paperName);
            page.setImageData(generatePlaceholderImage(1));
            pages.add(page);
            
            LOGGER.info("邮件文档处理完成");
        } catch (Exception e) {
            LOGGER.error("处理邮件文档失败: {}", e.getMessage(), e);
        }
        
        return pages;
    }

    /**
     * 生成占位图像
     */
    private String generatePlaceholderImage(int pageNumber) {
        try {
            // 创建一个简单的占位图像
            BufferedImage placeholderImage = new BufferedImage(800, 1200, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = placeholderImage.createGraphics();
            
            // 设置背景色
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, 800, 1200);
            
            // 添加页码文本
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Arial", Font.BOLD, 24));
            g2d.drawString("第 " + pageNumber + " 页", 350, 600);
            g2d.dispose();
            
            // 将图像转换为Base64字符串
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(placeholderImage, "png", baos);
            byte[] imageBytes = baos.toByteArray();
            
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(imageBytes);
        } catch (Exception ex) {
            LOGGER.error("生成占位图失败: {}", ex.getMessage(), ex);
            return "";
        }
    }

    /**
     * 文档处理的通用备用方法
     */
    private List<ExamQuestion> processDocumentFallback(byte[] fileContent, String paperName, String mimeType) {
        List<ExamQuestion> pages = new ArrayList<>();
        
        try {
            // 创建一个页面，包含基本信息
            ExamQuestion page = new ExamQuestion();
            page.setPageNumber(1);
            page.setPaperName(paperName);
            
            // 添加基本信息
            StringBuilder content = new StringBuilder();
            content.append("无法完全解析文档，可能包含不兼容的格式。\n\n");
            content.append("文档名称: ").append(paperName).append("\n");
            content.append("文件类型: ").append(mimeType).append("\n");
            content.append("文件大小: ").append(fileContent.length).append(" 字节\n");
            content.append("上传时间: ").append(new Date()).append("\n\n");
            
            // 尝试提取部分文本
            try {
                // 使用字符串解析尝试提取可读文本
                String textContent = extractPlainText(fileContent);
                if (textContent != null && !textContent.isEmpty()) {
                    content.append("提取的部分内容:\n\n");
                    content.append(textContent.length() > 5000 ? textContent.substring(0, 5000) + "..." : textContent);
                }
            } catch (Exception e) {
                LOGGER.warn("提取文本时出错: {}", e.getMessage());
                content.append("无法提取文本内容。");
            }
            
            page.setContent(content.toString());
            page.setImageData(generatePlaceholderImage(1));
            pages.add(page);
            
            LOGGER.info("使用备用方法处理文档完成");
        } catch (Exception e) {
            LOGGER.error("备用方法处理文档失败: {}", e.getMessage(), e);
        }
        
        return pages;
    }

    /**
     * 尝试从字节数组中提取纯文本
     */
    private String extractPlainText(byte[] fileContent) {
        StringBuilder text = new StringBuilder();
        
        // 尝试读取为UTF-8文本
        try {
            String utf8Text = new String(fileContent, "UTF-8");
            
            // 删除不可打印字符
            String cleanText = utf8Text.replaceAll("[^\\x20-\\x7E\\p{L}\\p{N}\\p{P}\\s]", " ");
            
            // 如果包含足够多的可读字符，认为是文本文件
            if (cleanText.matches(".*[a-zA-Z\\p{L}].*")) {
                text.append(cleanText);
            }
        } catch (Exception e) {
            LOGGER.debug("UTF-8解析失败: {}", e.getMessage());
        }
        
        return text.toString();
    }

    /**
     * 保存文档和页面内容
     */
    @Override
    @Transactional
    public Long savePaperAndQuestions(String paperName, String year, List<ExamQuestion> pages) {
        // 创建文档记录
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO exam_paper (paper_name, question_count, create_time, update_time) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, paperName);
            ps.setInt(2, pages.size());
            ps.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            ps.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            return ps;
        }, keyHolder);
        
        Long paperId = keyHolder.getKey().longValue();
        
        // 保存页面内容
        for (ExamQuestion page : pages) {
            page.setPaperId(paperId);
            page.setPaperName(paperName);
            jdbcTemplate.update(
                    "INSERT INTO exam_question (paper_id, page_number, content, image_data, paper_name, create_time, update_time) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    page.getPaperId(),
                    page.getPageNumber(),
                    page.getContent(),
                    page.getImageData(),
                    paperName,
                    new Timestamp(System.currentTimeMillis()),
                    new Timestamp(System.currentTimeMillis())
            );
        }
        
        LOGGER.info("保存文档成功，文档ID: {}, 共 {} 页", paperId, pages.size());
        
        return paperId;
    }

    /**
     * 保存文档和页面内容（简化版，不需要年份）
     */
    @Override
    @Transactional
    public int savePaperAndQuestions(String paperName, List<ExamQuestion> pages) {
        // 调用原始方法，年份传null
        return savePaperAndQuestions(paperName, null, pages).intValue();
    }
} 