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
import org.bson.types.Binary;
import java.util.Base64;

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
            List<String> columnOrder = Arrays.asList(columnOrderStr.split(","));
            LOGGER.info("从元数据表获取MongoDB集合 {} 的列顺序: {}", tableName, columnOrder);
            return columnOrder;
        } else {
            LOGGER.warn("获取MongoDB集合 {} 的列顺序失败: 未找到相关信息", tableName);
            
            // 尝试从集合中获取第一条记录的字段顺序
            try {
                Document firstDoc = mongoTemplate.findOne(new Query(), Document.class, tableName);
                if (firstDoc != null) {
                    List<String> columnOrder = new ArrayList<>(firstDoc.keySet());
                    LOGGER.info("从第一条记录获取MongoDB集合 {} 的列顺序: {}", tableName, columnOrder);
                    
                    // 保存到元数据表中
                    saveColumnOrder(tableName, columnOrder);
                    
                    return columnOrder;
                }
            } catch (Exception e) {
                LOGGER.error("尝试从集合获取列顺序时出错: {}", e.getMessage());
            }
            
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

    public void batchInsertData(String collectionName, Map<Integer, String> headMap, List<Map<Integer, Object>> dataList) {
        List<Map<String, Object>> documents = new ArrayList<>();
        
        // 获取有序的列名列表
        List<String> orderedColumns = new ArrayList<>();
        orderedColumns.add("_id"); // 添加 _id 字段
        
        // 格式化其他列名
        Map<Integer, String> formattedHeadMap = new LinkedHashMap<>();
        for (int i = 0; i < headMap.size(); i++) {
            String columnName = headMap.get(i);
            String formattedName = formatFieldName(columnName);
            formattedHeadMap.put(i, formattedName);
            
            if (!"_id".equals(formattedName)) { // 避免重复添加 _id
                orderedColumns.add(formattedName);
            }
        }
        
        LOGGER.info("MongoDB集合 {} 的列顺序: {}", collectionName, orderedColumns);
        
        // 保存列顺序
        saveColumnOrder(collectionName, orderedColumns);
        
        // 按照列顺序创建文档
        for (Map<Integer, Object> data : dataList) {
            Map<String, Object> document = new LinkedHashMap<>();
            document.put("_id", new ObjectId()); // 为每个文档生成唯一的 _id
            
            // 按照列顺序添加字段
            for (String fieldName : orderedColumns) {
                if ("_id".equals(fieldName)) {
                    continue; // 跳过 _id 字段，因为已经处理过了
                }
                
                // 查找对应的索引
                int index = -1;
                for (Map.Entry<Integer, String> entry : formattedHeadMap.entrySet()) {
                    if (fieldName.equals(entry.getValue())) {
                        index = entry.getKey();
                        break;
                    }
                }
                
                if (index != -1) {
                    Object value = data.get(index);
                    
                    // 处理图像数据
                    if (value != null) {
                        // 跳过图像标记，只保存实际的图像数据
                        if (value instanceof String && "[IMAGE]".equals(value)) {
                            continue;
                        }
                        
                        // 如果是图像数据（byte数组），直接保存
                        if (value instanceof byte[]) {
                            byte[] imageData = (byte[]) value;
                            LOGGER.info("MongoDB保存图片数据到字段: {}, 大小: {}字节", fieldName, imageData.length);
                            
                            // MongoDB中存储二进制数据需要使用Binary对象
                            document.put(fieldName, new org.bson.types.Binary(imageData));
                        } else {
                            document.put(fieldName, value);
                        }
                    }
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
        try {
            // 尝试将ID转换为ObjectId
            ObjectId objectId;
            try {
                objectId = new ObjectId(id);
            } catch (IllegalArgumentException e) {
                LOGGER.warn("无效的ObjectId格式: {}，尝试使用字符串ID", id);
                // 如果不是有效的ObjectId，尝试使用字符串ID
                Query query = new Query(Criteria.where("_id").is(id));
                Document doc = mongoTemplate.findOne(query, Document.class, collectionName);
                if (doc == null) {
                    throw new RuntimeException("ID不存在，无法删除数据");
                }
                objectId = null;
            }
            
            // 构建查询条件
            Query query;
            if (objectId != null) {
                query = new Query(Criteria.where("_id").is(objectId));
            } else {
                query = new Query(Criteria.where("_id").is(id));
            }
            
            DeleteResult result = mongoTemplate.remove(query, collectionName);
            if (result.getDeletedCount() == 0) {
                throw new RuntimeException("ID不存在，无法删除数据");
            }
            
            LOGGER.info("MongoDB集合 {} 删除文档成功, _id: {}", collectionName, id);
        } catch (RuntimeException e) {
            LOGGER.error("删除MongoDB集合 {} 数据失败: {}", collectionName, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            LOGGER.error("删除MongoDB集合 {} 数据时发生未知错误: {}", collectionName, e.getMessage(), e);
            throw new RuntimeException("删除失败: " + e.getMessage());
        }
    }

    public void updateData(String collectionName, String id, Map<String, Object> data) {
        try {
            // 尝试将ID转换为ObjectId
            ObjectId objectId;
            try {
                objectId = new ObjectId(id);
            } catch (IllegalArgumentException e) {
                LOGGER.warn("无效的ObjectId格式: {}，尝试使用字符串ID", id);
                // 如果不是有效的ObjectId，尝试使用字符串ID
                Query query = new Query(Criteria.where("_id").is(id));
                Document doc = mongoTemplate.findOne(query, Document.class, collectionName);
                if (doc == null) {
                    throw new RuntimeException("ID不存在，无法更新数据");
                }
                objectId = null;
            }
            
            // 构建查询条件
            Query query;
            if (objectId != null) {
                query = new Query(Criteria.where("_id").is(objectId));
            } else {
                query = new Query(Criteria.where("_id").is(id));
            }
            
            // 构建更新字段
            Update update = new Update();
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                if (!"_id".equals(entry.getKey())) {  // 跳过_id字段
                    String key = formatFieldName(entry.getKey());
                    
                    // 处理图片数据
                    Object value = entry.getValue();
                    if (value instanceof String && ((String) value).startsWith("data:image/")) {
                        try {
                            String base64String = (String) value;
                            int commaIndex = base64String.indexOf(",");
                            if (commaIndex > 0) {
                                String base64Data = base64String.substring(commaIndex + 1);
                                byte[] imageData = Base64.getDecoder().decode(base64Data);
                                value = new Binary(imageData);
                                LOGGER.info("MongoDB图片数据已转换，大小: {} 字节", imageData.length);
                            }
                        } catch (Exception e) {
                            LOGGER.error("处理MongoDB图片数据时出错: {}", e.getMessage(), e);
                        }
                    }
                    
                    update.set(key, value);
                }
            }

            UpdateResult result = mongoTemplate.updateFirst(query, update, collectionName);
            if (result.getModifiedCount() == 0) {
                if (result.getMatchedCount() > 0) {
                    LOGGER.info("MongoDB集合 {} 文档匹配但未修改, _id: {}", collectionName, id);
                } else {
                    throw new RuntimeException("ID不存在，无法更新数据");
                }
            } else {
                LOGGER.info("MongoDB集合 {} 更新文档成功, _id: {}", collectionName, id);
            }
        } catch (RuntimeException e) {
            LOGGER.error("更新MongoDB集合 {} 数据失败: {}", collectionName, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            LOGGER.error("更新MongoDB集合 {} 数据时发生未知错误: {}", collectionName, e.getMessage(), e);
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