package com.davidwang456.excel.service;

import com.davidwang456.excel.model.ExamPaper;
import com.davidwang456.excel.model.ExamQuestion;
import com.davidwang456.excel.model.OCRResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * OCR服务接口
 */
public interface OCRService {
    
    /**
     * 处理OCR识别
     * @param file 文件
     * @param paperName 文件名称
     * @param year 年份
     * @return OCR识别结果
     */
    OCRResult processOCR(MultipartFile file, String paperName, String year);
    
    /**
     * 对图片进行OCR处理
     * @param fileContent 文件内容
     * @param paperName 文件名称
     * @param year 年份
     * @return 识别结果列表
     */
    List<ExamQuestion> processImageFile(byte[] fileContent, String paperName, String year);
    
    /**
     * 对PDF文件进行处理
     * @param fileContent 文件内容
     * @param paperName 文件名称
     * @param year 年份
     * @return 识别结果列表
     */
    List<ExamQuestion> processPdfFile(byte[] fileContent, String paperName, String year);
    
    /**
     * 保存文件和题目
     * @param paperName 文件名称
     * @param year 年份
     * @param questions 题目列表
     * @return 文件ID
     */
    Long savePaperAndQuestions(String paperName, String year, List<ExamQuestion> questions);
    
    /**
     * 获取所有文件
     * @return 文件列表
     */
    List<ExamPaper> getAllPapers();
    
    /**
     * 根据ID获取文件详情
     * @param paperId 文件ID
     * @return 文件详情
     */
    ExamPaper getPaperById(Long paperId);
    
    /**
     * 删除文件
     * @param paperId 文件ID
     * @return 是否成功
     */
    boolean deletePaper(Long paperId);
    
    /**
     * 更新题目
     * @param question 题目
     * @return 是否成功
     */
    boolean updateQuestion(ExamQuestion question);
    
    /**
     * 删除题目
     * @param questionId 题目ID
     * @return 是否成功
     */
    boolean deleteQuestion(Long questionId);
    
    /**
     * 根据ID获取题目
     * @param questionId 题目ID
     * @return 题目
     */
    ExamQuestion getQuestionById(Long questionId);
    
    /**
     * 根据文件名称搜索文件
     * @param query 搜索关键字
     * @return 匹配的文件列表
     */
    List<ExamPaper> searchPapersByName(String query);
    
    /**
     * 根据内容搜索题目
     * @param query 搜索关键字
     * @return 匹配的题目列表
     */
    List<ExamQuestion> searchQuestionsByContent(String query);
} 