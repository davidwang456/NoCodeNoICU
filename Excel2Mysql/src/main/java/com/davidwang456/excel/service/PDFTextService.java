package com.davidwang456.excel.service;

import com.davidwang456.excel.model.ExamPaper;
import com.davidwang456.excel.model.ExamQuestion;
import com.davidwang456.excel.model.PDFResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * PDF文本提取服务接口
 */
public interface PDFTextService {
    /**
     * 处理PDF文件，提取文本
     * @param file PDF文件
     * @param paperName 文档名称
     * @return 处理结果
     */
    PDFResult processPDF(MultipartFile file, String paperName);
    
    /**
     * 直接从PDF文件内容中提取文本和图像
     * @param fileContent PDF文件内容
     * @param paperName 文档名称
     * @return 提取的页面列表
     */
    List<ExamQuestion> processPdfFileWithIText(byte[] fileContent, String paperName);
    
    /**
     * 保存文档和页面内容
     * @param paperName 文档名称
     * @param year 年份（已弃用）
     * @param questions 页面列表
     * @return 文档ID
     */
    Long savePaperAndQuestions(String paperName, String year, List<ExamQuestion> questions);
    
    // ... 其他现有方法 ...
} 