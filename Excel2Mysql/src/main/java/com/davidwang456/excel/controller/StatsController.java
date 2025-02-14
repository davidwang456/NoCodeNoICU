package com.davidwang456.excel.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mongodb.client.MongoCollection;

@RestController
@RequestMapping("/api/dashboard")
public class StatsController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MongoTemplate mongoTemplate;

    @GetMapping("/mysql-stats")
    public Map<String, Object> getMySQLStats() {
        String sql = "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = (SELECT DATABASE())";
        List<String> tables = jdbcTemplate.queryForList(sql, String.class);
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("count", tables.size());
        stats.put("tables", tables);
        return stats;
    }

    @GetMapping("/mongodb-stats")
    public Map<String, Object> getMongoDBStats() {
        List<String> collections = mongoTemplate.getCollectionNames()
            .stream()
            .filter(name -> !name.startsWith("system."))
            .collect(Collectors.toList());
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("count", collections.size());
        stats.put("tables", collections);
        return stats;
    }

    @GetMapping("/mysql-data/{table}")
    public Map<String, Object> getMySQLTableData(
            @PathVariable String table,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 获取总记录数
            String countSql = "SELECT COUNT(*) FROM " + table;
            int total = jdbcTemplate.queryForObject(countSql, Integer.class);
            
            // 获取列名（排除 system_id）
            String columnSql = "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = (SELECT DATABASE()) AND TABLE_NAME = ? AND COLUMN_NAME != 'system_id'";
            List<String> headers = jdbcTemplate.queryForList(columnSql, String.class, table);
            
            // 构建查询字段
            String fields = headers.stream().collect(Collectors.joining(", "));
            
            // 获取数据
            String dataSql = "SELECT " + fields + " FROM " + table + " LIMIT ? OFFSET ?";
            int offset = (page - 1) * size;
            List<Map<String, Object>> data = jdbcTemplate.queryForList(dataSql, size, offset);
            
            result.put("content", data);
            result.put("total", total);
            result.put("headers", headers);
            
        } catch (Exception e) {
            result.put("error", "获取MySQL数据失败: " + e.getMessage());
            result.put("content", new ArrayList<>());
            result.put("total", 0);
            result.put("headers", new ArrayList<>());
        }
        
        return result;
    }

    @GetMapping("/mongodb-data/{collection}")
    public Map<String, Object> getMongoDBCollectionData(
            @PathVariable String collection,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 获取集合
            MongoCollection<Document> mongoCollection = mongoTemplate.getCollection(collection);
            
            // 获取总记录数
            long total = mongoCollection.countDocuments();
            
            // 计算跳过的记录数
            int skip = (page - 1) * size;
            
            // 获取数据
            List<Map<String, Object>> data = new ArrayList<>();
            mongoCollection.find()
                .skip(skip)
                .limit(size)
                .forEach(doc -> {
                    Map<String, Object> row = new HashMap<>();
                    doc.forEach((key, value) -> {
                        if (!"_id".equals(key)) {  // 排除 MongoDB 的 _id 字段
                            row.put(key, value);
                        }
                    });
                    data.add(row);
                });
            
            // 获取字段名（表头）
            Set<String> headers = new HashSet<>();
            if (!data.isEmpty()) {
                // 从第一条记录中获取所有字段名
                headers = data.get(0).keySet();
            } else {
                // 如果没有数据，尝试从集合的第一条记录获取字段结构
                Document firstDoc = mongoCollection.find().first();
                if (firstDoc != null) {
                    headers = firstDoc.keySet().stream()
                        .filter(key -> !"_id".equals(key))
                        .collect(Collectors.toSet());
                }
            }
            
            result.put("content", data);
            result.put("total", total);
            result.put("headers", new ArrayList<>(headers));
            
        } catch (Exception e) {
            result.put("error", "获取MongoDB数据失败: " + e.getMessage());
            result.put("content", new ArrayList<>());
            result.put("total", 0);
            result.put("headers", new ArrayList<>());
        }
        
        return result;
    }
} 