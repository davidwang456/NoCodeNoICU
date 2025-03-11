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
     * @param paperName 试卷名称
     * @param year 年份
     * @return OCR识别结果
     */
    OCRResult processOCR(MultipartFile file, String paperName, String year);
    
    /**
     * 保存试卷和题目
     * @param paperName 试卷名称
     * @param year 年份
     * @param questions 题目列表
     * @return 试卷ID
     */
    Long savePaperAndQuestions(String paperName, String year, List<ExamQuestion> questions);
    
    /**
     * 获取试卷列表
     * @return 试卷列表
     */
    List<ExamPaper> getPaperList();
    
    /**
     * 获取试卷详情
     * @param paperId 试卷ID
     * @return 试卷详情
     */
    ExamPaper getPaperDetail(Long paperId);
    
    /**
     * 删除试卷
     * @param paperId 试卷ID
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