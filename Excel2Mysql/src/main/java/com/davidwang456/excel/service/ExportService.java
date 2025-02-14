package com.davidwang456.excel.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.OutputStream;
import java.util.*;
import java.util.stream.Collectors;

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
        DataExporter exporter = getExporter(dataSource);
        List<Map<String, Object>> data = exporter.exportData(tableName);
        List<String> orderedHeaders = exporter.getOrderedHeaders(tableName);

        // 转换数据格式以适应EasyExcel
        List<List<Object>> rows = new ArrayList<>();
        rows.add(new ArrayList<>(orderedHeaders)); // 使用有序的表头

        // 按照有序表头的顺序添加数据
        for (Map<String, Object> row : data) {
            List<Object> rowData = new ArrayList<>();
            for (String header : orderedHeaders) {
                rowData.add(row.get(header));
            }
            rows.add(rowData);
        }

        // 写入Excel
        EasyExcel.write(outputStream)
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                .sheet(tableName)
                .doWrite(rows);
    }

    public void exportToCsv(String tableName, String dataSource, OutputStream outputStream) {
        DataExporter exporter = getExporter(dataSource);
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
                    rowValues.add(value != null ? escapeCSVValue(value.toString()) : "");
                }
                outputStream.write(String.join(",", rowValues).getBytes());
                outputStream.write("\n".getBytes());
            }
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
} 