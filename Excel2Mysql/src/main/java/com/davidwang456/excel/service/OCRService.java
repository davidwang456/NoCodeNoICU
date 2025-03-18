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
     * 保存文件和题目
     * @param paperName 文件名称
     * @param year 年份
     * @param questions 题目列表
     * @return 文件ID
     */
    Long savePaperAndQuestions(String paperName, String year, List<ExamQuestion> questions);
    
    /**
     * 获取文件列表
     * @return 文件列表
     */
    List<ExamPaper> getPaperList();
    
    /**
     * 获取文件详情
     * @param paperId 文件ID
     * @return 文件详情
     */
    ExamPaper getPaperDetail(Long paperId);
    
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
} 