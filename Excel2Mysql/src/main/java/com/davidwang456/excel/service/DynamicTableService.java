package com.davidwang456.excel.service;
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
        createTableSql.append("CREATE TABLE `").append(tableName).append("` (");
        createTableSql.append("system_id BIGINT AUTO_INCREMENT PRIMARY KEY,");
        
        List<String> columnDefinitions = new ArrayList<>();
        headMap.forEach((index, columnName) -> {
            String formattedColumnName = formatColumnName(columnName);
            String dataType = dataTypeMap.getOrDefault(index, "VARCHAR(255)");
            columnDefinitions.add("`" + formattedColumnName + "` " + dataType);
        });
        
        createTableSql.append(String.join(",", columnDefinitions));
        createTableSql.append(")");

        LOGGER.info("创建表SQL: {}", createTableSql.toString());
        jdbcTemplate.execute(createTableSql.toString());
        LOGGER.info("表 {} 创建成功", tableName);
    }

    private void dropTableIfExists(String tableName) {
        String sql = "DROP TABLE IF EXISTS `" + tableName + "`";
        jdbcTemplate.execute(sql);
    }

    @Transactional
    public void batchInsertData(String tableName, Map<Integer, String> headMap, List<Map<Integer, String>> dataList) {
        List<String> columns = headMap.values().stream()
                .map(this::formatColumnName)
                .collect(Collectors.toList());

        String insertSql = generateInsertSql(tableName, columns);
        LOGGER.info("插入数据SQL: {}", insertSql);
        
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
        String formatted = PinyinUtil.toPinyin(columnName)
                .replaceAll("\\s+", "_")
                .replaceAll("[^a-z0-9_]", "_");
        
        // 确保列名不以数字开头
        if (formatted.matches("^\\d.*")) {
            formatted = "col_" + formatted;
        }
        return formatted;
    }

    private String generateInsertSql(String tableName, List<String> columns) {
        String columnList = columns.stream()
                .map(col -> "`" + col + "`")
                .collect(Collectors.joining(","));
        String valuePlaceholders = String.join(",", Collections.nCopies(columns.size(), "?"));
        return String.format("INSERT INTO `%s` (%s) VALUES (%s)", tableName, columnList, valuePlaceholders);
    }

    @Transactional
    public void saveColumnOrder(String tableName, List<String> columnOrder) {
        // 创建元数据表（如果不存在）
        jdbcTemplate.execute(
            "CREATE TABLE IF NOT EXISTS table_metadata (" +
            "table_name VARCHAR(255) PRIMARY KEY," +
            "column_order TEXT," +
            "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")"
        );
        
        // 保存列顺序
        String orderJson = String.join(",", columnOrder);
        jdbcTemplate.update(
            "INSERT INTO table_metadata (table_name, column_order) VALUES (?, ?) " +
            "ON DUPLICATE KEY UPDATE column_order = ?",
            tableName, orderJson, orderJson
        );
        LOGGER.info("表 {} 的列顺序已保存", tableName);
    }

    public List<String> getColumnOrder(String tableName) {
        try {
            String orderJson = jdbcTemplate.queryForObject(
                "SELECT column_order FROM table_metadata WHERE table_name = ?",
                String.class,
                tableName
            );
            return orderJson != null ? Arrays.asList(orderJson.split(",")) : null;
        } catch (Exception e) {
            LOGGER.warn("获取表 {} 的列顺序失败: {}", tableName, e.getMessage());
            return null;
        }
    }
} 