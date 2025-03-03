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
     * 从Excel文件中提取图片
     * @param filePath Excel文件路径
     * @return 图片映射，键为"行号:列号"，值为图片字节数组
     */
    public static Map<String, byte[]> extractImages(Path filePath) {
        Map<String, byte[]> imageMap = new HashMap<>();
        Workbook workbook = null;
        
        try {
            // 打开工作簿
            workbook = WorkbookFactory.create(filePath.toFile());
            Sheet sheet = workbook.getSheetAt(0);
            
            if (workbook instanceof XSSFWorkbook) {
                extractXSSFImages((XSSFWorkbook) workbook, (XSSFSheet) sheet, imageMap);
            } else if (workbook instanceof HSSFWorkbook) {
                extractHSSFImages((HSSFWorkbook) workbook, (HSSFSheet) sheet, imageMap);
            }
            
            // 不在这里关闭workbook，而是在finally块中处理
        } catch (Exception e) {
            LOGGER.error("提取Excel图片时出错", e);
        } finally {
            // 安全关闭Workbook
            if (workbook != null) {
                try {
                    // 对于XSSFWorkbook，我们需要特殊处理
                    if (workbook instanceof XSSFWorkbook) {
                        // 不调用close()方法，避免触发ZipPackage.closeImpl()中的保存操作
                        // 这可能会导致ClassCastException
                        // 这里可能会有资源泄漏，但比应用崩溃要好
                        LOGGER.info("跳过XSSFWorkbook的close()调用，避免ClassCastException");
                    } else {
                        // 对于其他类型的Workbook，正常关闭
                        workbook.close();
                    }
                } catch (Exception e) {
                    LOGGER.warn("关闭Workbook时出错: {}", e.getMessage());
                }
            }
        }
        
        return imageMap;
    }
    
    /**
     * 从XLSX文件中提取图片
     */
    private static void extractXSSFImages(XSSFWorkbook workbook, XSSFSheet sheet, Map<String, byte[]> imageMap) {
        try {
            for (POIXMLDocumentPart part : sheet.getRelations()) {
                if (part instanceof XSSFDrawing) {
                    XSSFDrawing drawing = (XSSFDrawing) part;
                    List<XSSFShape> shapes = drawing.getShapes();
                    
                    for (XSSFShape shape : shapes) {
                        if (shape instanceof XSSFPicture) {
                            XSSFPicture picture = (XSSFPicture) shape;
                            XSSFClientAnchor anchor = (XSSFClientAnchor) picture.getAnchor();
                            
                            // 获取图片所在的行和列
                            int row = anchor.getRow1();
                            int col = anchor.getCol1();
                            
                            // 获取图片数据
                            byte[] pictureData = picture.getPictureData().getData();
                            
                            // 存储图片数据，键为"行号:列号"
                            String key = row + ":" + col;
                            imageMap.put(key, pictureData);
                            
                            LOGGER.info("从XLSX提取图片: 位置={}, 大小={}字节", key, pictureData.length);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("提取XLSX图片时出错", e);
        }
    }
    
    /**
     * 从XLS文件中提取图片
     */
    private static void extractHSSFImages(HSSFWorkbook workbook, HSSFSheet sheet, Map<String, byte[]> imageMap) {
        try {
            HSSFPatriarch patriarch = sheet.getDrawingPatriarch();
            if (patriarch != null) {
                for (HSSFShape shape : patriarch.getChildren()) {
                    if (shape instanceof HSSFPicture) {
                        HSSFPicture picture = (HSSFPicture) shape;
                        HSSFClientAnchor anchor = (HSSFClientAnchor) picture.getAnchor();
                        
                        // 获取图片所在的行和列
                        int row = anchor.getRow1();
                        int col = anchor.getCol1();
                        
                        // 获取图片数据
                        byte[] pictureData = picture.getPictureData().getData();
                        
                        // 存储图片数据，键为"行号:列号"
                        String key = row + ":" + col;
                        imageMap.put(key, pictureData);
                        
                        LOGGER.info("从XLS提取图片: 位置={}, 大小={}字节", key, pictureData.length);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("提取XLS图片时出错", e);
        }
    }
    
    /**
     * 获取指定位置的图片
     * @param imageMap 图片映射
     * @param row 行号
     * @param col 列号
     * @return 图片字节数组，如果没有找到则返回null
     */
    public static byte[] getImageAt(Map<String, byte[]> imageMap, int row, int col) {
        String key = row + ":" + col;
        return imageMap.get(key);
    }
} 