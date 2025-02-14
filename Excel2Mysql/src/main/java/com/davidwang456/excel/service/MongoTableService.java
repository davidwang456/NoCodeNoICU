package com.davidwang456.excel.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import com.davidwang456.excel.util.PinyinUtil;
import org.bson.Document;

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
        
        // 获取有序的列名列表
        List<String> orderedColumns = new ArrayList<>();
        for (int i = 0; i < headMap.size(); i++) {
            String columnName = headMap.get(i);
            orderedColumns.add(formatFieldName(columnName));
        }
        
        for (Map<Integer, String> data : dataList) {
            // 使用LinkedHashMap保持字段顺序
            Map<String, Object> document = new LinkedHashMap<>();
            // 按照列的顺序添加字段
            for (int i = 0; i < orderedColumns.size(); i++) {
                String fieldName = orderedColumns.get(i);
                document.put(fieldName, data.get(i));
            }
            documents.add(document);
        }

        mongoTemplate.insert(documents, collectionName);
        LOGGER.info("MongoDB集合 {} 插入 {} 条数据", collectionName, documents.size());
    }

    public String formatFieldName(String fieldName) {
        // MongoDB的字段名规则比MySQL宽松，但我们仍然保持一致的命名规范
        String formatted = PinyinUtil.toPinyin(fieldName)
                .replaceAll("\\s+", "_")
                .replaceAll("[^a-z0-9_]", "_");
        
        if (formatted.matches("^\\d.*")) {
            formatted = "field_" + formatted;
        }
        return formatted;
    }

    public void saveColumnOrder(String tableName, List<String> columnOrder) {
        // 在一个单独的集合中保存列顺序
        Document metadata = new Document();
        metadata.put("tableName", tableName);
        metadata.put("columnOrder", columnOrder);
        
        // 更新或插入列顺序信息
        mongoTemplate.upsert(
            Query.query(Criteria.where("tableName").is(tableName)),
            Update.fromDocument(metadata),
            "table_metadata"
        );
    }
    
    public List<String> getColumnOrder(String tableName) {
        Document metadata = mongoTemplate.findOne(
            Query.query(Criteria.where("tableName").is(tableName)),
            Document.class,
            "table_metadata"
        );
        return metadata != null ? (List<String>) metadata.get("columnOrder") : null;
    }
} 