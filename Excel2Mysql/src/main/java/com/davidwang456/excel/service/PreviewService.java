package com.davidwang456.excel.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    
    @SuppressWarnings("unused")
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
                
                if (imageMap != null && !imageMap.isEmpty()) {
                    LOGGER.info("从Excel文件中提取了{}张图片", imageMap.size());
                    
                    // 打印图片位置信息，帮助调试
                    for (Map.Entry<String, byte[]> entry : imageMap.entrySet()) {
                        LOGGER.info("图片位置: {}, 大小: {}字节", entry.getKey(), entry.getValue().length);
                    }
                } else {
                    LOGGER.warn("未能从Excel文件中提取图片或提取过程出错");
                }
            } catch (Exception e) {
                LOGGER.error("提取Excel图片时出错: {}", e.getMessage(), e);
                // 出错时创建空的图片映射
                imageMap = new HashMap<>();
            }
        }
        
        if (!data.isEmpty()) {
            headers = new ArrayList<>(data.get(0).keySet());
        } else {
            headers = new ArrayList<>();
        }
        
        // 检查图片列并标记 - 即使图片提取失败，也要基于列名检测图片列
        if (!data.isEmpty() && data.get(0) != null) {
            Map<String, Object> firstRow = data.get(0);
            
            // 先识别所有图片列
            List<String> imageColumns = new ArrayList<>();
            
            // 1. 基于列名关键词识别图片列
            for (Map.Entry<String, Object> entry : firstRow.entrySet()) {
                String header = entry.getKey();
                String lowerHeader = header.toLowerCase();
                
                // 图片相关的关键词列表
                List<String> imageKeywords = Arrays.asList(
                    "图片", "照片", "相片", "pic", "image", "photo", "picture", "logo", "icon", "头像", "img", "图像"
                );
                
                // 检查列名是否包含图片关键词
                boolean isImageColumn = false;
                for (String keyword : imageKeywords) {
                    if (lowerHeader.contains(keyword.toLowerCase())) {
                        isImageColumn = true;
                        break;
                    }
                }
                
                if (isImageColumn) {
                    imageColumns.add(header);
                    LOGGER.info("基于列名关键词检测到图片列: {}", header);
                }
            }
            
            // 2. 如果没有基于列名识别到图片列，但有图片数据，尝试找到最合适的列
            if (imageColumns.isEmpty() && imageMap!=null&& !imageMap.isEmpty()) {
                LOGGER.info("基于列名未识别到图片列，但有{}张图片，尝试找到最合适的列", imageMap.size());
                
                // 优先选择名称中包含"pic"的列
                for (String header : headers) {
                    if (header.toLowerCase().equals("pic")) {
                        imageColumns.add(header);
                        LOGGER.info("找到精确匹配的图片列: {}", header);
                        break;
                    }
                }
                
                // 如果仍未找到，尝试使用最后一列作为图片列
                if (imageColumns.isEmpty() && !headers.isEmpty()) {
                    String lastColumn = headers.get(headers.size() - 1);
                    imageColumns.add(lastColumn);
                    LOGGER.info("未找到合适的图片列，使用最后一列作为图片列: {}", lastColumn);
                }
            }
            
            // 3. 如果识别到的图片列数量少于图片数量，且有明确的"pic"列，则只使用该列
            if (imageMap!=null&&imageMap.size() > imageColumns.size()) {
                // 检查是否有明确的"pic"列
                boolean hasPicColumn = false;
                for (String header : headers) {
                    if (header.toLowerCase().equals("pic")) {
                        if (!imageColumns.contains(header)) {
                            imageColumns.clear(); // 清除之前识别的列
                            imageColumns.add(header);
                            hasPicColumn = true;
                            LOGGER.info("找到明确的pic列，使用它作为唯一图片列: {}", header);
                        }
                        break;
                    }
                }
                
                // 如果没有明确的"pic"列，但有其他可能的图片列
                if (!hasPicColumn && imageColumns.isEmpty()) {
                    // 尝试查找最可能的图片列
                    for (String header : headers) {
                        if (header.toLowerCase().contains("pic") || 
                            header.toLowerCase().contains("image") || 
                            header.toLowerCase().contains("图片")) {
                            imageColumns.add(header);
                            LOGGER.info("找到可能的图片列: {}", header);
                        }
                    }
                }
            }
            
            LOGGER.info("最终识别到{}个图片列: {}", imageColumns.size(), imageColumns);
            
            // 如果有多个图片列，确保每列使用不同的图片
            if (imageColumns.size() > 1 && imageMap!=null&&!imageMap.isEmpty()) {
                LOGGER.info("检测到{}个图片列，确保每列使用不同的图片", imageColumns.size());
                
                // 获取所有可用的图片
                List<String> imageKeys = new ArrayList<>(imageMap.keySet());
                Collections.sort(imageKeys); // 排序以确保顺序一致
                
                // 为每个图片列分配一个图片
                for (int i = 0; i < imageColumns.size(); i++) {
                    String header = imageColumns.get(i);
                    int colIndex = getColumnIndex(headers, header);
                    
                    // 选择对应的图片，如果图片不够，循环使用
                    String imageKey = i < imageKeys.size() ? imageKeys.get(i) : imageKeys.get(i % imageKeys.size());
                    byte[] pictureData = imageMap.get(imageKey);
                    
                    if (pictureData != null) {
                        String base64Image = convertToBase64Image(pictureData);
                        LOGGER.info("为图片列 {} (索引={}) 分配图片: 位置={}, 大小={}字节", 
                                  header, colIndex, imageKey, pictureData.length);
                        
                        // 为每一行设置图片
                        int rowIndex = 0;
                        for (Map<String, Object> row : data) {
                            row.put(header, base64Image);
                            rowIndex++;
                        }
                    }
                }
            } else if (imageColumns.size() == 1 && imageMap!=null&&!imageMap.isEmpty()) {
                // 单个图片列的处理
                String header = imageColumns.get(0);
                LOGGER.info("处理单个图片列: {}", header);
                
                // 获取所有可用的图片
                List<String> imageKeys = new ArrayList<>(imageMap.keySet());
                Collections.sort(imageKeys); // 排序以确保顺序一致
                
                // 为每一行分配图片
                int rowIndex = 0;
                for (Map<String, Object> row : data) {
                    // 选择对应行的图片，如果图片不够，循环使用
                    String imageKey = rowIndex < imageKeys.size() ? 
                                    imageKeys.get(rowIndex) : 
                                    imageKeys.get(rowIndex % imageKeys.size());
                    byte[] pictureData = imageMap.get(imageKey);
                    
                    if (pictureData != null) {
                        String base64Image = convertToBase64Image(pictureData);
                        LOGGER.info("为行 {} 的图片列 {} 分配图片: 位置={}, 大小={}字节", 
                                  rowIndex, header, imageKey, pictureData.length);
                        row.put(header, base64Image);
                    } else {
                        row.put(header, "[IMAGE]");
                        LOGGER.warn("未找到行 {} 的图片数据，标记为[IMAGE]", rowIndex);
                    }
                    rowIndex++;
                }
            }
        }
        
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
                                        
                                        String otherKey = r + ":" + c;
                                        if (imageMap!=null&&imageMap.containsKey(otherKey)) {
                                            LOGGER.info("在相邻单元格找到图片: 当前位置=[{},{}], 相邻位置=[{},{}]", 
                                                       rowIndex, colIndex, r, c);
                                            byte[] otherImageData = imageMap.get(otherKey);
                                            
                                            // 为图片数据添加唯一标识
                                            byte[] uniqueImageData = new byte[otherImageData.length + 8];
                                            System.arraycopy(otherImageData, 0, uniqueImageData, 0, otherImageData.length);
                                            
                                            // 在图片数据末尾添加位置信息
                                            String positionMarker = String.format("R%dC%d", rowIndex, colIndex);
                                            byte[] markerBytes = positionMarker.getBytes();
                                            System.arraycopy(markerBytes, 0, uniqueImageData, otherImageData.length, 
                                                           Math.min(markerBytes.length, 8));
                                            
                                            dataRow.put(colIndex, uniqueImageData);
                                            LOGGER.info("添加了相邻单元格的图片数据: 位置={}, 标识={}", otherKey, positionMarker);
                                            break;
                                        }
                                    }
                                }
                                
                                // 如果仍然找不到图片数据，尝试找第一张图片
                                if (!dataRow.containsKey(colIndex) && imageMap != null && !imageMap.isEmpty()) {
                                    LOGGER.warn("未找到指定位置的图片: 行={}, 列={}", rowIndex, colIndex);
                                    
                                    // 检查是否有同一列的其他行的图片
                                    boolean foundColumnImage = false;
                                    for (int r = 0; r < previewData.getData().size(); r++) {
                                        if (r == rowIndex) continue; // 跳过当前行
                                        
                                        String sameColKey = r + ":" + colIndex;
                                        if (imageMap.containsKey(sameColKey)) {
                                            LOGGER.info("在同一列的其他行找到图片: 当前行={}, 找到行={}, 列={}", 
                                                      rowIndex, r, colIndex);
                                            byte[] sameColImageData = imageMap.get(sameColKey);
                                            
                                            // 为图片数据添加唯一标识
                                            byte[] uniqueImageData = new byte[sameColImageData.length + 8];
                                            System.arraycopy(sameColImageData, 0, uniqueImageData, 0, sameColImageData.length);
                                            
                                            // 在图片数据末尾添加位置信息
                                            String positionMarker = String.format("R%dC%d", rowIndex, colIndex);
                                            byte[] markerBytes = positionMarker.getBytes();
                                            System.arraycopy(markerBytes, 0, uniqueImageData, sameColImageData.length, 
                                                           Math.min(markerBytes.length, 8));
                                            
                                            dataRow.put(colIndex, uniqueImageData);
                                            LOGGER.info("添加了同列其他行的图片数据: 原位置={}, 新位置={}, 标识={}", 
                                                      sameColKey, rowIndex + ":" + colIndex, positionMarker);
                                            foundColumnImage = true;
                                            break;
                                        }
                                    }
                                    
                                    // 如果没有找到同列的图片，使用默认的空白图片
                                    if (!foundColumnImage) {
                                        LOGGER.warn("未能找到同列的其他图片数据，使用默认空白图片: 行={}, 列={}", rowIndex, colIndex);
                                        
                                        // 创建一个极小的空白图片数据(1x1像素的透明PNG)
                                        byte[] emptyImageData = new byte[] {
                                            (byte)0x89, (byte)0x50, (byte)0x4E, (byte)0x47, (byte)0x0D, (byte)0x0A, (byte)0x1A, (byte)0x0A,
                                            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x0D, (byte)0x49, (byte)0x48, (byte)0x44, (byte)0x52,
                                            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x01, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x01,
                                            (byte)0x08, (byte)0x06, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x1F, (byte)0x15, (byte)0xC4,
                                            (byte)0x89, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x0A, (byte)0x49, (byte)0x44, (byte)0x41,
                                            (byte)0x54, (byte)0x78, (byte)0x9C, (byte)0x63, (byte)0x00, (byte)0x01, (byte)0x00, (byte)0x00,
                                            (byte)0x05, (byte)0x00, (byte)0x01, (byte)0x0D, (byte)0x0A, (byte)0x2D, (byte)0xB4, (byte)0x00,
                                            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x49, (byte)0x45, (byte)0x4E, (byte)0x44, (byte)0xAE,
                                            (byte)0x42, (byte)0x60, (byte)0x82
                                        };
                                        
                                        // 为图片数据添加唯一标识，确保同一列的图片与其他列不同
                                        byte[] uniqueImageData = new byte[emptyImageData.length + 8];
                                        System.arraycopy(emptyImageData, 0, uniqueImageData, 0, emptyImageData.length);
                                        
                                        // 在图片数据末尾添加列索引作为唯一标识
                                        String positionMarker = String.format("C%d", colIndex);
                                        byte[] markerBytes = positionMarker.getBytes();
                                        System.arraycopy(markerBytes, 0, uniqueImageData, emptyImageData.length, 
                                                       Math.min(markerBytes.length, 8));
                                        
                                        dataRow.put(colIndex, uniqueImageData);
                                        LOGGER.info("添加了默认空白图片数据: 位置={}, 标识={}", rowIndex + ":" + colIndex, positionMarker);
                                    }
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
            
            // 收集列数据类型
            Map<Integer, String> columnTypes = null;
            if (!dataList.isEmpty()) {
                columnTypes = inferDataTypes(dataList.get(0));
                LOGGER.info("推断的列数据类型: {}", columnTypes);
            }

            // 执行导入
            if (type == DataSourceType.MYSQL) {
                dynamicTableService.createTable(tableName, headMap, columnTypes);
                dynamicTableService.batchInsertData(tableName, headMap, dataList);
                LOGGER.info("数据已导入到MySQL表: {}", tableName);
            } else if (type == DataSourceType.MONGODB) {
                mongoTableService.createCollection(tableName);
                mongoTableService.batchInsertData(tableName, headMap, dataList);
                LOGGER.info("数据已导入到MongoDB集合: {}", tableName);
            } else if (type == DataSourceType.BOTH) {
                // MySQL导入
                dynamicTableService.createTable(tableName, headMap, columnTypes);
                dynamicTableService.batchInsertData(tableName, headMap, dataList);
                
                // MongoDB导入
                mongoTableService.createCollection(tableName);
                mongoTableService.batchInsertData(tableName, headMap, dataList);
                
                LOGGER.info("数据已同时导入到MySQL表和MongoDB集合: {}", tableName);
            }
        } catch (Exception e) {
            LOGGER.error("导入数据时出错: {}", e.getMessage(), e);
            throw new RuntimeException("导入数据失败: " + e.getMessage(), e);
        } finally {
            try {
                // 导入完成后从缓存中移除预览数据
                previewCache.remove(fileId);
                previewCacheTimestamps.remove(fileId);
                LOGGER.info("预览数据已从缓存中移除: {}", fileId);
            } catch (Exception e) {
                LOGGER.warn("移除预览数据缓存时出错: {}", e.getMessage());
            }
        }
    }
    
    public void cancelImport(String fileName) {
        cleanup(fileName);
    }
    
    private void cleanup(String fileId) {
        if (fileId != null) {
            PreviewData previewData = previewCache.remove(fileId);
            if (previewData != null && previewData.getTempFile() != null) {
                // 添加延迟重试机制，最多尝试3次
                int maxRetries = 3;
                for (int i = 0; i < maxRetries; i++) {
                    try {
                        Files.deleteIfExists(previewData.getTempFile());
                        LOGGER.info("成功清理临时文件: {}", previewData.getTempFile());
                        return; // 删除成功，直接返回
                    } catch (IOException e) {
                        LOGGER.warn("清理临时文件失败(尝试 {}/{}): {} - {}", 
                                    i + 1, maxRetries, previewData.getTempFile(), e.getMessage());
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
                            LOGGER.error("清理临时文件失败: {} - {}", previewData.getTempFile(), e.getMessage());
                        }
                    }
                }
            }
        }
    }

    private Map<Integer, String> inferDataTypes(Map<Integer, Object> firstRow) {
        Map<Integer, String> dataTypes = new HashMap<>();
        
        // 获取当前处理的fileId
        Optional<String> currentFileId = previewCache.keySet().stream().findFirst();
        if (!currentFileId.isPresent()) {
            LOGGER.warn("推断数据类型时未找到有效的预览数据缓存");
            return dataTypes;
        }
        
        PreviewData currentPreviewData = previewCache.get(currentFileId.get());
        if (currentPreviewData == null) {
            LOGGER.warn("未找到预览数据: {}", currentFileId.get());
            return dataTypes;
        }
        
        // 获取列名映射
        Map<Integer, String> headMap = new HashMap<>();
        List<String> headers = currentPreviewData.getHeaders();
        for (int i = 0; i < headers.size(); i++) {
            headMap.put(i, headers.get(i));
        }
        
        // 图片相关的关键词列表
        List<String> imageKeywords = Arrays.asList(
            "图片", "照片", "相片", "pic", "image", "photo", "picture", "logo", "icon", "头像", "img", "图像"
        );
        
        for (Map.Entry<Integer, Object> entry : firstRow.entrySet()) {
            int colIndex = entry.getKey();
            Object value = entry.getValue();
            
            // 首先检查列名是否包含图片关键词
            String columnName = headMap.get(colIndex);
            if (columnName != null) {
                String lowerColumnName = columnName.toLowerCase();
                boolean isImageColumn = imageKeywords.stream()
                    .anyMatch(keyword -> lowerColumnName.contains(keyword.toLowerCase()));
                
                if (isImageColumn) {
                    LOGGER.info("基于列名推断图片列: {} (索引: {}), 设置为MEDIUMBLOB类型", columnName, colIndex);
                    dataTypes.put(colIndex, "MEDIUMBLOB");
                    continue;
                }
            }
            
            // 如果列名不包含图片关键词，则根据值推断类型
            String dataType = inferDataType(value);
            dataTypes.put(colIndex, dataType);
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

    // 辅助方法：获取列索引
    private int getColumnIndex(List<String> headers, String header) {
        return headers.indexOf(header);
    }
    
    // 辅助方法：将图片数据转换为base64格式
    private String convertToBase64Image(byte[] imageData) {
        if (imageData == null || imageData.length == 0) {
            return "[IMAGE]";
        }
        
        try {
            // 检测图片类型
            String mimeType = detectMimeType(imageData);
            // 转换为base64
            String base64 = java.util.Base64.getEncoder().encodeToString(imageData);
            return "data:" + mimeType + ";base64," + base64;
        } catch (Exception e) {
            LOGGER.error("转换图片为base64时出错: {}", e.getMessage(), e);
            return "[IMAGE]";
        }
    }
    
    // 辅助方法：检测图片MIME类型
    private String detectMimeType(byte[] data) {
        if (data == null || data.length < 8) {
            return "image/png"; // 默认类型
        }
        
        // 检查文件头部特征
        if (data[0] == (byte)0xFF && data[1] == (byte)0xD8) {
            return "image/jpeg";
        } else if (data[0] == (byte)0x89 && data[1] == (byte)0x50 && data[2] == (byte)0x4E && data[3] == (byte)0x47) {
            return "image/png";
        } else if (data[0] == (byte)0x47 && data[1] == (byte)0x49 && data[2] == (byte)0x46) {
            return "image/gif";
        } else if (data[0] == (byte)0x42 && data[1] == (byte)0x4D) {
            return "image/bmp";
        }
        
        return "image/png"; // 默认类型
    }

} 