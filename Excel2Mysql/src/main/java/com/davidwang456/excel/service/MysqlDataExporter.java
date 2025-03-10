package com.davidwang456.excel.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.NonNull;

import com.davidwang456.excel.exception.DataExportException;

@Service
public class MysqlDataExporter extends AbstractDataExporter {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MysqlTableService dynamicTableService;
    
    // 存储表中包含图片的列
    private final Map<String, Set<String>> tableImageColumns = new HashMap<>();

    @Override
    public List<Map<String, Object>> exportData(String tableName) {
        try {
            validateTableExists(tableName);
            
            List<String> columns = getHeaders(tableName);
            String columnList = String.join(",", columns);
            String sql = "SELECT " + columnList + " FROM `" + tableName + "`";
            
            // 获取表的列类型信息
            analyzeTableColumns(tableName);
            
            // 使用自定义的RowMapper处理数据
            return jdbcTemplate.query(sql, new RowMapper<Map<String, Object>>() {
                @Override
                public Map<String, Object> mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
                    try {
                        Map<String, Object> row = new HashMap<>();
                        ResultSetMetaData metaData = rs.getMetaData();
                        int columnCount = metaData.getColumnCount();
                        
                        for (int i = 1; i <= columnCount; i++) {
                            String columnName = metaData.getColumnLabel(i);
                            Object value = rs.getObject(i);
                            row.put(columnName, value);
                        }
                        
                        return processRow(row, tableName, rowNum);
                    } catch (Exception e) {
                        logger.error("处理MySQL数据行时出错: 表={}, 行={}", tableName, rowNum, e);
                        throw new SQLException("处理数据行时出错", e);
                    }
                }
            });
        } catch (Exception e) {
            logger.error("导出MySQL表数据时出错: 表={}", tableName, e);
            throw DataExportException.exportFailed(tableName, e.getMessage());
        }
    }

    @Override
    public List<String> getHeaders(String tableName) {
        try {
            String sql = "SHOW COLUMNS FROM `" + tableName + "`";
            List<Map<String, Object>> columns = jdbcTemplate.queryForList(sql);
            return columns.stream()
                    .map(column -> column.get("Field").toString())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("获取MySQL表头信息时出错: 表={}", tableName, e);
            throw DataExportException.exportFailed(tableName, "获取表头信息失败: " + e.getMessage());
        }
    }

    @Override
    public List<String> getTableList() {
        try {
            String sql = "SHOW TABLES";
            return jdbcTemplate.queryForList(sql, String.class);
        } catch (Exception e) {
            logger.error("获取MySQL表列表时出错", e);
            throw DataExportException.exportFailed("", "获取表列表失败: " + e.getMessage());
        }
    }

    @Override
    public List<String> getOrderedHeaders(String tableName) {
        try {
            List<String> orderedHeaders = getOrderedColumnNames(tableName);
            return orderedHeaders != null ? orderedHeaders : getHeaders(tableName);
        } catch (Exception e) {
            logger.error("获取MySQL表有序列名时出错: 表={}", tableName, e);
            throw DataExportException.exportFailed(tableName, "获取有序列名失败: " + e.getMessage());
        }
    }

    @Override
    protected List<String> getOrderedColumnNames(String tableName) {
        return dynamicTableService.getColumnOrder(tableName);
    }
    
    @Override
    protected boolean isImageField(String tableName, String fieldName) {
        analyzeTableColumns(tableName);
        Set<String> imageColumns = tableImageColumns.getOrDefault(tableName, Collections.emptySet());
        return imageColumns.contains(fieldName);
    }
    
    /**
     * 分析表的列类型，识别可能包含图片的列
     * @param tableName 表名
     */
    private void analyzeTableColumns(String tableName) {
        if (tableImageColumns.containsKey(tableName)) {
            return; // 已经分析过，不需要重复分析
        }
        
        Set<String> imageColumns = new HashSet<>();
        
        try {
            String sql = "SELECT COLUMN_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS " +
                         "WHERE TABLE_NAME = ? AND (DATA_TYPE = 'blob' OR DATA_TYPE = 'mediumblob' OR " +
                         "DATA_TYPE = 'longblob' OR DATA_TYPE = 'varbinary' OR DATA_TYPE = 'binary')";
            
            List<Map<String, Object>> columns = jdbcTemplate.queryForList(sql, tableName);
            
            for (Map<String, Object> column : columns) {
                String columnName = (String) column.get("COLUMN_NAME");
                imageColumns.add(columnName);
            }
            
            tableImageColumns.put(tableName, imageColumns);
            logger.info("表 {} 中的图片列: {}", tableName, imageColumns);
        } catch (Exception e) {
            logger.error("分析MySQL表列类型时出错: 表={}", tableName, e);
            throw DataExportException.exportFailed(tableName, "分析表列类型失败: " + e.getMessage());
        }
    }
    
    /**
     * 检查表是否包含图片列
     * @param tableName 表名
     * @return 如果表包含图片列则返回true，否则返回false
     */
    public boolean hasImageColumns(String tableName) {
        try {
            analyzeTableColumns(tableName);
            Set<String> imageColumns = tableImageColumns.getOrDefault(tableName, Collections.emptySet());
            return !imageColumns.isEmpty();
        } catch (Exception e) {
            logger.warn("检查表是否包含图片列时出错: 表={}", tableName, e);
            return false;
        }
    }
    
    /**
     * 检查指定列是否是图片列
     * @param tableName 表名
     * @param columnName 列名
     * @return 如果是图片列则返回true，否则返回false
     */
    public boolean isImageColumn(String tableName, String columnName) {
        try {
            return isImageField(tableName, columnName);
        } catch (Exception e) {
            logger.warn("检查列是否为图片列时出错: 表={}, 列={}", tableName, columnName, e);
            return false;
        }
    }
} 