package com.davidwang456.excel.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;
import org.bson.Document;
import org.bson.types.Binary;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davidwang456.excel.util.ImageUtil;

@Service
public class MongoDataExporter implements DataExporter {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(MongoDataExporter.class);
    
    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private MongoTableService mongoTableService;
    
    // 存储集合中包含图片的字段
    private final Map<String, Set<String>> collectionImageFields = new HashMap<>();

    @Override
    public List<Map<String, Object>> exportData(String tableName) {
        try {
            // 先获取有序的表头
            List<String> orderedHeaders = getOrderedHeaders(tableName);
            if (orderedHeaders.isEmpty()) {
                LOGGER.warn("未找到表 {} 的列信息", tableName);
                return new ArrayList<>();
            }

            // 分析集合字段，识别图片字段
            analyzeCollectionFields(tableName);
            
            // 使用 Document 类型来获取数据
            List<Document> rawData = mongoTemplate.findAll(Document.class, tableName);
            
            return rawData.stream()
                    .map(doc -> {
                        Map<String, Object> orderedData = new LinkedHashMap<>();
                        // 特殊处理 _id 字段
                        if (doc.get("_id") != null) {
                            ObjectId objectId = (ObjectId) doc.get("_id");
                            orderedData.put("_id", objectId.toString());
                        }
                        
                        // 处理其他字段
                        for (String header : orderedHeaders) {
                            if (!header.equals("_id")) {  // 跳过 _id，因为已经处理过了
                                String formattedHeader = mongoTableService.formatFieldName(header);
                                Object value = doc.get(formattedHeader);
                                
                                // 处理Binary类型的图片数据
                                if (value instanceof Binary) {
                                    try {
                                        String base64Image = ImageUtil.bytesToBase64(((Binary) value).getData());
                                        if (base64Image != null) {
                                            // 添加data:image前缀，以便在前端显示
                                            value = "data:image/png;base64," + base64Image;
                                            LOGGER.info("转换MongoDB图片数据为Base64: 集合={}, 字段={}", tableName, header);
                                        } else {
                                            value = null;
                                            LOGGER.warn("MongoDB图片数据转换失败: 集合={}, 字段={}", tableName, header);
                                        }
                                    } catch (Exception e) {
                                        LOGGER.error("处理MongoDB图片数据时出错: 集合={}, 字段={}, 错误={}", 
                                                   tableName, header, e.getMessage());
                                        value = null;
                                    }
                                }
                                
                                orderedData.put(header, value);
                            }
                        }
                        return orderedData;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            LOGGER.error("从MongoDB导出数据失败: {}", e.getMessage(), e);
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

            return new ArrayList<>(firstDoc.keySet());
        } catch (Exception e) {
            LOGGER.error("获取MongoDB表头失败: {}", e.getMessage());
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
    
    /**
     * 分析集合的字段，识别可能包含图片的字段
     * @param collectionName 集合名称
     */
    private void analyzeCollectionFields(String collectionName) {
        if (collectionImageFields.containsKey(collectionName)) {
            return; // 已经分析过，不需要重复分析
        }
        
        Set<String> imageFields = new HashSet<>();
        
        try {
            // 获取文档样本
            Document sampleDoc = mongoTemplate.findOne(
                new org.springframework.data.mongodb.core.query.Query().limit(1), 
                Document.class, 
                collectionName
            );
            
            if (sampleDoc != null) {
                // 检查每个字段的类型
                for (String fieldName : sampleDoc.keySet()) {
                    Object value = sampleDoc.get(fieldName);
                    if (value instanceof Binary) {
                        imageFields.add(fieldName);
                        LOGGER.info("发现MongoDB集合中的图片字段: 集合={}, 字段={}", collectionName, fieldName);
                    }
                }
            }
            
            collectionImageFields.put(collectionName, imageFields);
            LOGGER.info("MongoDB集合 {} 中的图片字段: {}", collectionName, imageFields);
        } catch (Exception e) {
            LOGGER.error("分析MongoDB集合字段时出错: {}", e.getMessage(), e);
            collectionImageFields.put(collectionName, Collections.emptySet());
        }
    }
    
    /**
     * 检查集合是否包含图片字段
     * @param collectionName 集合名称
     * @return 如果集合包含图片字段则返回true，否则返回false
     */
    public boolean hasImageFields(String collectionName) {
        analyzeCollectionFields(collectionName);
        Set<String> imageFields = collectionImageFields.getOrDefault(collectionName, Collections.emptySet());
        return !imageFields.isEmpty();
    }
    
    /**
     * 检查指定字段是否是图片字段
     * @param collectionName 集合名称
     * @param fieldName 字段名称
     * @return 如果是图片字段则返回true，否则返回false
     */
    public boolean isImageField(String collectionName, String fieldName) {
        analyzeCollectionFields(collectionName);
        Set<String> imageFields = collectionImageFields.getOrDefault(collectionName, Collections.emptySet());
        return imageFields.contains(fieldName);
    }
} 