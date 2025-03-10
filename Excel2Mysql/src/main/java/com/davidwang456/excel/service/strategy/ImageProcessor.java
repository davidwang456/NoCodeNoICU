package com.davidwang456.excel.service.strategy;

/**
 * 图片处理策略接口
 */
public interface ImageProcessor {
    /**
     * 处理图片数据
     * @param value 原始图片数据
     * @param context 上下文信息
     * @return 处理后的图片数据（通常是base64编码的字符串）
     */
    String processImage(Object value, ImageProcessContext context);
    
    /**
     * 判断是否可以处理该类型的图片数据
     * @param value 待处理的数据
     * @return 是否可以处理
     */
    boolean canProcess(Object value);
}