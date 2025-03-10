package com.davidwang456.excel.service.strategy;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * 图片处理器管理类
 */
@Component
public class ImageProcessorManager {
    private final List<ImageProcessor> processors;

    public ImageProcessorManager(List<ImageProcessor> processors) {
        this.processors = processors;
    }

    /**
     * 处理图片数据
     * @param value 原始图片数据
     * @param tableName 表名
     * @param columnName 列名
     * @param rowNum 行号
     * @return 处理后的图片数据
     */
    public String processImage(Object value, String tableName, String columnName, int rowNum) {
        if (value == null) {
            return null;
        }

        ImageProcessContext context = new ImageProcessContext(tableName, columnName, rowNum);
        
        for (ImageProcessor processor : processors) {
            if (processor.canProcess(value)) {
                return processor.processImage(value, context);
            }
        }
        
        return null;
    }
} 