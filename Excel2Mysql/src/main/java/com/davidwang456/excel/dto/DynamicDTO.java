package com.davidwang456.excel.dto;

import lombok.Data;
import java.util.LinkedHashMap;

/**
 * 动态数据传输对象
 * 用于处理动态字段的数据传输
 */
@Data
public class DynamicDTO {
    /**
     * 存储动态字段数据
     * key: 字段名
     * value: 字段值
     */
    private LinkedHashMap<String, String> data;
    
    /**
     * 获取指定字段的值
     * @param fieldName 字段名
     * @return 字段值
     */
    public String getFieldValue(String fieldName) {
        return data != null ? data.get(fieldName) : null;
    }
    
    /**
     * 设置字段值
     * @param fieldName 字段名
     * @param value 字段值
     */
    public void setFieldValue(String fieldName, String value) {
        if (data == null) {
            data = new LinkedHashMap<>();
        }
        data.put(fieldName, value);
    }
} 