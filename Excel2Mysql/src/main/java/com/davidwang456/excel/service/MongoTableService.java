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

import javax.annotation.PostConstruct;

import com.davidwang456.excel.util.PinyinUtil;
import org.bson.Document;
import org.bson.types.ObjectId;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;

@Service
public class MongoTableService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MongoTableService.class);
    private static final String METADATA_COLLECTION = "table_metadata";

    @Autowired
    private MongoTemplate mongoTemplate;

    @PostConstruct
    public void init() {
        // 确保元数据集合存在
        if (!mongoTemplate.collectionExists(METADATA_COLLECTION)) {
            mongoTemplate.createCollection(METADATA_COLLECTION);
            LOGGER.info("MongoDB元数据集合创建成功");
        }
    }

    public void saveColumnOrder(String tableName, List<String> columnOrder) {
        List<String> finalColumnOrder = new ArrayList<>();
        finalColumnOrder.addAll(columnOrder);

        // 先查询是否存在
        Query query = new Query(Criteria.where("table_name").is(tableName));
        Document existingMetadata = mongoTemplate.findOne(query, Document.class, METADATA_COLLECTION);

        if (existingMetadata != null) {
            // 如果存在，执行更新操作
            Update update = new Update()
                    .set("column_order", String.join(",", finalColumnOrder))
                    .set("update_time", new Date());
            mongoTemplate.updateFirst(query, update, METADATA_COLLECTION);
            LOGGER.info("MongoDB集合 {} 的列顺序已更新: {}", tableName, String.join(",", finalColumnOrder));
        } else {
            // 如果不存在，执行插入操作
            Document metadata = new Document();
            metadata.put("_id", new ObjectId());  // 显式设置_id
            metadata.put("table_name", tableName);
            metadata.put("column_order", String.join(",", finalColumnOrder));
            metadata.put("create_time", new Date());
            metadata.put("update_time", new Date());
            
            mongoTemplate.insert(metadata, METADATA_COLLECTION);
            LOGGER.info("MongoDB集合 {} 的列顺序已新增: {}", tableName, String.join(",", finalColumnOrder));
        }
    }

    public List<String> getColumnOrder(String tableName) {
        Query query = new Query(Criteria.where("table_name").is(tableName));
        Document metadata = mongoTemplate.findOne(query, Document.class, METADATA_COLLECTION);
        
        if (metadata != null && metadata.containsKey("column_order")) {
            // 将逗号分隔的字符串转回列表
            String columnOrderStr = metadata.getString("column_order");
            return Arrays.asList(columnOrderStr.split(","));
        } else {
            LOGGER.warn("获取MongoDB集合 {} 的列顺序失败: 未找到相关信息", tableName);
            return null;
        }
    }
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
        orderedColumns.add("_id"); // 添加 _id 字段
        
        // 格式化其他列名
        for (int i = 0; i < headMap.size(); i++) {
            String columnName = headMap.get(i);
            String formattedName = formatFieldName(columnName);
            if (!"_id".equals(formattedName)) { // 避免重复添加 _id
                orderedColumns.add(formattedName);
            }
        }
        
        // 保存列顺序
        saveColumnOrder(collectionName, orderedColumns);
        
        // 按照列顺序创建文档
        for (Map<Integer, String> data : dataList) {
            Map<String, Object> document = new LinkedHashMap<>();
            document.put("_id", new ObjectId()); // 为每个文档生成唯一的 _id
            
            for (int i = 0; i < headMap.size(); i++) {
                String fieldName = formatFieldName(headMap.get(i));
                if (!"_id".equals(fieldName)) { // 跳过 _id 字段
                    document.put(fieldName, data.get(i));
                }
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
    public void deleteData(String collectionName, String id) {
        // 获取列顺序
        List<String> columnOrder = getColumnOrder(collectionName);
        if (columnOrder == null || columnOrder.isEmpty()) {
            throw new RuntimeException("未找到表的列顺序信息");
        }

        // 找到第一个非_id的列名
        String firstColumn = columnOrder.stream()
                .filter(col -> !"_id".equals(col))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("未找到可用的查询列"));

        try {
            // 使用第一个非系统列进行查询和删除
            Query query = new Query(Criteria.where(firstColumn).is(id));
            Document doc = mongoTemplate.findOne(query, Document.class, collectionName);
            if (doc == null) {
                throw new RuntimeException("未找到要删除的文档");
            }

            // 使用找到的文档的_id进行删除
            Query deleteQuery = new Query(Criteria.where("_id").is(doc.get("_id")));
            DeleteResult result = mongoTemplate.remove(deleteQuery, collectionName);
            
            if (result.getDeletedCount() == 0) {
                throw new RuntimeException("删除文档失败");
            }
            
            LOGGER.info("MongoDB集合 {} 删除文档成功", collectionName);
        } catch (Exception e) {
            throw new RuntimeException("删除失败: " + e.getMessage());
        }
    }

    public void updateData(String collectionName, String id, Map<String, Object> data) {
        try {
            // 直接使用 _id 构建查询条件
            ObjectId objectId = new ObjectId(id);
            Query query = new Query(Criteria.where("_id").is(objectId));
            
            // 构建更新字段
            Update update = new Update();
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                if (!"_id".equals(entry.getKey())) {  // 跳过_id字段
                    String key = formatFieldName(entry.getKey());
                    update.set(key, entry.getValue());
                }
            }

            UpdateResult result = mongoTemplate.updateFirst(query, update, collectionName);
            if (result.getModifiedCount() == 0) {
                throw new RuntimeException("更新文档失败");
            }
            
            LOGGER.info("MongoDB集合 {} 更新文档成功, _id: {}", collectionName, id);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("无效的ObjectId格式: " + id);
        } catch (Exception e) {
            throw new RuntimeException("更新失败: " + e.getMessage());
        }
    }

    public Map<String, Object> getDataWithOrder(String collectionName, List<String> columnOrder, int page, int size) {
        int skip = (page - 1) * size;
        
        // 获取总数
        long total = mongoTemplate.count(new Query(), collectionName);
        
        // 获取分页数据
        Query query = new Query().skip(skip).limit(size);
        List<Document> documents = mongoTemplate.find(query, Document.class, collectionName);
        
        // 按照保存的列顺序重新组织数据
        List<Map<String, Object>> orderedData = documents.stream()
            .map(doc -> {
                Map<String, Object> orderedDoc = new LinkedHashMap<>();
                // 首先添加 _id 字段
                orderedDoc.put("_id", doc.get("_id").toString());
                // 然后按照保存的列顺序添加其他字段
                columnOrder.forEach(column -> {
                    Object value = doc.get(column);
                    orderedDoc.put(column, value);
                });
                return orderedDoc;
            })
            .collect(Collectors.toList());
        
        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("content", orderedData);
        result.put("total", total);
        result.put("headers", columnOrder);
        
        return result;
    }
}