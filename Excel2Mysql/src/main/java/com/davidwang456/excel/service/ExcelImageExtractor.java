package com.davidwang456.excel.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ooxml.POIXMLDocumentPart;
import org.apache.poi.hssf.usermodel.HSSFClientAnchor;
import org.apache.poi.hssf.usermodel.HSSFPatriarch;
import org.apache.poi.hssf.usermodel.HSSFPicture;
import org.apache.poi.hssf.usermodel.HSSFPictureData;
import org.apache.poi.hssf.usermodel.HSSFShape;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFPictureData;
import org.apache.poi.xssf.usermodel.XSSFShape;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExcelImageExtractor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExcelImageExtractor.class);

    public static Map<String, byte[]> extractImages(File file) throws IOException {
        Map<String, byte[]> imageMap = new HashMap<>();
        FileInputStream fis = null;
        Workbook workbook = null;
        
        try {
            fis = new FileInputStream(file);
            workbook = WorkbookFactory.create(fis);
            
            if (workbook instanceof XSSFWorkbook) {
                extractXSSFImages((XSSFWorkbook) workbook, imageMap);
            } else if (workbook instanceof HSSFWorkbook) {
                extractHSSFImages((HSSFWorkbook) workbook, imageMap);
            }
            
            return imageMap;
        } catch (Exception e) {
            LOGGER.error("提取图片时出错: {}", e.getMessage(), e);
            return imageMap;
        } finally {
            // 确保资源正确关闭
            if (workbook != null) {
                try {
                    workbook.close();
                } catch (IOException e) {
                    LOGGER.error("关闭工作簿时出错: {}", e.getMessage(), e);
                }
            }
            
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    LOGGER.error("关闭文件流时出错: {}", e.getMessage(), e);
                }
            }
            
            // 手动触发垃圾回收，帮助释放资源
            System.gc();
        }
    }
    
    private static void extractXSSFImages(XSSFWorkbook workbook, Map<String, byte[]> imageMap) {
        try {
            for (int sheetNum = 0; sheetNum < workbook.getNumberOfSheets(); sheetNum++) {
                XSSFSheet sheet = workbook.getSheetAt(sheetNum);
                
                // 获取所有图片
                List<XSSFPictureData> pictures = workbook.getAllPictures();
                if (pictures.isEmpty()) {
                    continue;
                }
                
                // 获取图片位置信息
                for (POIXMLDocumentPart part : sheet.getRelations()) {
                    if (part instanceof XSSFDrawing) {
                        XSSFDrawing drawing = (XSSFDrawing) part;
                        List<XSSFShape> shapes = drawing.getShapes();
                        
                        for (XSSFShape shape : shapes) {
                            if (shape instanceof XSSFPicture) {
                                XSSFPicture picture = (XSSFPicture) shape;
                                XSSFClientAnchor anchor = picture.getPreferredSize();
                                XSSFPictureData pictureData = picture.getPictureData();
                                
                                // 获取图片所在的行和列
                                int row = anchor.getRow1();
                                int col = anchor.getCol1();
                                
                                // 将图片数据存储到映射中
                                String key = row + ":" + col;
                                byte[] data = pictureData.getData();
                                
                                // 创建图片数据的副本，避免内存泄漏
                                byte[] dataCopy = new byte[data.length];
                                System.arraycopy(data, 0, dataCopy, 0, data.length);
                                
                                imageMap.put(key, dataCopy);
                                LOGGER.info("提取XLSX图片: 位置={}, 大小={}字节", key, dataCopy.length);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("提取XLSX图片时出错: {}", e.getMessage(), e);
        }
    }
    
    private static void extractHSSFImages(HSSFWorkbook workbook, Map<String, byte[]> imageMap) {
        try {
            for (int sheetNum = 0; sheetNum < workbook.getNumberOfSheets(); sheetNum++) {
                HSSFSheet sheet = workbook.getSheetAt(sheetNum);
                
                // 获取所有图片
                List<HSSFPictureData> pictures = workbook.getAllPictures();
                if (pictures.isEmpty()) {
                    continue;
                }
                
                // 获取图片位置信息
                HSSFPatriarch patriarch = sheet.getDrawingPatriarch();
                if (patriarch == null) {
                    continue;
                }
                
                for (HSSFShape shape : patriarch.getChildren()) {
                    if (shape instanceof HSSFPicture) {
                        HSSFPicture picture = (HSSFPicture) shape;
                        HSSFClientAnchor anchor = (HSSFClientAnchor) picture.getAnchor();
                        
                        // 获取图片所在的行和列
                        int row = anchor.getRow1();
                        int col = anchor.getCol1();
                        
                        // 获取图片数据
                        int pictureIndex = picture.getPictureIndex() - 1;
                        if (pictureIndex >= 0 && pictureIndex < pictures.size()) {
                            HSSFPictureData pictureData = pictures.get(pictureIndex);
                            
                            // 将图片数据存储到映射中
                            String key = row + ":" + col;
                            byte[] data = pictureData.getData();
                            
                            // 创建图片数据的副本，避免内存泄漏
                            byte[] dataCopy = new byte[data.length];
                            System.arraycopy(data, 0, dataCopy, 0, data.length);
                            
                            imageMap.put(key, dataCopy);
                            LOGGER.info("提取XLS图片: 位置={}, 大小={}字节", key, dataCopy.length);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("提取XLS图片时出错: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 根据行列索引获取图片数据
     * @param imageMap 图片映射
     * @param row 行索引
     * @param col 列索引
     * @return 图片数据字节数组
     */
    public static byte[] getImageAt(Map<String, byte[]> imageMap, int row, int col) {
        if (imageMap == null) {
            return null;
        }
        
        String key = row + ":" + col;
        return imageMap.get(key);
    }
} 