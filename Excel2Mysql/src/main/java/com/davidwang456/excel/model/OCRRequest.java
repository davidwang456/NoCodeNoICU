package com.davidwang456.excel.model;

import lombok.Data;
import java.util.List;

/**
 * OCR识别请求
 */
@Data
public class OCRRequest {
    /**
     * 试卷名称
     */
    private String paperName;
    
    /**
     * 年份
     */
    private String year;
    
    /**
     * 文件ID列表
     */
    private List<String> fileIds;
} 