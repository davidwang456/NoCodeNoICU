package com.davidwang456.excel;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.stream.Collectors;
import com.davidwang456.excel.util.PinyinUtil;

@Service
public class DynamicTableService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicTableService.class);
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Transactional
    public void createTable(String tableName, Map<Integer, String> headMap, Map<Integer, String> dataTypeMap) {
        // 检查表是否已存在
        dropTableIfExists(tableName);
        
        // 构建建表SQL
        StringBuilder createTableSql = new StringBuilder();
        createTableSql.append("CREATE TABLE ").append(tableName).append(" (");
        createTableSql.append("system_id BIGINT AUTO_INCREMENT PRIMARY KEY,");
        
        List<String> columnDefinitions = new ArrayList<>();
        headMap.forEach((index, columnName) -> {
            String formattedColumnName = formatColumnName(columnName);
            String dataType = dataTypeMap.getOrDefault(index, "VARCHAR(255)");
            columnDefinitions.add(formattedColumnName + " " + dataType);
        });
        
        createTableSql.append(String.join(",", columnDefinitions));
        createTableSql.append(")");

        jdbcTemplate.execute(createTableSql.toString());
        LOGGER.info("表 {} 创建成功", tableName);
    }

    private void dropTableIfExists(String tableName) {
        String sql = "DROP TABLE IF EXISTS " + tableName;
        jdbcTemplate.execute(sql);
    }

    @Transactional
    public void batchInsertData(String tableName, Map<Integer, String> headMap, List<Map<Integer, String>> dataList) {
        List<String> columns = headMap.values().stream()
                .map(this::formatColumnName)
                .collect(Collectors.toList());

        String insertSql = generateInsertSql(tableName, columns);
        
        List<Object[]> batchArgs = new ArrayList<>();
        for (Map<Integer, String> data : dataList) {
            Object[] rowData = new Object[columns.size()];
            for (int i = 0; i < columns.size(); i++) {
                rowData[i] = data.get(i);
            }
            batchArgs.add(rowData);
        }

        jdbcTemplate.batchUpdate(insertSql, batchArgs);
    }

    private String formatColumnName(String columnName) {
        // 先转换为拼音，然后确保符合SQL命名规范
        return PinyinUtil.toPinyin(columnName)
                .replaceAll("\\s+", "_")
                .replaceAll("[^a-z0-9_]", "_");
    }

    private String generateInsertSql(String tableName, List<String> columns) {
        String columnList = String.join(",", columns);
        String valuePlaceholders = String.join(",", Collections.nCopies(columns.size(), "?"));
        return String.format("INSERT INTO %s (%s) VALUES (%s)", tableName, columnList, valuePlaceholders);
    }
} 