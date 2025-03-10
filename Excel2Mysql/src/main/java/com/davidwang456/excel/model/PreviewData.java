package com.davidwang456.excel.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class PreviewData {
    private List<String> headers;
    private List<Map<String, Object>> data;
    private Path tempFile;
    private String originalFileName;
    private Map<String, byte[]> imageMap;

    public PreviewData(List<String> headers, List<Map<String, Object>> data, Path tempFile, String originalFileName, Map<String, byte[]> imageMap) {
        this.headers = headers;
        this.data = data;
        this.tempFile = tempFile;
        this.originalFileName = originalFileName;
        this.imageMap = imageMap;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public List<Map<String, Object>> getData() {
        return data;
    }

    public Path getTempFile() {
        return tempFile;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }
    
    public Map<String, byte[]> getImageMap() {
        return imageMap;
    }
} 