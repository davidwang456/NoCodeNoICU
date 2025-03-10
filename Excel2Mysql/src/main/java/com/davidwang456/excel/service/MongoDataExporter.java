package com.davidwang456.excel.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;
import org.bson.Document;
import org.bson.types.Binary;
import org.bson.types.ObjectId;
import com.davidwang456.excel.exception.DataExportException;

import com.davidwang456.excel.util.ImageUtil;

@Service
public class MongoDataExporter extends AbstractDataExporter {
    
    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private MongoTableService mongoTableService;
    
    // 存储集合中包含图片的字段
    private final Map<String, Set<String>> collectionImageFields = new HashMap<>();

    @Override
    public List<Map<String, Object>> exportData(String tableName) {
        try {
            validateTableExists(tableName);
            
            // 先获取有序的表头
            List<String> orderedHeaders = getOrderedHeaders(tableName);
            if (orderedHeaders.isEmpty()) {
                logger.warn("未找到表 {} 的列信息", tableName);
                throw DataExportException.columnNotFound(tableName, "*");
            }

            // 分析集合字段，识别图片字段
            analyzeCollectionFields(tableName);
            
            // 使用 Document 类型来获取数据
            List<Document> rawData = mongoTemplate.findAll(Document.class, tableName);
            
            return rawData.stream()
                    .map(doc -> {
                        try {
                            Map<String, Object> row = new LinkedHashMap<>();
                            // 特殊处理 _id 字段
                            if (doc.get("_id") != null) {
                                ObjectId objectId = (ObjectId) doc.get("_id");
                                row.put("_id", objectId.toString());
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
                                                logger.info("转换MongoDB图片数据为Base64: 集合={}, 字段={}", tableName, header);
                                            } else {
                                                value = null;
                                                logger.warn("MongoDB图片数据转换失败: 集合={}, 字段={}", tableName, header);
                                            }
                                        } catch (Exception e) {
                                            logger.error("处理MongoDB图片数据时出错: 集合={}, 字段={}, 错误={}", 
                                                       tableName, header, e.getMessage());
                                            value = null;
                                        }
                                    }
                                    
                                    row.put(header, value);
                                }
                            }
                            
                            return processRow(row, tableName, rawData.indexOf(doc));
                        } catch (Exception e) {
                            logger.error("处理MongoDB文档时出错: 表={}, 文档ID={}", 
                                    tableName, doc.get("_id"), e);
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.toList());
        } catch (DataExportException e) {
            throw e;
        } catch (Exception e) {
            logger.error("从MongoDB导出数据失败: 表={}", tableName, e);
            throw DataExportException.exportFailed(tableName, e.getMessage());
        }
    }

    @Override
    public List<String> getHeaders(String tableName) {
        try {
            validateTableExists(tableName);
            
            // 直接从MongoDB获取第一条记录来确定字段
            Document firstDoc = mongoTemplate.findOne(
                new org.springframework.data.mongodb.core.query.Query(), 
                Document.class, 
                tableName
            );
            
            if (firstDoc == null) {
                logger.warn("表 {} 中没有数据", tableName);
                return new ArrayList<>();
            }

            return new ArrayList<>(firstDoc.keySet());
        } catch (Exception e) {
            logger.error("获取MongoDB表头失败: 表={}", tableName, e);
            throw DataExportException.exportFailed(tableName, "获取表头失败: " + e.getMessage());
        }
    }

    @Override
    public List<String> getTableList() {
        try {
            return mongoTemplate.getCollectionNames().stream()
                    .filter(name -> !name.startsWith("system."))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("获取MongoDB集合列表失败", e);
            throw DataExportException.exportFailed("", "获取集合列表失败: " + e.getMessage());
        }
    }

    @Override
    public List<String> getOrderedHeaders(String tableName) {
        try {
            List<String> orderedHeaders = getOrderedColumnNames(tableName);
            return orderedHeaders != null ? orderedHeaders : getHeaders(tableName);
        } catch (Exception e) {
            logger.error("获取MongoDB表有序列名时出错: 表={}", tableName, e);
            throw DataExportException.exportFailed(tableName, "获取有序列名失败: " + e.getMessage());
        }
    }

    @Override
    protected List<String> getOrderedColumnNames(String tableName) {
        return mongoTableService.getColumnOrder(tableName);
    }
    
    @Override
    protected boolean isImageField(String tableName, String fieldName) {
        analyzeCollectionFields(tableName);
        Set<String> imageFields = collectionImageFields.getOrDefault(tableName, Collections.emptySet());
        return imageFields.contains(fieldName);
    }
    
    /**
     * 分析集合的字段，识别可能包含图片的字段
     * @param tableName 集合名称
     */
    private void analyzeCollectionFields(String tableName) {
        if (collectionImageFields.containsKey(tableName)) {
            return; // 已经分析过，不需要重复分析
        }
        
        Set<String> imageFields = new HashSet<>();
        
        try {
            // 获取文档样本
            Document sampleDoc = mongoTemplate.findOne(
                new org.springframework.data.mongodb.core.query.Query().limit(1), 
                Document.class, 
                tableName
            );
            
            if (sampleDoc != null) {
                // 检查每个字段的类型
                for (String fieldName : sampleDoc.keySet()) {
                    Object value = sampleDoc.get(fieldName);
                    if (value instanceof Binary) {
                        imageFields.add(fieldName);
                        logger.info("发现MongoDB集合中的图片字段: 集合={}, 字段={}", tableName, fieldName);
                    }
                }
            }
            
            collectionImageFields.put(tableName, imageFields);
            logger.info("MongoDB集合 {} 中的图片字段: {}", tableName, imageFields);
        } catch (Exception e) {
            logger.error("分析MongoDB集合字段时出错: 表={}", tableName, e);
            throw DataExportException.exportFailed(tableName, "分析集合字段失败: " + e.getMessage());
        }
    }
    
    /**
     * 检查集合是否包含图片字段
     * @param collectionName 集合名称
     * @return 如果集合包含图片字段则返回true，否则返回false
     */
    public boolean hasImageFields(String collectionName) {
        try {
            analyzeCollectionFields(collectionName);
            Set<String> imageFields = collectionImageFields.getOrDefault(collectionName, Collections.emptySet());
            return !imageFields.isEmpty();
        } catch (Exception e) {
            logger.warn("检查集合是否包含图片字段时出错: 集合={}", collectionName, e);
            return false;
        }
    }
    
    /**
     * 检查指定字段是否是图片字段（公共方法）
     * @param collectionName 集合名称
     * @param fieldName 字段名称
     * @return 如果是图片字段则返回true，否则返回false
     */
    public boolean checkImageField(String collectionName, String fieldName) {
        try {
            return isImageField(collectionName, fieldName);
        } catch (Exception e) {
            logger.warn("检查字段是否为图片字段时出错: 集合={}, 字段={}", collectionName, fieldName, e);
            return false;
        }
    }
} 