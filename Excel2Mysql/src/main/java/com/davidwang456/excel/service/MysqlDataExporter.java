package com.davidwang456.excel.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;
import java.sql.Blob;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import com.davidwang456.excel.util.ImageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;

@Service
public class MysqlDataExporter implements DataExporter {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(MysqlDataExporter.class);
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MysqlTableService dynamicTableService;
    
    // 存储表中包含图片的列
    private final Map<String, Set<String>> tableImageColumns = new HashMap<>();

    @Override
    public List<Map<String, Object>> exportData(String tableName) {
        List<String> columns = getHeaders(tableName);
        String columnList = String.join(",", columns);
        String sql = "SELECT " + columnList + " FROM `" + tableName + "`";
        
        // 获取表的列类型信息
        analyzeTableColumns(tableName);
        Set<String> imageColumns = tableImageColumns.getOrDefault(tableName, Collections.emptySet());
        LOGGER.info("导出表 {} 数据，图片列: {}", tableName, imageColumns);
        
        // 使用自定义的RowMapper处理Blob类型
        return jdbcTemplate.query(sql, new RowMapper<Map<String, Object>>() {
            @Override
            public Map<String, Object> mapRow(ResultSet rs, int rowNum) throws SQLException {
                Map<String, Object> row = new HashMap<>();
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnLabel(i);
                    Object value = rs.getObject(i);
                    
                    // 处理Blob类型的图片
                    if (value instanceof Blob) {
                        try {
                            String base64Image = ImageUtil.blobToBase64((Blob) value);
                            if (base64Image != null) {
                                // 添加data:image前缀，以便在前端显示
                                value = "data:image/png;base64," + base64Image;
                                LOGGER.info("成功转换Blob图片: 表={}, 列={}, 行={}, 大小={}字节", 
                                          tableName, columnName, rowNum, base64Image.length());
                            } else {
                                value = null;
                                LOGGER.warn("Blob图片转换为null: 表={}, 列={}, 行={}", tableName, columnName, rowNum);
                            }
                        } catch (Exception e) {
                            LOGGER.error("处理Blob图片时出错: 表={}, 列={}, 行={}, 错误={}", 
                                       tableName, columnName, rowNum, e.getMessage(), e);
                            value = null;
                        }
                    } else if (value instanceof byte[] && imageColumns.contains(columnName)) {
                        // 处理byte[]类型的图片
                        try {
                            byte[] imageBytes = (byte[]) value;
                            if (imageBytes != null && imageBytes.length > 0) {
                                // 检查是否已经是base64编码的图片数据
                                String strValue = new String(imageBytes);
                                if (strValue.startsWith("data:image/")) {
                                    // 已经是base64编码的图片数据，直接使用
                                    value = strValue;
                                    LOGGER.info("检测到已编码的图片数据: 表={}, 列={}, 行={}, 长度={}字节", 
                                              tableName, columnName, rowNum, strValue.length());
                                } else {
                                    // 原始图片数据，需要转换为base64
                                    String base64Image = ImageUtil.bytesToBase64(imageBytes);
                                    if (base64Image != null) {
                                        // 检测图片类型并添加适当的MIME类型前缀
                                        String mimeType = ImageUtil.detectMimeType(imageBytes);
                                        value = "data:" + mimeType + ";base64," + base64Image;
                                        LOGGER.info("成功转换byte[]图片: 表={}, 列={}, 行={}, 类型={}, 大小={}字节, base64长度={}", 
                                                  tableName, columnName, rowNum, mimeType, imageBytes.length, base64Image.length());
                                        
                                        // 打印base64字符串的前50个字符，用于调试
                                        if (base64Image.length() > 50) {
                                            LOGGER.debug("Base64图片前缀: {}", base64Image.substring(0, 50));
                                        }
                                    } else {
                                        value = null;
                                        LOGGER.warn("byte[]图片转换为null: 表={}, 列={}, 行={}", tableName, columnName, rowNum);
                                    }
                                }
                            } else {
                                value = null;
                                LOGGER.warn("byte[]图片为空: 表={}, 列={}, 行={}", tableName, columnName, rowNum);
                            }
                        } catch (Exception e) {
                            LOGGER.error("处理byte[]图片时出错: 表={}, 列={}, 行={}, 错误={}", 
                                       tableName, columnName, rowNum, e.getMessage(), e);
                            value = null;
                        }
                    }
                    
                    row.put(columnName, value);
                }
                
                return row;
            }
        });
    }

    @Override
    public List<String> getHeaders(String tableName) {
        String sql = "SHOW COLUMNS FROM `" + tableName + "`";
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(sql);
        return columns.stream()
                .map(column -> column.get("Field").toString())
                .filter(field -> !"system_id".equals(field))
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getTableList() {
        String sql = "SHOW TABLES";
        return jdbcTemplate.queryForList(sql, String.class);
    }

    @Override
    public List<String> getOrderedHeaders(String tableName) {
        List<String> orderedHeaders = dynamicTableService.getColumnOrder(tableName);
        return orderedHeaders != null ? orderedHeaders : getHeaders(tableName);
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
            LOGGER.info("表 {} 中的图片列: {}", tableName, imageColumns);
        } catch (Exception e) {
            LOGGER.error("分析表列类型时出错: " + tableName, e);
            tableImageColumns.put(tableName, Collections.emptySet());
        }
    }
    
    /**
     * 检查表是否包含图片列
     * @param tableName 表名
     * @return 如果表包含图片列则返回true，否则返回false
     */
    public boolean hasImageColumns(String tableName) {
        analyzeTableColumns(tableName);
        Set<String> imageColumns = tableImageColumns.getOrDefault(tableName, Collections.emptySet());
        return !imageColumns.isEmpty();
    }
    
    /**
     * 检查指定列是否是图片列
     * @param tableName 表名
     * @param columnName 列名
     * @return 如果是图片列则返回true，否则返回false
     */
    public boolean isImageColumn(String tableName, String columnName) {
        analyzeTableColumns(tableName);
        Set<String> imageColumns = tableImageColumns.getOrDefault(tableName, Collections.emptySet());
        return imageColumns.contains(columnName);
    }
} 