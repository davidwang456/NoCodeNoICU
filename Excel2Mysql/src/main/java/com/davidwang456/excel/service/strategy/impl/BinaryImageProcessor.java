package com.davidwang456.excel.service.strategy.impl;

import com.davidwang456.excel.service.strategy.ImageProcessor;
import com.davidwang456.excel.service.strategy.ImageProcessContext;
import com.davidwang456.excel.util.ImageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BinaryImageProcessor implements ImageProcessor {
    private static final Logger logger = LoggerFactory.getLogger(BinaryImageProcessor.class);

    @Override
    public String processImage(Object value, ImageProcessContext context) {
        try {
            byte[] imageBytes = (byte[]) value;
            if (imageBytes != null && imageBytes.length > 0) {
                // 检查是否已经是base64编码的图片数据
                String strValue = new String(imageBytes);
                if (strValue.startsWith("data:image/")) {
                    logger.info("检测到已编码的图片数据: 表={}, 列={}, 行={}, 长度={}字节",
                            context.getTableName(), context.getColumnName(), context.getRowNum(), strValue.length());
                    return strValue;
                }
                
                // 原始图片数据，需要转换为base64
                String base64Image = ImageUtil.bytesToBase64(imageBytes);
                if (base64Image != null) {
                    // 检测图片类型并添加适当的MIME类型前缀
                    String mimeType = ImageUtil.detectMimeType(imageBytes);
                    String result = "data:" + mimeType + ";base64," + base64Image;
                    logger.info("成功转换byte[]图片: 表={}, 列={}, 行={}, 类型={}, 大小={}字节, base64长度={}",
                            context.getTableName(), context.getColumnName(), context.getRowNum(),
                            mimeType, imageBytes.length, base64Image.length());
                    return result;
                }
            }
            logger.warn("byte[]图片为空或无效: 表={}, 列={}, 行={}",
                    context.getTableName(), context.getColumnName(), context.getRowNum());
        } catch (Exception e) {
            logger.error("处理byte[]图片时出错: 表={}, 列={}, 行={}, 错误={}",
                    context.getTableName(), context.getColumnName(), context.getRowNum(), e.getMessage(), e);
        }
        return null;
    }

    @Override
    public boolean canProcess(Object value) {
        return value instanceof byte[];
    }
} 