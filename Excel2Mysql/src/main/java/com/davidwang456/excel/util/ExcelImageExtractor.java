package com.davidwang456.excel.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.POIXMLDocumentPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * Excel图片提取工具类
 */
public class ExcelImageExtractor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExcelImageExtractor.class);
    
    /**
     * 提取Excel文件中的图片
     * 
     * @param file Excel文件路径
     * @return 图片映射表，键为单元格位置（格式为"行:列"），值为图片数据
     */
    public static Map<String, byte[]> extractImages(Path file) throws IOException {
        Map<String, byte[]> imageMap = new HashMap<>();
        Workbook workbook = null;
        
        try {
            LOGGER.info("开始从Excel文件提取图片: {}", file.getFileName());
            workbook = WorkbookFactory.create(file.toFile());
            Sheet sheet = workbook.getSheetAt(0); // 只处理第一个工作表
            
            if (workbook instanceof XSSFWorkbook) {
                // 处理XLSX格式 - 使用更直接的方法获取图片
                extractXSSFImagesSafely((XSSFWorkbook) workbook, (XSSFSheet) sheet, imageMap);
            } else if (workbook instanceof HSSFWorkbook) {
                // 处理XLS格式
                extractHSSFImages((HSSFSheet) sheet, imageMap);
            } else {
                LOGGER.warn("不支持的Excel文件格式");
            }
            
            // 如果没有提取到图片，尝试使用备用方法
            if (imageMap.isEmpty()) {
                LOGGER.info("使用备用方法提取图片");
                if (workbook instanceof XSSFWorkbook) {
                    extractXSSFImagesAlternative((XSSFWorkbook) workbook, imageMap);
                } else if (workbook instanceof HSSFWorkbook) {
                    extractHSSFImagesAlternative((HSSFWorkbook) workbook, imageMap);
                }
            }
        } catch (Exception e) {
            LOGGER.error("提取Excel图片出错: {}", e.getMessage(), e);
            // 捕获错误但不抛出，返回空的图片映射
            return new HashMap<>();
        } finally {
            if (workbook != null) {
                try {
                    // 对于XSSFWorkbook，我们需要特殊处理，避免ClassCastException
                    if (workbook instanceof XSSFWorkbook) {
                        // 不调用close()方法，避免触发ZipPackage.closeImpl()中的保存操作
                        // 这可能会导致ClassCastException
                        LOGGER.info("跳过XSSFWorkbook的close()调用，避免ClassCastException");
                    } else {
                        // 对于其他类型的Workbook，正常关闭
                        workbook.close();
                    }
                } catch (IOException e) {
                    LOGGER.warn("关闭Workbook出错: {}", e.getMessage());
                }
            }
        }
        
        LOGGER.info("从Excel文件中提取了{}张图片", imageMap.size());
        return imageMap;
    }
    
    /**
     * 获取指定位置的图片
     * 
     * @param imageMap 图片映射表
     * @param row 行索引
     * @param col 列索引
     * @return 图片数据，如果不存在则返回null
     */
    public static byte[] getImageAt(Map<String, byte[]> imageMap, int row, int col) {
        if (imageMap == null || imageMap.isEmpty()) {
            return null;
        }
        
        // 1. 直接尝试精确匹配
        String key = row + ":" + col;
        if (imageMap.containsKey(key)) {
            return imageMap.get(key);
        }
        
        // 2. 查找最接近的图片
        // 定义最大偏差范围为2
        final int MAX_OFFSET = 2;
        
        for (int rowOffset = -MAX_OFFSET; rowOffset <= MAX_OFFSET; rowOffset++) {
            for (int colOffset = -MAX_OFFSET; colOffset <= MAX_OFFSET; colOffset++) {
                if (rowOffset == 0 && colOffset == 0) continue; // 跳过已经检查过的精确位置
                
                String nearbyKey = (row + rowOffset) + ":" + (col + colOffset);
                if (imageMap.containsKey(nearbyKey)) {
                    LOGGER.info("在附近位置找到图片: 原位置=[{},{}], 找到位置=[{},{}]", 
                              row, col, row + rowOffset, col + colOffset);
                    return imageMap.get(nearbyKey);
                }
            }
        }
        
        // 3. 如果只有一张图片，直接返回
        if (imageMap.size() == 1) {
            LOGGER.info("只有一张图片，直接返回");
            return imageMap.values().iterator().next();
        }
        
        return null;
    }
    
    /**
     * 使用更安全的方法提取XLSX格式工作表中的图片
     */
    private static void extractXSSFImagesSafely(XSSFWorkbook workbook, XSSFSheet sheet, Map<String, byte[]> imageMap) {
        try {
            LOGGER.info("使用安全方法提取XLSX图片");
            
            // 获取所有图片数据
            List<XSSFPictureData> allPictures = workbook.getAllPictures();
            if (allPictures.isEmpty()) {
                LOGGER.warn("XLSX文件中没有找到图片数据");
                return;
            }
            
            LOGGER.info("在XLSX文件中找到{}张图片", allPictures.size());
            
            // 直接从图片数据中提取，不使用drawing，避免ClassCastException
            if (!allPictures.isEmpty()) {
                // 将所有图片放在第一行的不同列
                int row = 1; // 第二行（跳过表头）
                for (int i = 0; i < allPictures.size(); i++) {
                    XSSFPictureData pictureData = allPictures.get(i);
                    byte[] data = pictureData.getData();
                    
                    // 从第一列开始放置图片
                    int col = i;
                    String key = row + ":" + col;
                    imageMap.put(key, data);
                    
                    LOGGER.info("放置XLSX图片到默认位置: 位置=[{},{}], 大小={}字节", row, col, data.length);
                }
                return; // 直接返回，不尝试使用drawing
            }
            
            // 以下代码在Java 11环境下可能会导致ClassCastException，仅作为备用方案
            try {
                // 获取所有图片形状并匹配它们的位置
                XSSFDrawing drawing = sheet.createDrawingPatriarch();
                List<XSSFShape> shapes = drawing.getShapes();
                
                for (XSSFShape shape : shapes) {
                    if (shape instanceof XSSFPicture) {
                        XSSFPicture picture = (XSSFPicture) shape;
                        XSSFClientAnchor anchor = (XSSFClientAnchor) picture.getAnchor();
                        
                        // 获取图片位置信息
                        int row = anchor.getRow1();
                        int col = anchor.getCol1();
                        
                        // 获取图片数据
                        XSSFPictureData pictureData = picture.getPictureData();
                        byte[] data = pictureData.getData();
                        
                        // 保存到映射表
                        String key = row + ":" + col;
                        imageMap.put(key, data);
                        
                        LOGGER.info("提取XLSX图片: 位置=[{},{}], 大小={}字节, 类型={}", 
                                   row, col, data.length, pictureData.getMimeType());
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("通过drawing提取图片失败，使用备用方法: {}", e.getMessage());
                // 如果drawing方法失败，使用备用方法
                if (imageMap.isEmpty() && !allPictures.isEmpty()) {
                    // 将所有图片放在第一行的不同列
                    int row = 1; // 第二行（跳过表头）
                    for (int i = 0; i < allPictures.size(); i++) {
                        XSSFPictureData pictureData = allPictures.get(i);
                        byte[] data = pictureData.getData();
                        
                        // 从第一列开始放置图片
                        int col = i;
                        String key = row + ":" + col;
                        imageMap.put(key, data);
                        
                        LOGGER.info("放置XLSX图片到默认位置: 位置=[{},{}], 大小={}字节", row, col, data.length);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("提取XLSX图片时出错: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 提取XLS格式工作表中的图片
     */
    private static void extractHSSFImages(HSSFSheet sheet, Map<String, byte[]> imageMap) {
        HSSFPatriarch patriarch = sheet.getDrawingPatriarch();
        if (patriarch == null) {
            LOGGER.warn("未找到XLS图片绘图对象");
            return;
        }
        
        List<HSSFShape> shapes = patriarch.getChildren();
        for (HSSFShape shape : shapes) {
            if (shape instanceof HSSFPicture) {
                HSSFPicture picture = (HSSFPicture) shape;
                HSSFClientAnchor anchor = (HSSFClientAnchor) picture.getAnchor();
                
                // 获取图片位置信息
                int row = anchor.getRow1();
                int col = anchor.getCol1();
                
                // 获取图片数据
                byte[] pictureData = picture.getPictureData().getData();
                LOGGER.info("提取XLS图片: 位置=[{},{}], 大小={}字节", row, col, pictureData.length);
                
                // 保存到映射表
                String key = row + ":" + col;
                imageMap.put(key, pictureData);
            }
        }
    }
    
    /**
     * 使用备用方法提取XLSX格式工作簿中的图片
     */
    private static void extractXSSFImagesAlternative(XSSFWorkbook workbook, Map<String, byte[]> imageMap) {
        try {
            List<XSSFPictureData> pictures = workbook.getAllPictures();
            if (pictures.isEmpty()) {
                LOGGER.info("工作簿中没有图片");
                return;
            }
            
            LOGGER.info("找到{}张图片", pictures.size());
            
            // 如果只有一张图片，放在第一个单元格
            if (pictures.size() == 1) {
                XSSFPictureData picture = pictures.get(0);
                byte[] data = picture.getData();
                imageMap.put("0:0", data);
                LOGGER.info("添加单张图片到位置[0,0], 大小={}字节", data.length);
                return;
            }
            
            // 多张图片，按顺序放置在不同列
            int index = 0;
            for (XSSFPictureData picture : pictures) {
                byte[] data = picture.getData();
                // 将图片放在第一行的不同列，确保每列有不同的图片
                int row = 0;
                int col = index;
                String key = row + ":" + col;
                
                // 确保不覆盖已有的图片
                if (!imageMap.containsKey(key)) {
                    imageMap.put(key, data);
                    LOGGER.info("添加图片到位置[{},{}], 大小={}字节, 图片索引={}", row, col, data.length, index);
                } else {
                    // 如果位置已被占用，尝试下一行
                    key = (row + 1) + ":" + col;
                    imageMap.put(key, data);
                    LOGGER.info("位置[{},{}]已被占用，添加图片到位置[{},{}], 大小={}字节, 图片索引={}", 
                              row, col, row + 1, col, data.length, index);
                }
                index++;
            }
            
            // 记录图片位置映射，便于调试
            LOGGER.info("图片位置映射:");
            for (String key : imageMap.keySet()) {
                LOGGER.info("位置: {}, 大小: {}字节", key, imageMap.get(key).length);
            }
        } catch (Exception e) {
            LOGGER.error("备用方法提取XLSX图片出错: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 使用备用方法提取XLS格式工作簿中的图片
     */
    private static void extractHSSFImagesAlternative(HSSFWorkbook workbook, Map<String, byte[]> imageMap) {
        try {
            List<HSSFPictureData> pictures = workbook.getAllPictures();
            if (pictures.isEmpty()) {
                LOGGER.info("工作簿中没有图片");
                return;
            }
            
            LOGGER.info("找到{}张图片", pictures.size());
            
            // 如果只有一张图片，放在第一个单元格
            if (pictures.size() == 1) {
                HSSFPictureData picture = pictures.get(0);
                byte[] data = picture.getData();
                imageMap.put("0:0", data);
                LOGGER.info("添加单张图片到位置[0,0], 大小={}字节", data.length);
                return;
            }
            
            // 多张图片，按顺序放置在不同列
            int index = 0;
            for (HSSFPictureData picture : pictures) {
                byte[] data = picture.getData();
                // 将图片放在第一行的不同列，确保每列有不同的图片
                int row = 0;
                int col = index;
                String key = row + ":" + col;
                
                // 确保不覆盖已有的图片
                if (!imageMap.containsKey(key)) {
                    imageMap.put(key, data);
                    LOGGER.info("添加图片到位置[{},{}], 大小={}字节, 图片索引={}", row, col, data.length, index);
                } else {
                    // 如果位置已被占用，尝试下一行
                    key = (row + 1) + ":" + col;
                    imageMap.put(key, data);
                    LOGGER.info("位置[{},{}]已被占用，添加图片到位置[{},{}], 大小={}字节, 图片索引={}", 
                              row, col, row + 1, col, data.length, index);
                }
                index++;
            }
            
            // 记录图片位置映射，便于调试
            LOGGER.info("图片位置映射:");
            for (String key : imageMap.keySet()) {
                LOGGER.info("位置: {}, 大小: {}字节", key, imageMap.get(key).length);
            }
        } catch (Exception e) {
            LOGGER.error("备用方法提取XLS图片出错: {}", e.getMessage(), e);
        }
    }
} 