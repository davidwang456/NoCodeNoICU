package com.davidwang456.excel.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MysqlDataExporter implements DataExporter {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DynamicTableService dynamicTableService;

    @Override
    public List<Map<String, Object>> exportData(String tableName) {
        // 获取除system_id外的所有列名
        List<String> columns = getHeaders(tableName);
        String columnList = String.join(",", columns);
        String sql = "SELECT " + columnList + " FROM `" + tableName + "`";
        return jdbcTemplate.queryForList(sql);
    }

    @Override
    public List<String> getHeaders(String tableName) {
        String sql = "SHOW COLUMNS FROM `" + tableName + "`";
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(sql);
        return columns.stream()
                .map(column -> column.get("Field").toString())
                .filter(field -> !"system_id".equals(field))
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getTableList() {
        String sql = "SHOW TABLES";
        return jdbcTemplate.queryForList(sql, String.class);
    }

    @Override
    public List<String> getOrderedHeaders(String tableName) {
        List<String> orderedHeaders = dynamicTableService.getColumnOrder(tableName);
        return orderedHeaders != null ? orderedHeaders : getHeaders(tableName);
    }
} 