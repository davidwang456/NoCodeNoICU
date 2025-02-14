package com.davidwang456.excel.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MongoDataExporter implements DataExporter {
    
    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private MongoTableService mongoTableService;

    @Override
    public List<Map<String, Object>> exportData(String tableName) {
        List<Map> rawData = mongoTemplate.findAll(Map.class, tableName);
        List<String> orderedHeaders = getOrderedHeaders(tableName);
        
        return rawData.stream()
                .map(doc -> {
                    // 使用 LinkedHashMap 保持字段顺序
                    Map<String, Object> orderedData = new LinkedHashMap<>();
                    // 按照保存的列顺序添加字段
                    for (String header : orderedHeaders) {
                        String formattedHeader = mongoTableService.formatFieldName(header);
                        orderedData.put(header, doc.get(formattedHeader));
                    }
                    return orderedData;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getHeaders(String tableName) {
        List<Map<String, Object>> data = exportData(tableName);
        if (data.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(data.get(0).keySet());
    }

    @Override
    public List<String> getTableList() {
        return mongoTemplate.getCollectionNames().stream()
                .filter(name -> !name.startsWith("system."))
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getOrderedHeaders(String tableName) {
        List<String> orderedHeaders = mongoTableService.getColumnOrder(tableName);
        return orderedHeaders != null ? orderedHeaders : getHeaders(tableName);
    }

    private Map<String, Object> removeSystemFields(Map<?, ?> document) {
        Map<String, Object> result = new LinkedHashMap<>();  // 改用 LinkedHashMap
        document.forEach((key, value) -> {
            if (key instanceof String && !key.equals("_id")) {
                result.put((String) key, value);
            }
        });
        return result;
    }
} 