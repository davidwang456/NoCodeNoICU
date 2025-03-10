package com.davidwang456.excel.service;

import com.davidwang456.excel.service.strategy.ImageProcessorManager;
import com.davidwang456.excel.exception.DataExportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.*;

/**
 * 抽象数据导出器基类
 */
public abstract class AbstractDataExporter implements DataExporter {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    
    @Autowired
    protected ImageProcessorManager imageProcessorManager;
    
    /**
     * 处理单行数据
     * @param row 原始数据行
     * @param tableName 表名
     * @param rowNum 行号
     * @return 处理后的数据行
     * @throws DataExportException 如果处理过程中出现错误
     */
    protected Map<String, Object> processRow(Map<String, Object> row, String tableName, int rowNum) {
        if (row == null) {
            throw DataExportException.exportFailed(tableName, "数据行为空");
        }

        Map<String, Object> processedRow = new LinkedHashMap<>();
        
        try {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String columnName = entry.getKey();
                Object value = entry.getValue();
                
                // 处理可能的图片数据
                if (isImageField(tableName, columnName)) {
                    try {
                        String processedImage = imageProcessorManager.processImage(value, tableName, columnName, rowNum);
                        processedRow.put(columnName, processedImage);
                    } catch (Exception e) {
                        logger.warn("处理图片数据失败: 表={}, 列={}, 行={}", tableName, columnName, rowNum, e);
                        throw DataExportException.imageProcessingError(tableName, columnName, e.getMessage());
                    }
                } else {
                    processedRow.put(columnName, value);
                }
            }
            
            return processedRow;
        } catch (DataExportException e) {
            throw e;
        } catch (Exception e) {
            logger.error("处理数据行时发生错误: 表={}, 行={}", tableName, rowNum, e);
            throw DataExportException.exportFailed(tableName, "处理数据行时发生错误: " + e.getMessage());
        }
    }
    
    /**
     * 验证表是否存在
     * @param tableName 表名
     * @throws DataExportException 如果表不存在
     */
    protected void validateTableExists(String tableName) {
        if (!getTableList().contains(tableName)) {
            throw DataExportException.tableNotFound(tableName);
        }
    }
    
    /**
     * 验证列是否存在
     * @param tableName 表名
     * @param columnName 列名
     * @throws DataExportException 如果列不存在
     */
    protected void validateColumnExists(String tableName, String columnName) {
        if (!getHeaders(tableName).contains(columnName)) {
            throw DataExportException.columnNotFound(tableName, columnName);
        }
    }
    
    /**
     * 判断字段是否为图片字段
     * @param tableName 表名
     * @param fieldName 字段名
     * @return 是否为图片字段
     */
    protected abstract boolean isImageField(String tableName, String fieldName);
    
    /**
     * 获取表的有序列名
     * @param tableName 表名
     * @return 有序列名列表
     */
    protected abstract List<String> getOrderedColumnNames(String tableName);
} 