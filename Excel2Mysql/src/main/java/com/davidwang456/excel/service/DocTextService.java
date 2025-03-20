package com.davidwang456.excel.service;

import java.util.List;
import com.davidwang456.excel.model.ExamQuestion;

/**
 * 文档处理服务接口
 * 支持处理多种格式的文档：.doc .docx .eml .xls .xlsx .ppt .pptx .pdf .txt .json .csv等
 */
public interface DocTextService {
    
    /**
     * 使用Apache Tika处理文档，提取文本和图像
     * @param fileContent 文件内容
     * @param fileName 文件名
     * @param paperName 文档名称
     * @return 提取的页面列表
     */
    List<ExamQuestion> processDocumentWithTika(byte[] fileContent, String fileName, String paperName);
    
    /**
     * 使用Apache Tika处理文档，提取文本和图像（简化版，不需要文件名）
     * @param fileContent 文件内容
     * @param paperName 文档名称
     * @return 提取的页面列表
     */
    List<ExamQuestion> processDocumentWithTika(byte[] fileContent, String paperName);
    
    /**
     * 保存文档和页面内容
     * @param paperName 文档名称
     * @param year 年份（可选）
     * @param pages 页面列表
     * @return 文档ID
     */
    Long savePaperAndQuestions(String paperName, String year, List<ExamQuestion> pages);
    
    /**
     * 保存文档和页面内容（简化版，不需要年份）
     * @param paperName 文档名称
     * @param pages 页面列表
     * @return 文档ID
     */
    int savePaperAndQuestions(String paperName, List<ExamQuestion> pages);
} 