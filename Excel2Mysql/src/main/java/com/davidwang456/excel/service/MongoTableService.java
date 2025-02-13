package com.davidwang456.excel.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import com.davidwang456.excel.util.PinyinUtil;

@Service
public class MongoTableService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MongoTableService.class);
    
    @Autowired
    private MongoTemplate mongoTemplate;

    public void createCollection(String collectionName) {
        if (mongoTemplate.collectionExists(collectionName)) {
            mongoTemplate.dropCollection(collectionName);
        }
        mongoTemplate.createCollection(collectionName);
        LOGGER.info("MongoDB集合 {} 创建成功", collectionName);
    }

    public void batchInsertData(String collectionName, Map<Integer, String> headMap, List<Map<Integer, String>> dataList) {
        List<Map<String, Object>> documents = new ArrayList<>();
        
        for (Map<Integer, String> data : dataList) {
            Map<String, Object> document = new HashMap<>();
            headMap.forEach((index, columnName) -> {
                String formattedColumnName = formatFieldName(columnName);
                document.put(formattedColumnName, data.get(index));
            });
            documents.add(document);
        }

        mongoTemplate.insert(documents, collectionName);
        LOGGER.info("MongoDB集合 {} 插入 {} 条数据", collectionName, documents.size());
    }

    private String formatFieldName(String fieldName) {
        // MongoDB的字段名规则比MySQL宽松，但我们仍然保持一致的命名规范
        String formatted = PinyinUtil.toPinyin(fieldName)
                .replaceAll("\\s+", "_")
                .replaceAll("[^a-z0-9_]", "_");
        
        if (formatted.matches("^\\d.*")) {
            formatted = "field_" + formatted;
        }
        return formatted;
    }
} 