package com.davidwang456.excel.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import com.alibaba.excel.write.handler.SheetWriteHandler;
import com.alibaba.excel.write.metadata.holder.WriteSheetHolder;
import com.alibaba.excel.write.metadata.holder.WriteWorkbookHolder;

/**
 * 用于将Excel中的Base64图片字符串转换为实际图片
 */
public class ImageCellWriteHandler implements SheetWriteHandler {
    private static final Logger LOGGER = LogManager.getLogger(ImageCellWriteHandler.class);
    private List<List<Object>> data;
    private List<Integer> imageColumnIndexes;

    /**
     * 构造函数
     * @param data Excel数据
     */
    public ImageCellWriteHandler(List<List<Object>> data) {
        this.data = data;
        this.imageColumnIndexes = null;
    }
    
    /**
     * 构造函数
     * @param data Excel数据
     * @param imageColumnIndexes 图片列的索引列表
     */
    public ImageCellWriteHandler(List<List<Object>> data, List<Integer> imageColumnIndexes) {
        this.data = data;
        this.imageColumnIndexes = imageColumnIndexes;
        LOGGER.info("创建ImageCellWriteHandler，图片列索引: {}", imageColumnIndexes);
    }

    @Override
    public void beforeSheetCreate(WriteWorkbookHolder writeWorkbookHolder, WriteSheetHolder writeSheetHolder) {
        // 不需要操作
    }

    @Override
    public void afterSheetCreate(WriteWorkbookHolder writeWorkbookHolder, WriteSheetHolder writeSheetHolder) {
        Sheet sheet = writeSheetHolder.getSheet();
        Workbook workbook = writeWorkbookHolder.getWorkbook();
        Drawing<?> patriarch = sheet.createDrawingPatriarch();
        
        LOGGER.info("处理Excel中的图片，数据行数: {}", data.size());
        
        // 从第二行开始（跳过表头）
        for (int i = 1; i < data.size(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                LOGGER.warn("第 {} 行为空，创建新行", i);
                row = sheet.createRow(i);
            }
            
            List<Object> rowData = data.get(i);
            
            // 遍历每一列
            for (int j = 0; j < rowData.size(); j++) {
                // 如果指定了图片列索引，则只处理这些列
                if (imageColumnIndexes != null && !imageColumnIndexes.contains(j)) {
                    continue;
                }
                
                Object cellValue = rowData.get(j);
                
                if (cellValue instanceof String && isBase64Image((String) cellValue)) {
                    LOGGER.info("处理第 {} 行 第 {} 列的图片数据", i, j);
                    Cell cell = row.getCell(j);
                    if (cell == null) {
                        LOGGER.warn("第 {} 行 第 {} 列的单元格为空，创建新单元格", i, j);
                        cell = row.createCell(j);
                    }
                    
                    try {
                        // 在处理前就清空单元格内容
                        cell.setCellValue("");
                        
                        String base64 = extractBase64Data((String) cellValue);
                        if (base64 != null && !base64.isEmpty()) {
                            byte[] imageBytes = Base64.getDecoder().decode(base64);
                            
                            if (imageBytes != null && imageBytes.length > 0) {
                                // 确定图片类型
                                int pictureType = detectPictureType((String) cellValue);
                                LOGGER.info("图片类型: {}, 大小: {} 字节", pictureType, imageBytes.length);
                                
                                int pictureIdx = workbook.addPicture(imageBytes, pictureType);
                                
                                // 创建锚点并设置位置
                                ClientAnchor anchor = workbook.getCreationHelper().createClientAnchor();
                                anchor.setCol1(j);
                                anchor.setRow1(i);
                                anchor.setCol2(j + 1);
                                anchor.setRow2(i + 1);
                                
                                // 添加图片到绘图层
                                patriarch.createPicture(anchor, pictureIdx);
                                
                                // 再次清除单元格的文本内容，确保文本被删除
                                cell.setCellValue("");
                                
                                // 设置空的单元格样式，避免显示原有内容
                                CellStyle blankStyle = workbook.createCellStyle();
                                cell.setCellStyle(blankStyle);
                                
                                // 修改原始数据，确保Base64字符串不会被重新写入
                                rowData.set(j, "");
                                
                                LOGGER.info("成功添加图片到单元格 ({}, {})", i, j);
                            } else {
                                LOGGER.warn("图片数据解码后为空或长度为0");
                            }
                        } else {
                            LOGGER.warn("无法提取Base64数据");
                        }
                    } catch (Exception e) {
                        LOGGER.error("处理图片时出错: ", e);
                    }
                }
            }
        }
    }
    
    /**
     * 检查字符串是否为Base64编码的图片
     * @param value 要检查的字符串
     * @return 是否为Base64图片
     */
    private boolean isBase64Image(String value) {
        return value != null && value.startsWith("data:image/");
    }
    
    /**
     * 从Base64图片字符串中提取实际的Base64数据
     * @param base64Image Base64图片字符串
     * @return 实际的Base64数据
     */
    private String extractBase64Data(String base64Image) {
        if (base64Image == null) {
            LOGGER.warn("Base64图片字符串为null");
            return null;
        }
        
        if (!base64Image.startsWith("data:image/")) {
            LOGGER.warn("不是有效的Base64图片字符串: {}", base64Image.substring(0, Math.min(30, base64Image.length())));
            return null;
        }
        
        int commaIndex = base64Image.indexOf(",");
        if (commaIndex > 0) {
            String base64Data = base64Image.substring(commaIndex + 1);
            LOGGER.debug("成功提取Base64数据，长度: {}", base64Data.length());
            return base64Data;
        } else {
            LOGGER.warn("Base64图片字符串格式不正确，未找到逗号分隔符: {}", base64Image.substring(0, Math.min(30, base64Image.length())));
            return null;
        }
    }
    
    /**
     * 根据Base64图片字符串检测图片类型
     * @param base64Image Base64图片字符串
     * @return 图片类型常量
     */
    private int detectPictureType(String base64Image) {
        if (base64Image.contains("image/png")) {
            return Workbook.PICTURE_TYPE_PNG;
        } else if (base64Image.contains("image/jpeg") || base64Image.contains("image/jpg")) {
            return Workbook.PICTURE_TYPE_JPEG;
        } else {
            // 默认使用PNG
            return Workbook.PICTURE_TYPE_PNG;
        }
    }
} 