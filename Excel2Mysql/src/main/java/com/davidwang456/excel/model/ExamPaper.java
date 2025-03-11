package com.davidwang456.excel.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.Date;
import java.util.List;

/**
 * 试卷实体类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExamPaper {
    /**
     * 系统编号
     */
    private Long id;
    
    /**
     * 试卷名称
     */
    private String paperName;
    
    /**
     * 年份
     */
    private String year;
    
    /**
     * 题目数量
     */
    private Integer questionCount;
    
    /**
     * 创建时间
     */
    private Date createTime;
    
    /**
     * 更新时间
     */
    private Date updateTime;
    
    /**
     * 试题列表（非数据库字段）
     */
    private transient List<ExamQuestion> questions;
} 