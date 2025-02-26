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
import javax.annotation.PostConstruct;

@Service
public class MysqlTableService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MysqlTableService.class);
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @PostConstruct
    public void init() {
        try {
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS table_metadata (" +
                "table_name VARCHAR(255) PRIMARY KEY," +
                "column_order TEXT," +
                "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );
        } catch (Exception e) {
            LOGGER.error("创建元数据表失败: {}", e.getMessage());
        }
    }

    @Transactional
    public void createTable(String tableName, Map<Integer, String> headMap, Map<Integer, String> dataTypeMap) {
        dropTableIfExists(tableName);
        
        StringBuilder createTableSql = new StringBuilder();
        createTableSql.append("CREATE TABLE `").append(tableName).append("` (");
        createTableSql.append("system_id BIGINT AUTO_INCREMENT PRIMARY KEY,");
        
        List<String> columnDefinitions = new ArrayList<>();
        List<String> columnOrder = new ArrayList<>();
        columnOrder.add("system_id"); // 添加system_id作为第一列

        headMap.forEach((index, columnName) -> {
            String formattedColumnName = formatColumnName(columnName);
            String dataType = dataTypeMap.getOrDefault(index, "VARCHAR(255)");
            columnDefinitions.add("`" + formattedColumnName + "` " + dataType);
            columnOrder.add(formattedColumnName);
        });
        
        createTableSql.append(String.join(",", columnDefinitions));
        createTableSql.append(")");

        LOGGER.info("创建表SQL: {}", createTableSql.toString());
        jdbcTemplate.execute(createTableSql.toString());
        
        // 保存列顺序
        saveColumnOrder(tableName, columnOrder);
        
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
        String formatted = PinyinUtil.toPinyin(columnName)
                .replaceAll("\\s+", "_")
                .replaceAll("[^a-z0-9_]", "_");
        
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
            
            if (orderJson != null) {
                List<String> columns = Arrays.asList(orderJson.split(","));
                // 确保 system_id 在列表中
                if (!columns.contains("system_id")) {
                    List<String> updatedColumns = new ArrayList<>();
                    updatedColumns.add("system_id");
                    updatedColumns.addAll(columns);
                    return updatedColumns;
                }
                return columns;
            }
            
            // 如果没有找到列顺序，从表结构中获取
            return getTableColumns(tableName);
        } catch (Exception e) {
            LOGGER.warn("获取表 {} 的列顺序失败: {}", tableName, e.getMessage());
            return getTableColumns(tableName);
        }
    }

    public void deleteData(String tableName, String id) {
        List<String> columnOrder = getColumnOrder(tableName);
        if (columnOrder == null || columnOrder.isEmpty()) {
            throw new RuntimeException("未找到表的列顺序信息");
        }

        String firstColumn = columnOrder.stream()
                .filter(col -> !"system_id".equals(col))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("未找到可用的查询列"));

        String sql = "DELETE FROM " + tableName + " WHERE " + firstColumn + " = ? LIMIT 1";
        jdbcTemplate.update(sql, id);
    }

    public void updateData(String tableName, String id, Map<String, Object> data) {
        List<String> columnOrder = getColumnOrder(tableName);
        if (columnOrder == null || columnOrder.isEmpty()) {
            LOGGER.warn("未找到表 {} 的列顺序信息，尝试重建列顺序", tableName);
            // 如果找不到列顺序，尝试从表结构中重建
            List<String> columns = getTableColumns(tableName);
            if (columns != null && !columns.isEmpty()) {
                saveColumnOrder(tableName, columns);
                columnOrder = columns;
            } else {
                throw new RuntimeException("未找到表的列顺序信息");
            }
        }

        StringBuilder sql = new StringBuilder("UPDATE `").append(tableName).append("` SET ");
        List<Object> params = new ArrayList<>();
        
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!"system_id".equals(entry.getKey())) {
                sql.append("`").append(entry.getKey()).append("` = ?, ");
                params.add(entry.getValue());
            }
        }
        
        sql.setLength(sql.length() - 2);
        sql.append(" WHERE system_id = ?");
        params.add(id);

        int updatedRows = jdbcTemplate.update(sql.toString(), params.toArray());
        if (updatedRows == 0) {
            throw new RuntimeException("未找到ID为 " + id + " 的记录");
        }
    }

    // 新增方法：从数据库获取表的列信息
    private List<String> getTableColumns(String tableName) {
        try {
            String sql = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? " +
                        "ORDER BY ORDINAL_POSITION";
            return jdbcTemplate.queryForList(sql, String.class, tableName);
        } catch (Exception e) {
            LOGGER.error("获取表列信息失败: {}", e.getMessage());
            throw new RuntimeException("获取表列信息失败", e);
        }
    }

    public Map<String, Object> getTableData(String tableName, List<String> columnOrder) {
        try {
            // 确保查询包含 system_id
            StringBuilder sqlBuilder = new StringBuilder("SELECT system_id, ");
            
            // 添加其他列
            String columns = columnOrder.stream()
                    .filter(col -> !"system_id".equals(col))  // 过滤掉可能重复的 system_id
                    .map(col -> "`" + col + "`")
                    .collect(Collectors.joining(", "));
            
            sqlBuilder.append(columns)
                     .append(" FROM `")
                     .append(tableName)
                     .append("`");
            
            String sql = sqlBuilder.toString();
            LOGGER.info("查询SQL: {}", sql);  // 添加日志
            
            List<Map<String, Object>> content = jdbcTemplate.queryForList(sql);
            
            // 确保 headers 包含 system_id
            List<String> headers = new ArrayList<>();
            headers.add("system_id");
            headers.addAll(columnOrder.stream()
                    .filter(col -> !"system_id".equals(col))
                    .collect(Collectors.toList()));
            
            Map<String, Object> result = new HashMap<>();
            result.put("headers", headers);
            result.put("content", content);
            result.put("total", content.size());
            
            return result;
        } catch (Exception e) {
            LOGGER.error("获取表数据失败: {}", e.getMessage());
            throw new RuntimeException("获取表数据失败", e);
        }
    }
}