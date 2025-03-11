package com.davidwang456.excel.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.Date;

/**
 * 试题实体类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExamQuestion {
    /**
     * 系统编号
     */
    private Long id;
    
    /**
     * 题目编号
     */
    private String questionNumber;
    
    /**
     * 题目类型
     */
    private String questionType;
    
    /**
     * 题目内容
     */
    private String content;
    
    /**
     * 图片数据（Base64编码）
     */
    private String imageData;
    
    /**
     * 所属试卷ID
     */
    private Long paperId;
    
    /**
     * 年份
     */
    private String year;
    
    /**
     * 创建时间
     */
    private Date createTime;
    
    /**
     * 更新时间
     */
    private Date updateTime;
    
    /**
     * 是否仅使用图像显示（适用于数学公式）
     */
    private Boolean useImageOnly;
} 