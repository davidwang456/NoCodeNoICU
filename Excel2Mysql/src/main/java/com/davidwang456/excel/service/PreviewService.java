package com.davidwang456.excel.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.davidwang456.excel.enums.DataSourceType;
import com.davidwang456.excel.model.PreviewData;
import com.davidwang456.excel.model.PreviewResult;
import com.davidwang456.excel.service.preview.CsvPreviewReader;
import com.davidwang456.excel.service.preview.ExcelPreviewReader;
import com.davidwang456.excel.util.PinyinUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PreviewService {
    private final Map<String, PreviewData> previewCache = new ConcurrentHashMap<>();
    
    @Autowired
    private DynamicTableService dynamicTableService;

    @Autowired
    private MongoTableService mongoTableService;
    
    public PreviewResult previewFile(Path file, String fileExtension, String originalFileName) throws IOException {
        List<Map<String, Object>> data;
        List<String> headers;
        
        if ("csv".equals(fileExtension)) {
            // 处理CSV文件
            CsvPreviewReader reader = new CsvPreviewReader();
            data = reader.readPreview(file);
        } else {
            // 处理Excel文件
            ExcelPreviewReader reader = new ExcelPreviewReader();
            data = reader.readPreview(file);
        }
        
        if (!data.isEmpty()) {
            headers = new ArrayList<>(data.get(0).keySet());
        } else {
            headers = new ArrayList<>();
        }
        
        // 生成唯一的文件标识符，使用传入的原始文件名
        String fileId = UUID.randomUUID().toString();
        previewCache.put(fileId, new PreviewData(headers, data, file, originalFileName));
        
        return new PreviewResult(headers, 
            data.subList(0, Math.min(10, data.size())), 
            data.size(),
            fileId);  // 返回文件标识符
    }
    
    public Map<String, Object> getPreviewData(String fileName, int page, int size) {
        PreviewData previewData = previewCache.get(fileName);
        if (previewData == null) {
            throw new RuntimeException("预览数据不存在");
        }
        
        int start = (page - 1) * size;
        int end = Math.min(start + size, previewData.getData().size());
        
        Map<String, Object> result = new HashMap<>();
        result.put("content", previewData.getData().subList(start, end));
        result.put("total", previewData.getData().size());
        return result;
    }
    
    public void importData(String fileId, String dataSource) {
        if (fileId == null || fileId.trim().isEmpty()) {
            throw new IllegalArgumentException("文件ID不能为空");
        }
        
        PreviewData previewData = previewCache.get(fileId);
        if (previewData == null) {
            throw new RuntimeException("预览数据不存在，fileId: " + fileId);
        }

        try {
            // 使用原始文件名转拼音作为表名
            String originalFileName = previewData.getOriginalFileName();
            String tableName = PinyinUtil.toPinyin(
                originalFileName.substring(0, originalFileName.lastIndexOf("."))
            ).toLowerCase(); // 确保表名全小写
            
            // 移除特殊字符，只保留字母、数字和下划线
            tableName = tableName.replaceAll("[^a-z0-9_]", "_");
            
            // 确保表名不以数字开头
            if (tableName.matches("^\\d.*")) {
                tableName = "t_" + tableName;
            }
            
            DataSourceType type = DataSourceType.valueOf(dataSource);
            
            // 构建表头映射
            Map<Integer, String> headMap = new HashMap<>();
            List<String> headers = previewData.getHeaders();
            for (int i = 0; i < headers.size(); i++) {
                headMap.put(i, headers.get(i));
            }
            
            // 构建数据
            List<Map<Integer, String>> dataList = new ArrayList<>();
            for (Map<String, Object> row : previewData.getData()) {
                Map<Integer, String> dataRow = new HashMap<>();
                int i = 0;
                for (String header : headers) {
                    Object value = row.get(header);
                    dataRow.put(i++, value != null ? value.toString() : null);
                }
                dataList.add(dataRow);
            }
            
            // 执行导入
            if (type == DataSourceType.MYSQL || type == DataSourceType.BOTH) {
                dynamicTableService.createTable(tableName, headMap, inferDataTypes(dataList.get(0)));
                dynamicTableService.batchInsertData(tableName, headMap, dataList);
            }
            if (type == DataSourceType.MONGODB || type == DataSourceType.BOTH) {
                mongoTableService.createCollection(tableName);
                mongoTableService.batchInsertData(tableName, headMap, dataList);
            }
        } catch (Exception e) {
            throw new RuntimeException("导入数据失败: " + e.getMessage(), e);
        } finally {
            cleanup(fileId);
        }
    }
    
    public void cancelImport(String fileName) {
        cleanup(fileName);
    }
    
    private void cleanup(String fileId) {
        if (fileId != null) {
            PreviewData previewData = previewCache.remove(fileId);
            if (previewData != null && previewData.getFile() != null) {
                try {
                    Files.deleteIfExists(previewData.getFile());
                } catch (IOException e) {
                    // 记录日志但不抛出异常
                    System.err.println("清理临时文件失败: " + e.getMessage());
                }
            }
        }
    }

    private Map<Integer, String> inferDataTypes(Map<Integer, String> firstRow) {
        Map<Integer, String> dataTypes = new HashMap<>();
        for (Map.Entry<Integer, String> entry : firstRow.entrySet()) {
            String value = entry.getValue();
            String dataType = inferDataType(value);
            dataTypes.put(entry.getKey(), dataType);
        }
        return dataTypes;
    }

    private String inferDataType(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "VARCHAR(255)";
        }
        
        try {
            Integer.parseInt(value);
            return "INT";
        } catch (NumberFormatException e) {
            try {
                Double.parseDouble(value);
                return "DECIMAL(10,2)";
            } catch (NumberFormatException e2) {
                if (value.length() > 255) {
                    return "TEXT";
                }
                return "VARCHAR(255)";
            }
        }
    }
} 