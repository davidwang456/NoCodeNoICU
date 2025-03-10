package com.davidwang456.excel.model;

import lombok.Data;

/**
 * 导入确认请求
 */
@Data
public class ImportConfirmRequest {
    /**
     * 文件名
     */
    private String fileName;
    
    /**
     * 数据源类型
     */
    private String dataSource;
    
    @Override
    public String toString() {
        return "ImportConfirmRequest{" +
                "fileName='" + fileName + '\'' +
                ", dataSource='" + dataSource + '\'' +
                '}';
    }
} 