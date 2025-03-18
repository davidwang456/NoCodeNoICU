package com.davidwang456.excel.model;

import lombok.Data;
import java.util.List;

/**
 * OCR识别结果
 */
@Data
public class OCRResult {
    /**
     * 识别状态
     */
    private boolean success;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 识别出的题目列表
     */
    private List<ExamQuestion> questions;
    
    /**
     * 文件ID
     */
    private Long paperId;
} 