package com.davidwang456.excel.service;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.builder.ExcelWriterBuilder;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.davidwang456.excel.util.ImageCellWriteHandler;

@Service
public class ExportService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExportService.class);

    @Autowired
    private MysqlDataExporter mysqlExporter;

    @Autowired
    private MongoDataExporter mongoExporter;

    private DataExporter getExporter(String dataSource) {
        return "MYSQL".equals(dataSource) ? mysqlExporter : mongoExporter;
    }

    public void exportToExcel(String tableName, String dataSource, OutputStream outputStream) {
        LOGGER.info("开始导出Excel: 表名={}, 数据源={}", tableName, dataSource);
        
        try {
            DataExporter exporter = getExporter(dataSource);
            List<Map<String, Object>> data = exporter.exportData(tableName);
            List<String> headers = exporter.getOrderedHeaders(tableName);
            
            // 检查是否有图片列
            boolean hasImages = false;
            List<Integer> imageColumnIndexes = new ArrayList<>();
            
            // 根据数据源类型处理图片列
            if (exporter instanceof MysqlDataExporter) {
                hasImages = ((MysqlDataExporter) exporter).hasImageColumns(tableName);
                if (hasImages) {
                    LOGGER.info("检测到MySQL表中包含图片列");
                    // 获取图片列的索引
                    for (int i = 0; i < headers.size(); i++) {
                        String columnName = headers.get(i);
                        if (((MysqlDataExporter) exporter).isImageColumn(tableName, columnName)) {
                            imageColumnIndexes.add(i);
                            LOGGER.info("MySQL列 '{}' (索引: {}) 是图片列", columnName, i);
                        }
                    }
                }
            } else if (exporter instanceof MongoDataExporter) {
                hasImages = ((MongoDataExporter) exporter).hasImageFields(tableName);
                if (hasImages) {
                    LOGGER.info("检测到MongoDB集合中包含图片字段");
                    // 获取MongoDB图片字段的索引
                    for (int i = 0; i < headers.size(); i++) {
                        String fieldName = headers.get(i);
                        if (((MongoDataExporter) exporter).isImageField(tableName, fieldName)) {
                            imageColumnIndexes.add(i);
                            LOGGER.info("MongoDB字段 '{}' (索引: {}) 是图片字段", fieldName, i);
                        }
                    }
                }
            }
            
            // 构建行数据（包括表头）
            List<List<Object>> rows = new ArrayList<>();
            rows.add(new ArrayList<>(headers));
            
            // 转换数据行
            for (Map<String, Object> rowMap : data) {
                List<Object> row = new ArrayList<>();
                
                for (String header : headers) {
                    Object value = rowMap.get(header);
                    
                    // 检查是否为图片数据，不进行预处理，保留原始数据给ImageCellWriteHandler处理
                    row.add(value);
                }
                
                rows.add(row);
            }
            
            // 配置Excel写入器，使用LongestMatchColumnWidthStyleStrategy设置列宽
            ExcelWriterBuilder writerBuilder = EasyExcel.write(outputStream)
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy());
            
            // 如果有图片数据，注册自定义的图片处理器
            if (hasImages && !imageColumnIndexes.isEmpty()) {
                LOGGER.info("注册ImageCellWriteHandler处理图片列: {}", imageColumnIndexes);
                writerBuilder.registerWriteHandler(new ImageCellWriteHandler(rows, imageColumnIndexes));
            }
            
            // 执行写入操作
            writerBuilder.sheet(tableName).doWrite(rows);
            
            LOGGER.info("Excel导出完成，总行数: {}", rows.size());
        } catch (Exception e) {
            LOGGER.error("导出Excel时发生错误: {}", e.getMessage(), e);
        }
    }
    
    public void exportToCsv(String tableName, String dataSource, OutputStream outputStream) {
        DataExporter exporter = getExporter(dataSource);
        
        // 检查表是否包含图片列
        if (hasImageColumns(tableName, dataSource)) {
            throw new UnsupportedOperationException("包含图片的表不支持导出为CSV格式，请使用Excel格式导出");
        }
        
        List<Map<String, Object>> data = exporter.exportData(tableName);
        List<String> orderedHeaders = exporter.getOrderedHeaders(tableName);

        try {
            // 写入CSV头部（保持顺序）
            outputStream.write(String.join(",", orderedHeaders).getBytes());
            outputStream.write("\n".getBytes());

            // 按照有序表头的顺序写入数据
            for (Map<String, Object> row : data) {
                List<String> rowValues = new ArrayList<>();
                for (String header : orderedHeaders) {
                    Object value = row.get(header);
                    
                    // 检查是否为图片数据
                    if (value != null && (value instanceof byte[] || 
                                         (value instanceof String && ((String) value).startsWith("data:image/")))) {
                        throw new UnsupportedOperationException("包含图片的表不支持导出为CSV格式，请使用Excel格式导出");
                    }
                    
                    rowValues.add(value != null ? escapeCSVValue(value.toString()) : "");
                }
                outputStream.write(String.join(",", rowValues).getBytes());
                outputStream.write("\n".getBytes());
            }
        } catch (UnsupportedOperationException e) {
            throw e; // 直接抛出不支持的操作异常
        } catch (Exception e) {
            throw new RuntimeException("导出CSV失败", e);
        }
    }

    private String escapeCSVValue(String value) {
        if (value == null) {
            return "";
        }
        // 如果值包含逗号、引号或换行符，则需要用引号包围
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    public List<String> getTableList(String dataSource) {
        return getExporter(dataSource).getTableList();
    }

    public Map<String, Object> getPageData(String tableName, String dataSource, int page, int size) {
        DataExporter exporter = getExporter(dataSource);
        List<Map<String, Object>> allData = exporter.exportData(tableName);
        List<String> orderedHeaders = exporter.getOrderedHeaders(tableName);
        
        int start = (page - 1) * size;
        int end = Math.min(start + size, allData.size());
        
        // 按照保存的列顺序重新组织数据
        List<Map<String, Object>> orderedData = allData.subList(start, end).stream()
            .map(row -> {
                Map<String, Object> orderedRow = new LinkedHashMap<>();
                for (String header : orderedHeaders) {
                    orderedRow.put(header, row.get(header));
                }
                return orderedRow;
            })
            .collect(Collectors.toList());
        
        Map<String, Object> result = new HashMap<>();
        result.put("content", orderedData);
        result.put("total", allData.size());
        result.put("headers", orderedHeaders);  // 添加有序的表头信息
        return result;
    }
    
    /**
     * 检查表是否包含图片列
     * @param tableName 表名
     * @param dataSource 数据源
     * @return 如果表包含图片列则返回true，否则返回false
     */
    public boolean hasImageColumns(String tableName, String dataSource) {
        DataExporter exporter = getExporter(dataSource);
        if (exporter instanceof MysqlDataExporter) {
            return ((MysqlDataExporter) exporter).hasImageColumns(tableName);
        } else if (exporter instanceof MongoDataExporter) {
            return ((MongoDataExporter) exporter).hasImageFields(tableName);
        }
        return false;
    }
} 