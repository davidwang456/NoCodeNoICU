package com.davidwang456.excel.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;
import org.bson.Document;

@Service
public class MongoDataExporter implements DataExporter {
    
    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private MongoTableService mongoTableService;

    @Override
    public List<Map<String, Object>> exportData(String tableName) {
        try {
            // 先获取有序的表头
            List<String> orderedHeaders = getOrderedHeaders(tableName);
            if (orderedHeaders.isEmpty()) {
                System.out.println("未找到表 " + tableName + " 的列信息");
                return new ArrayList<>();
            }

            // 使用 Document 类型来获取数据
            List<Document> rawData = mongoTemplate.findAll(Document.class, tableName);
            
            return rawData.stream()
                    .map(doc -> {
                        Map<String, Object> orderedData = new LinkedHashMap<>();
                        for (String header : orderedHeaders) {
                            String formattedHeader = mongoTableService.formatFieldName(header);
                            orderedData.put(header, doc.get(formattedHeader));
                        }
                        return orderedData;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            System.out.println("从MongoDB导出数据失败: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public List<String> getHeaders(String tableName) {
        try {
            // 直接从MongoDB获取第一条记录来确定字段
            Document firstDoc = mongoTemplate.findOne(
                new org.springframework.data.mongodb.core.query.Query(), 
                Document.class, 
                tableName
            );
            
            if (firstDoc == null) {
                return new ArrayList<>();
            }

            // 移除 _id 字段
            firstDoc.remove("_id");
            return new ArrayList<>(firstDoc.keySet());
        } catch (Exception e) {
            System.out.println("获取MongoDB表头失败: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public List<String> getTableList() {
        return mongoTemplate.getCollectionNames().stream()
                .filter(name -> !name.startsWith("system."))
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getOrderedHeaders(String tableName) {
        // 优先从保存的元数据中获取列顺序
        List<String> orderedHeaders = mongoTableService.getColumnOrder(tableName);
        if (orderedHeaders != null && !orderedHeaders.isEmpty()) {
            return orderedHeaders;
        }
        
        // 如果没有保存的顺序，则从实际数据中获取
        return getHeaders(tableName);
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