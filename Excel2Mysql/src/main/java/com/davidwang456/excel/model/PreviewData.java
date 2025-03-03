package com.davidwang456.excel.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class PreviewData {
    private List<String> headers;
    private List<Map<String, Object>> data;
    private Path file;
    private String originalFileName;
    private Map<String, byte[]> imageMap; // 图片映射，键为"行号:列号"，值为图片字节数组

    public PreviewData(List<String> headers, List<Map<String, Object>> data, Path file, String originalFileName) {
        this.headers = headers;
        this.data = data;
        this.file = file;
        this.originalFileName = originalFileName;
    }
    
    public PreviewData(List<String> headers, List<Map<String, Object>> data, Path file, String originalFileName, Map<String, byte[]> imageMap) {
        this.headers = headers;
        this.data = data;
        this.file = file;
        this.originalFileName = originalFileName;
        this.imageMap = imageMap;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public List<Map<String, Object>> getData() {
        return data;
    }

    public Path getFile() {
        return file;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }
    
    public Map<String, byte[]> getImageMap() {
        return imageMap;
    }
} 