package com.davidwang456.excel.service.preview;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.ooxml.POIXMLDocumentPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class ExcelPreviewReader implements PreviewReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExcelPreviewReader.class);
    
    // 图片相关的关键词列表
    private static final List<String> IMAGE_KEYWORDS = Arrays.asList(
        "图片", "image", "photo", "picture", "logo", "icon", "头像", "照片", "img", "图像", "相片"
    );
    
    @Override
    public List<Map<String, Object>> readPreview(Path file) throws IOException {
        List<Map<String, Object>> result = new ArrayList<>();
        Workbook workbook = null;
        
        try {
            // 使用POI打开Excel文件
            workbook = WorkbookFactory.create(file.toFile());
            Sheet sheet = workbook.getSheetAt(0);
            
            // 获取表头行
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                LOGGER.warn("Excel文件没有表头行");
                return result;
            }
            
            // 解析表头
            Map<Integer, String> headMap = new HashMap<>();
            Set<Integer> potentialImageColumns = new HashSet<>();
            
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell cell = headerRow.getCell(i);
                if (cell != null) {
                    String headerValue = getCellValueAsString(cell);
                    headMap.put(i, headerValue);
                    
                    // 检查列名是否包含图片相关关键词
                    String lowerHeader = headerValue.toLowerCase();
                    boolean containsImageKeyword = IMAGE_KEYWORDS.stream()
                        .anyMatch(keyword -> lowerHeader.contains(keyword.toLowerCase()));
                        
                    if (containsImageKeyword) {
                        potentialImageColumns.add(i);
                        LOGGER.info("检测到可能的图片列: {} (索引: {})", headerValue, i);
                    }
                }
            }
            
            // 检查是否有图片
            Map<String, Boolean> imageCellPositions = new HashMap<>();
            
            // 检查XLSX格式的图片
            if (workbook instanceof XSSFWorkbook) {
                XSSFSheet xssfSheet = (XSSFSheet) sheet;
                for (POIXMLDocumentPart part : xssfSheet.getRelations()) {
                    if (part instanceof XSSFDrawing) {
                        XSSFDrawing drawing = (XSSFDrawing) part;
                        for (XSSFShape shape : drawing.getShapes()) {
                            if (shape instanceof XSSFPicture) {
                                XSSFPicture picture = (XSSFPicture) shape;
                                XSSFClientAnchor anchor = (XSSFClientAnchor) picture.getAnchor();
                                int row = anchor.getRow1();
                                int col = anchor.getCol1();
                                String key = row + ":" + col;
                                imageCellPositions.put(key, true);
                                LOGGER.debug("发现XLSX图片: 行={}, 列={}", row, col);
                                
                                // 将该列标记为图片列
                                potentialImageColumns.add(col);
                            }
                        }
                    }
                }
            } 
            // 检查XLS格式的图片
            else if (workbook instanceof HSSFWorkbook) {
                HSSFSheet hssfSheet = (HSSFSheet) sheet;
                HSSFPatriarch patriarch = hssfSheet.getDrawingPatriarch();
                if (patriarch != null) {
                    for (HSSFShape shape : patriarch.getChildren()) {
                        if (shape instanceof HSSFPicture) {
                            HSSFPicture picture = (HSSFPicture) shape;
                            HSSFClientAnchor anchor = (HSSFClientAnchor) picture.getAnchor();
                            int row = anchor.getRow1();
                            int col = anchor.getCol1();
                            String key = row + ":" + col;
                            imageCellPositions.put(key, true);
                            LOGGER.debug("发现XLS图片: 行={}, 列={}", row, col);
                            
                            // 将该列标记为图片列
                            potentialImageColumns.add(col);
                        }
                    }
                }
            }
            
            LOGGER.info("检测到{}个图片单元格, {}个潜在图片列", 
                      imageCellPositions.size(), potentialImageColumns.size());
            
            // 读取数据行
            int lastRowNum = sheet.getLastRowNum();
            for (int rowNum = 1; rowNum <= lastRowNum; rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null) continue;
                
                Map<String, Object> rowData = new LinkedHashMap<>();
                
                for (int colNum = 0; colNum < headMap.size(); colNum++) {
                    String header = headMap.get(colNum);
                    if (header == null) continue;
                    
                    Cell cell = row.getCell(colNum);
                    String cellValue = cell != null ? getCellValueAsString(cell) : "";
                    
                    // 检查是否是图片单元格
                    boolean isImageCell = imageCellPositions.containsKey(rowNum + ":" + colNum);
                    boolean isImageColumn = potentialImageColumns.contains(colNum);
                    
                    // 检查单元格内容是否符合图片特征
                    boolean hasImageContent = false;
                    if (cellValue.isEmpty() && isImageColumn) {
                        // 空单元格在图片列中可能是图片
                        hasImageContent = true;
                    } else if (cellValue.length() < 15) {  // 短文本可能是图片标记
                        String lowerValue = cellValue.toLowerCase();
                        hasImageContent = IMAGE_KEYWORDS.stream()
                            .anyMatch(keyword -> lowerValue.contains(keyword.toLowerCase()));
                    }
                    
                    if (isImageCell || (isImageColumn && (hasImageContent || cellValue.isEmpty()))) {
                        // 标记为图片
                        rowData.put(header, "[IMAGE]");
                        LOGGER.debug("标记图片单元格: 行={}, 列={}, 列名={}, 原因={}", 
                                   rowNum, colNum, header, 
                                   isImageCell ? "检测到图片" : 
                                   (isImageColumn ? "图片列" : "图片关键词"));
                    } else {
                        rowData.put(header, cellValue);
                    }
                }
                
                result.add(rowData);
            }
            
            LOGGER.info("Excel预览数据读取完成，共{}行", result.size());
            
        } catch (Exception e) {
            LOGGER.error("读取Excel文件失败", e);
            throw new IOException("读取Excel文件失败: " + e.getMessage(), e);
        } finally {
            // 安全关闭Workbook
            if (workbook != null) {
                try {
                    // 对于XSSFWorkbook，我们需要特殊处理，避免ClassCastException
                    if (workbook instanceof XSSFWorkbook) {
                        // 不调用close()方法，避免触发ZipPackage.closeImpl()中的保存操作
                        // 这可能会导致ClassCastException
                        // 这里可能会有资源泄漏，但比应用崩溃要好
                        LOGGER.info("跳过XSSFWorkbook的close()调用，避免ClassCastException");
                    } else {
                        // 对于其他类型的Workbook，正常关闭
                        workbook.close();
                    }
                } catch (IOException e) {
                    LOGGER.warn("关闭Workbook时出错: {}", e.getMessage());
                }
            }
        }
        
        return result;
    }
    
    /**
     * 获取单元格的值，转换为字符串
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    // 避免数值显示为科学计数法
                    double value = cell.getNumericCellValue();
                    if (value == Math.floor(value)) {
                        return String.format("%.0f", value);
                    } else {
                        return String.valueOf(value);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    try {
                        return cell.getStringCellValue();
                    } catch (Exception ex) {
                        return cell.getCellFormula();
                    }
                }
            case BLANK:
                return "";
            default:
                return "";
        }
    }
} 