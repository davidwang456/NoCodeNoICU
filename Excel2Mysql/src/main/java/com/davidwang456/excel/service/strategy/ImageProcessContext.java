package com.davidwang456.excel.service.strategy;

/**
 * 图片处理上下文
 */
public class ImageProcessContext {
    private final String tableName;
    private final String columnName;
    private final int rowNum;

    public ImageProcessContext(String tableName, String columnName, int rowNum) {
        this.tableName = tableName;
        this.columnName = columnName;
        this.rowNum = rowNum;
    }

    public String getTableName() {
        return tableName;
    }

    public String getColumnName() {
        return columnName;
    }

    public int getRowNum() {
        return rowNum;
    }
} 