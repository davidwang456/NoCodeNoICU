package com.davidwang456.excel.service.strategy.impl;

import com.davidwang456.excel.service.strategy.ImageProcessor;
import com.davidwang456.excel.service.strategy.ImageProcessContext;
import com.davidwang456.excel.util.ImageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.sql.Blob;

@Component
public class BlobImageProcessor implements ImageProcessor {
    private static final Logger logger = LoggerFactory.getLogger(BlobImageProcessor.class);

    @Override
    public String processImage(Object value, ImageProcessContext context) {
        try {
            String base64Image = ImageUtil.blobToBase64((Blob) value);
            if (base64Image != null) {
                logger.info("成功转换Blob图片: 表={}, 列={}, 行={}, 大小={}字节",
                        context.getTableName(), context.getColumnName(), context.getRowNum(), base64Image.length());
                return "data:image/png;base64," + base64Image;
            }
            logger.warn("Blob图片转换为null: 表={}, 列={}, 行={}",
                    context.getTableName(), context.getColumnName(), context.getRowNum());
        } catch (Exception e) {
            logger.error("处理Blob图片时出错: 表={}, 列={}, 行={}, 错误={}",
                    context.getTableName(), context.getColumnName(), context.getRowNum(), e.getMessage(), e);
        }
        return null;
    }

    @Override
    public boolean canProcess(Object value) {
        return value instanceof Blob;
    }
} 