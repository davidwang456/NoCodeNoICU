package com.davidwang456.excel.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import com.davidwang456.excel.dto.DataAssetStats;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DataAssetService {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private MongoTemplate mongoTemplate;
    
    public DataAssetStats getMySQLStats() {
        // 查询所有用户创建的表（排除系统表）
        String sql = "SELECT TABLE_NAME FROM information_schema.TABLES " +
                    "WHERE TABLE_SCHEMA = (SELECT DATABASE()) " +
                    "AND TABLE_NAME NOT LIKE 'schema%' " +
                    "AND TABLE_NAME NOT IN ('flyway_schema_history', 'hibernate_sequence')";
        
        List<String> tables = jdbcTemplate.queryForList(sql, String.class);
        return new DataAssetStats(tables.size(), tables);
    }
    
    public DataAssetStats getMongoDBStats() {
        // 获取所有集合名称（排除系统集合）
        List<String> collections = mongoTemplate.getCollectionNames()
            .stream()
            .filter(name -> !name.startsWith("system."))
            .collect(Collectors.toList());
        
        return new DataAssetStats(collections.size(), collections);
    }
} 