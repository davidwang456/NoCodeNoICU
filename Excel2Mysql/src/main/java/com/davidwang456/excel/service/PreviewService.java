package com.davidwang456.excel.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.davidwang456.excel.enums.DataSourceType;
import com.davidwang456.excel.model.PreviewData;
import com.davidwang456.excel.model.PreviewResult;
import com.davidwang456.excel.service.preview.CsvPreviewReader;
import com.davidwang456.excel.service.preview.ExcelPreviewReader;
import com.davidwang456.excel.util.ExcelImageExtractor;
import com.davidwang456.excel.util.PinyinUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class PreviewService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PreviewService.class);
    
    // 预览数据缓存，键为文件ID，值为预览数据
    private final Map<String, PreviewData> previewCache = new ConcurrentHashMap<>();
    
    // 预览数据缓存时间，键为文件ID，值为创建时间戳
    private final Map<String, Long> previewCacheTimestamps = new ConcurrentHashMap<>();
    
    // 预览数据缓存过期时间（毫秒），默认30分钟
    private static final long CACHE_EXPIRATION_TIME = TimeUnit.MINUTES.toMillis(30);

    @Autowired
    private MysqlTableService dynamicTableService;

    @Autowired
    private MongoTableService mongoTableService;
    
    public PreviewResult previewFile(Path file, String fileExtension, String originalFileName) throws IOException {
        List<Map<String, Object>> data;
        List<String> headers;
        Map<String, byte[]> imageMap = new HashMap<>();
        Path tempImageFile = null;
        
        if ("csv".equals(fileExtension)) {
            // 处理CSV文件
            CsvPreviewReader reader = new CsvPreviewReader();
            data = reader.readPreview(file);
        } else {
            // 处理Excel文件
            ExcelPreviewReader reader = new ExcelPreviewReader();
            data = reader.readPreview(file);
            
            // 提取Excel中的图片
            try {
                // 创建文件的副本，用于图片提取，避免锁定原始文件
                tempImageFile = Files.createTempFile("image_extract_", "." + fileExtension);
                Files.copy(file, tempImageFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                
                // 从副本中提取图片
                imageMap = ExcelImageExtractor.extractImages(tempImageFile);
                LOGGER.info("从Excel文件中提取了{}张图片", imageMap.size());
                
                // 不在这里删除临时文件，而是在finally块中处理
            } catch (Exception e) {
                LOGGER.error("提取Excel图片时出错", e);
            } finally {
                // 确保在方法结束前尝试删除临时文件
                if (tempImageFile != null) {
                    try {
                        // 在删除前尝试强制GC，释放可能的文件句柄
                        System.gc();
                        Thread.sleep(100); // 给GC一点时间
                        
                        // 尝试多次删除文件，最多尝试3次
                        boolean deleted = false;
                        for (int i = 0; i < 3 && !deleted; i++) {
                            try {
                                deleted = Files.deleteIfExists(tempImageFile);
                                if (deleted) {
                                    LOGGER.info("成功删除图片提取临时文件: {} (尝试 {})", tempImageFile, i+1);
                                    break;
                                } else {
                                    LOGGER.warn("临时文件不存在或已被删除: {} (尝试 {})", tempImageFile, i+1);
                                }
                            } catch (IOException e) {
                                LOGGER.warn("删除图片提取临时文件失败: {} (尝试 {}) - {}", 
                                          tempImageFile, i+1, e.getMessage());
                                // 等待一段时间后重试
                                Thread.sleep(500 * (i + 1));
                            }
                        }
                        
                        if (!deleted) {
                            // 如果仍然无法删除，注册JVM关闭钩子
                            final Path fileToDelete = tempImageFile;
                            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                                try {
                                    Files.deleteIfExists(fileToDelete);
                                    LOGGER.info("在JVM关闭时成功删除临时文件: {}", fileToDelete);
                                } catch (IOException ex) {
                                    // 忽略，因为这是最后的尝试
                                }
                            }));
                            LOGGER.info("已注册JVM关闭钩子以删除临时文件: {}", tempImageFile);
                        }
                    } catch (Exception e) {
                        LOGGER.warn("处理临时文件时出错: {}", e.getMessage());
                    }
                }
            }
        }
        
        if (!data.isEmpty()) {
            headers = new ArrayList<>(data.get(0).keySet());
        } else {
            headers = new ArrayList<>();
        }
        
        // 生成唯一的文件标识符，使用传入的原始文件名
        String fileId = UUID.randomUUID().toString();
        previewCache.put(fileId, new PreviewData(headers, data, file, originalFileName, imageMap));
        previewCacheTimestamps.put(fileId, System.currentTimeMillis());
        LOGGER.info("添加预览数据到缓存: fileId={}, 原始文件名={}, 数据行数={}", fileId, originalFileName, data.size());
        
        return new PreviewResult(headers, 
            data.subList(0, Math.min(10, data.size())), 
            data.size(),
            fileId,
            originalFileName);  // 返回文件标识符
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
    
    @SuppressWarnings("unchecked")
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
            List<Map<Integer, Object>> dataList = new ArrayList<>();
            Map<String, byte[]> imageMap = previewData.getImageMap();
            
            // 记录图片信息
            if (imageMap != null && !imageMap.isEmpty()) {
                LOGGER.info("预览数据中包含{}张图片", imageMap.size());
                for (Map.Entry<String, byte[]> entry : imageMap.entrySet()) {
                    LOGGER.info("图片位置: {}, 大小: {}字节", entry.getKey(), entry.getValue().length);
                }
            } else {
                LOGGER.warn("预览数据中不包含图片");
            }
            
            int rowIndex = 0; // 从0开始，与Excel中的行索引保持一致
            for (Map<String, Object> row : previewData.getData()) {
                Map<Integer, Object> dataRow = new HashMap<>();
                int colIndex = 0;
                for (String header : headers) {
                    Object value = row.get(header);
                    
                    // 检查是否为图片标记
                    if (value != null && "[IMAGE]".equals(value.toString())) {
                        // 尝试从图片映射中获取图片数据
                        String imageKey = rowIndex + ":" + colIndex;
                        byte[] imageData = imageMap != null ? imageMap.get(imageKey) : null;
                        
                        if (imageData != null) {
                            LOGGER.info("找到图片数据: 位置={}, 大小={}字节", imageKey, imageData.length);
                            // 为每个图片数据添加唯一标识，避免相同图片被混淆
                            byte[] uniqueImageData = new byte[imageData.length + 8];
                            System.arraycopy(imageData, 0, uniqueImageData, 0, imageData.length);
                            
                            // 在图片数据末尾添加位置信息作为唯一标识
                            String positionMarker = String.format("R%dC%d", rowIndex, colIndex);
                            byte[] markerBytes = positionMarker.getBytes();
                            System.arraycopy(markerBytes, 0, uniqueImageData, imageData.length, Math.min(markerBytes.length, 8));
                            
                            dataRow.put(colIndex, uniqueImageData);
                            LOGGER.info("添加了唯一标识的图片数据: 位置={}, 标识={}", imageKey, positionMarker);
                        } else {
                            // 尝试使用ExcelImageExtractor.getImageAt方法
                            imageData = ExcelImageExtractor.getImageAt(imageMap, rowIndex, colIndex);
                            if (imageData != null) {
                                LOGGER.info("通过getImageAt找到图片数据: 行={}, 列={}, 大小={}字节", 
                                           rowIndex, colIndex, imageData.length);
                                // 为每个图片数据添加唯一标识
                                byte[] uniqueImageData = new byte[imageData.length + 8];
                                System.arraycopy(imageData, 0, uniqueImageData, 0, imageData.length);
                                
                                // 在图片数据末尾添加位置信息作为唯一标识
                                String positionMarker = String.format("R%dC%d", rowIndex, colIndex);
                                byte[] markerBytes = positionMarker.getBytes();
                                System.arraycopy(markerBytes, 0, uniqueImageData, imageData.length, Math.min(markerBytes.length, 8));
                                
                                dataRow.put(colIndex, uniqueImageData);
                                LOGGER.info("添加了唯一标识的图片数据: 位置={}, 标识={}", imageKey, positionMarker);
                            } else {
                                // 尝试查找相邻单元格的图片
                                for (int r = Math.max(0, rowIndex-1); r <= rowIndex+1; r++) {
                                    for (int c = Math.max(0, colIndex-1); c <= colIndex+1; c++) {
                                        if (r == rowIndex && c == colIndex) continue; // 跳过当前单元格
                                        
                                        String nearbyKey = r + ":" + c;
                                        byte[] nearbyImageData = imageMap != null ? imageMap.get(nearbyKey) : null;
                                        
                                        if (nearbyImageData != null) {
                                            LOGGER.info("在相邻单元格找到图片数据: 原位置={}, 找到位置={}, 大小={}字节", 
                                                      imageKey, nearbyKey, nearbyImageData.length);
                                            
                                            // 为每个图片数据添加唯一标识
                                            byte[] uniqueImageData = new byte[nearbyImageData.length + 8];
                                            System.arraycopy(nearbyImageData, 0, uniqueImageData, 0, nearbyImageData.length);
                                            
                                            // 在图片数据末尾添加位置信息作为唯一标识
                                            String positionMarker = String.format("R%dC%d", rowIndex, colIndex);
                                            byte[] markerBytes = positionMarker.getBytes();
                                            System.arraycopy(markerBytes, 0, uniqueImageData, nearbyImageData.length, Math.min(markerBytes.length, 8));
                                            
                                            dataRow.put(colIndex, uniqueImageData);
                                            LOGGER.info("添加了唯一标识的图片数据: 位置={}, 标识={}", imageKey, positionMarker);
                                            
                                            imageData = nearbyImageData;
                                            break;
                                        }
                                    }
                                    if (imageData != null) break;
                                }
                                
                                if (imageData == null) {
                                    LOGGER.warn("未找到图片数据: 行={}, 列={}", rowIndex, colIndex);
                                    // 使用特殊标记，表示这是一个图片列但没有找到图片数据
                                    dataRow.put(colIndex, "[IMAGE_PLACEHOLDER]");
                                }
                            }
                        }
                    } else {
                        dataRow.put(colIndex, value);
                    }
                    colIndex++;
                }
                dataList.add(dataRow);
                rowIndex++;
            }
            
            // 执行导入
            if (type == DataSourceType.MYSQL || type == DataSourceType.BOTH) {
                // 确保数据类型推断正确处理图片
                Map<Integer, String> dataTypes = inferDataTypes(dataList.get(0));
                
                // 打印推断的数据类型
                for (Map.Entry<Integer, String> entry : dataTypes.entrySet()) {
                    LOGGER.info("列 {} 推断类型: {}", headMap.get(entry.getKey()), entry.getValue());
                }
                
                dynamicTableService.createTable(tableName, headMap, dataTypes);
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
                // 添加延迟重试机制，最多尝试3次
                int maxRetries = 3;
                for (int i = 0; i < maxRetries; i++) {
                    try {
                        Files.deleteIfExists(previewData.getFile());
                        LOGGER.info("成功清理临时文件: {}", previewData.getFile());
                        return; // 删除成功，直接返回
                    } catch (IOException e) {
                        LOGGER.warn("清理临时文件失败(尝试 {}/{}): {} - {}", 
                                    i + 1, maxRetries, previewData.getFile(), e.getMessage());
                        if (i < maxRetries - 1) {
                            try {
                                // 等待一段时间后重试
                                Thread.sleep(500 * (i + 1));
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                LOGGER.error("等待重试时被中断", ie);
                                break;
                            }
                        } else {
                            // 最后一次尝试失败，记录错误但不抛出异常
                            LOGGER.error("清理临时文件失败: {} - {}", previewData.getFile(), e.getMessage());
                        }
                    }
                }
            }
        }
    }

    private Map<Integer, String> inferDataTypes(Map<Integer, Object> firstRow) {
        Map<Integer, String> dataTypes = new HashMap<>();
        for (Map.Entry<Integer, Object> entry : firstRow.entrySet()) {
            Object value = entry.getValue();
            String dataType = inferDataType(value);
            dataTypes.put(entry.getKey(), dataType);
        }
        return dataTypes;
    }

    private String inferDataType(Object value) {
        if (value == null) {
            return "VARCHAR(255)";
        }
        
        // 检查是否为图片数据
        if (value instanceof byte[]) {
            return "MEDIUMBLOB";
        }
        
        // 检查是否为图片标记
        if (value instanceof String) {
            String strValue = (String) value;
            if ("[IMAGE]".equals(strValue) || "[IMAGE_PLACEHOLDER]".equals(strValue)) {
                return "MEDIUMBLOB";  // 图片列统一使用MEDIUMBLOB类型
            }
        }
        
        String strValue = value.toString();
        if (strValue.trim().isEmpty()) {
            return "VARCHAR(255)";
        }
        
        try {
            Integer.parseInt(strValue);
            return "INT";
        } catch (NumberFormatException e) {
            try {
                Double.parseDouble(strValue);
                return "DECIMAL(10,2)";
            } catch (NumberFormatException e2) {
                if (strValue.length() > 255) {
                    return "TEXT";
                }
                return "VARCHAR(255)";
            }
        }
    }

    // 获取预览数据但不删除
    public PreviewResult getPreviewResult(String fileId) {
        PreviewData previewData = previewCache.get(fileId);
        if (previewData == null) {
            LOGGER.warn("预览数据不存在: fileId={}", fileId);
            return null;
        }
        
        // 更新缓存时间戳
        previewCacheTimestamps.put(fileId, System.currentTimeMillis());
        LOGGER.info("获取预览数据: fileId={}, 原始文件名={}", fileId, previewData.getOriginalFileName());
        
        return new PreviewResult(
            previewData.getHeaders(),
            previewData.getData(),
            previewData.getData().size(),
            fileId,
            PinyinUtil.toPinyin(
                previewData.getOriginalFileName().substring(0, 
                previewData.getOriginalFileName().lastIndexOf(".")))
                .toLowerCase()  // 使用原始文件名生成表名
        );
    }

    // 使用指定的预览数据进行导入
    @SuppressWarnings("unchecked")
    public void importDataWithPreview(PreviewResult previewData, String dataSource) {
        if (previewData == null) {
            throw new IllegalStateException("预览数据不存在");
        }

        String tableName = previewData.getTableName();
        List<String> headers = previewData.getHeaders();
        List<Map<String, Object>> content = previewData.getContent();

        try {
            // 构建表头映射
            Map<Integer, String> headMap = new HashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                headMap.put(i, headers.get(i));
            }

            // 构建数据
            List<Map<Integer, Object>> dataList = new ArrayList<>();
            for (Map<String, Object> row : content) {
                Map<Integer, Object> dataRow = new HashMap<>();
                int i = 0;
                for (String header : headers) {
                    Object value = row.get(header);
                    
                    // 检查是否为图片标记
                    if (value != null && "[IMAGE]".equals(value.toString())) {
                        // 由于PreviewResult中没有图片数据，所以这里只能设置为null
                        dataRow.put(i, null);
                    } else {
                        dataRow.put(i, value);
                    }
                    i++;
                }
                dataList.add(dataRow);
            }

            // 执行导入
            if ("MYSQL".equals(dataSource)) {
                dynamicTableService.createTable(tableName, headMap, inferDataTypes(dataList.get(0)));
                dynamicTableService.batchInsertData(tableName, headMap, dataList);
            } else if ("MONGODB".equals(dataSource)) {
                mongoTableService.createCollection(tableName);
                mongoTableService.batchInsertData(tableName, headMap, dataList);
            }
        } catch (Exception e) {
            throw new RuntimeException("导入数据失败: " + e.getMessage(), e);
        }
    }

    // 定时清理过期的预览数据缓存
    @Scheduled(fixedRate = 300000) // 每5分钟执行一次
    public void cleanupExpiredCache() {
        long currentTime = System.currentTimeMillis();
        List<String> expiredKeys = new ArrayList<>();
        
        // 找出过期的缓存项
        for (Map.Entry<String, Long> entry : previewCacheTimestamps.entrySet()) {
            if (currentTime - entry.getValue() > CACHE_EXPIRATION_TIME) {
                expiredKeys.add(entry.getKey());
            }
        }
        
        // 清理过期的缓存项
        for (String key : expiredKeys) {
            LOGGER.info("清理过期的预览数据缓存: fileId={}", key);
            cleanup(key);
            previewCacheTimestamps.remove(key);
        }
        
        LOGGER.info("预览数据缓存清理完成，当前缓存项数量: {}", previewCache.size());
    }

} 