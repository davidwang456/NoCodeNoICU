package com.davidwang456.excel.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.OutputStream;
import java.util.*;

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
        List<String> headers = exporter.getHeaders(tableName);

        // 转换数据格式以适应EasyExcel
        List<List<Object>> rows = new ArrayList<>();
        rows.add(new ArrayList<>(headers)); // 添加表头

        // 添加数据行
        for (Map<String, Object> row : data) {
            List<Object> rowData = new ArrayList<>();
            for (String header : headers) {
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
        List<String> headers = exporter.getHeaders(tableName);

        try {
            // 写入CSV头部
            outputStream.write(String.join(",", headers).getBytes());
            outputStream.write("\n".getBytes());

            // 写入数据行
            for (Map<String, Object> row : data) {
                List<String> rowValues = new ArrayList<>();
                for (String header : headers) {
                    Object value = row.get(header);
                    rowValues.add(value != null ? escapeCSVValue(value.toString()) : "");
                }
                outputStream.write(String.join(",", rowValues).getBytes());
                outputStream.write("\n".getBytes());
            }
        } catch (Exception e) {
            LOGGER.error("导出CSV失败", e);
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
} 