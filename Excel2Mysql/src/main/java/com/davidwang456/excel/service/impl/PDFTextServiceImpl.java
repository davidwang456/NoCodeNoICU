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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
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

import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import com.itextpdf.text.pdf.parser.SimpleTextExtractionStrategy;
import com.itextpdf.text.pdf.parser.TextExtractionStrategy;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Base64;

/**
 * PDF文本提取服务实现类
 */
@Service
public class PDFTextServiceImpl implements PDFTextService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PDFTextServiceImpl.class);

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
            
            LOGGER.info("PDF相关表创建/更新成功");
        } catch (Exception e) {
            LOGGER.error("PDF相关表创建/更新失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 直接从PDF文件内容中提取文本和图像，仅进行分页识别
     * @param fileContent PDF文件内容
     * @param paperName 文件名称
     * @return 提取的页面列表
     */
    @Override
    public List<ExamQuestion> processPdfFileWithIText(byte[] fileContent, String paperName) {
        List<ExamQuestion> pages = new ArrayList<>();
        PDDocument pdfBoxDocument = null;
        
        try {
            // 使用iText提取文本
            PdfReader reader = new PdfReader(fileContent);
            
            // 同时使用PDFBox提取页面图像
            pdfBoxDocument = PDDocument.load(fileContent);
            PDFRenderer renderer = new PDFRenderer(pdfBoxDocument);
            
            // 提取每一页的文本和图像
            for (int i = 1; i <= reader.getNumberOfPages(); i++) {
                TextExtractionStrategy strategy = new SimpleTextExtractionStrategy();
                String pageText = PdfTextExtractor.getTextFromPage(reader, i, strategy);
                
                LOGGER.info("提取PDF第{}页文本，长度: {}", i, pageText.length());
                
                // 创建页面对象
                ExamQuestion page = new ExamQuestion();
                page.setPageNumber(i); // 设置页码
                page.setContent(pageText); // 设置页面文本内容
                
                // 使用PDFBox提取页面图像
                try {
                    // PDFBox页码从0开始
                    BufferedImage image = renderer.renderImageWithDPI(i - 1, 150);
                    
                    // 将图像转换为Base64字符串
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(image, "png", baos);
                    byte[] imageBytes = baos.toByteArray();
                    
                    String pageImage = "data:image/png;base64," + Base64.getEncoder().encodeToString(imageBytes);
                    page.setImageData(pageImage);
                    
                    LOGGER.info("成功提取PDF第{}页图像", i);
                } catch (Exception e) {
                    LOGGER.error("提取PDF第{}页图像失败: {}", i, e.getMessage());
                    // 如果提取失败，使用占位图
                    page.setImageData(createPlaceholderImage(i));
                }
                
                page.setCreateTime(new Date());
                page.setUpdateTime(new Date());
                
                pages.add(page);
            }
            
            // 关闭reader
            reader.close();
            
            LOGGER.info("PDF处理完成，共提取 {} 页内容", pages.size());
            
        } catch (Exception e) {
            LOGGER.error("使用iText处理PDF文件失败: {}", e.getMessage(), e);
        } finally {
            if (pdfBoxDocument != null) {
                try {
                    pdfBoxDocument.close();
                } catch (IOException e) {
                    LOGGER.error("关闭PDFBox文档失败: {}", e.getMessage());
                }
            }
        }
        
        return pages;
    }
    
    /**
     * 创建占位图像
     * @param pageNumber 页码
     * @return 图像的Base64编码字符串
     */
    private String createPlaceholderImage(int pageNumber) {
        try {
            // 创建一个空白图像，作为页面的占位图
            BufferedImage placeholderImage = new BufferedImage(
                800, // 宽度
                1200, // 高度
                BufferedImage.TYPE_INT_RGB
            );
            
            // 设置为白色背景
            Graphics2D g2d = placeholderImage.createGraphics();
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
     * 保存文档和页面内容
     * @param paperName 文档名称
     * @param year 年份（已弃用）
     * @param pages 页面列表
     * @return 文档ID
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
     * 处理PDF文件，提取文本，仅进行分页识别
     * @param file PDF文件
     * @param paperName 文档名称
     * @return 处理结果
     */
    @Override
    public PDFResult processPDF(MultipartFile file, String paperName) {
        PDFResult result = new PDFResult();
        
        try {
            LOGGER.info("接收到PDF处理请求: 文件={}, 文档名称={}", file.getOriginalFilename(), paperName);
            
            // 读取文件内容
            byte[] fileContent = file.getBytes();
            String fileType = file.getContentType();
            
            // 根据文件类型选择不同的处理方式
            List<ExamQuestion> pages = new ArrayList<>();
            
            if ("application/pdf".equals(fileType)) {
                // 处理PDF文件 - 使用iText直接提取文本
                pages = processPdfFileWithIText(fileContent, paperName);
                
                if (pages.isEmpty()) {
                    result.setSuccess(false);
                    result.setErrorMessage("未能从PDF中提取到内容");
                    return result;
                }
            } else {
                result.setSuccess(false);
                result.setErrorMessage("不支持的文件类型: " + fileType + "，只支持PDF文件");
                return result;
            }
            
            result.setSuccess(true);
            result.setQuestions(pages);
            
            LOGGER.info("PDF处理完成，共提取 {} 页内容", pages.size());
            
        } catch (Exception e) {
            LOGGER.error("PDF处理失败: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setErrorMessage("PDF处理失败: " + e.getMessage());
        }
        
        return result;
    }
} 