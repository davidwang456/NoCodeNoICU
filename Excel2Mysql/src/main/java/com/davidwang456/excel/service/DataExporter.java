package com.davidwang456.excel.service;

import java.util.List;
import java.util.Map;

public interface DataExporter {
    List<Map<String, Object>> exportData(String tableName);
    List<String> getHeaders(String tableName);
    List<String> getOrderedHeaders(String tableName);
    List<String> getTableList();
} 