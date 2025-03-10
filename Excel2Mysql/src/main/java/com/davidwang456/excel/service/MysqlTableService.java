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
import java.util.Base64;

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

        // 图片相关的关键词列表
        List<String> imageKeywords = Arrays.asList(
            "图片", "照片", "相片", "pic", "image", "photo", "picture", "logo", "icon", "头像", "img", "图像"
        );

        headMap.forEach((index, columnName) -> {
            String formattedColumnName = formatColumnName(columnName);
            String dataType = dataTypeMap.getOrDefault(index, "VARCHAR(255)");
            
            // 检查是否为图片列 - 根据列类型和列名
            boolean isImageColumn = "MEDIUMBLOB".equals(dataType) 
                || "[IMAGE]".equals(dataTypeMap.get(index))
                || "[IMAGE_PLACEHOLDER]".equals(dataTypeMap.get(index));
            
            // 如果不是通过类型判断的图片列，尝试通过列名判断
            if (!isImageColumn) {
                String lowerColumnName = columnName.toLowerCase();
                for (String keyword : imageKeywords) {
                    if (lowerColumnName.contains(keyword.toLowerCase())) {
                        isImageColumn = true;
                        LOGGER.info("检测到基于名称的图片列: {}, 包含关键词: {}", columnName, keyword);
                        break;
                    }
                }
            }
            
            if (isImageColumn) {
                dataType = "MEDIUMBLOB";
                LOGGER.info("确认图片列: {}, 使用MEDIUMBLOB类型", formattedColumnName);
            }
            
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
    public void batchInsertData(String tableName, Map<Integer, String> headMap, List<Map<Integer, Object>> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            LOGGER.warn("没有数据需要插入到表 {}", tableName);
            return;
        }
        
        // 获取列顺序
        List<String> columnOrder = getColumnOrder(tableName);
        if (columnOrder == null || columnOrder.isEmpty()) {
            LOGGER.error("未找到表 {} 的列顺序信息", tableName);
            return;
        }
        
        // 移除system_id列，因为它是自动生成的
        columnOrder = columnOrder.stream()
            .filter(col -> !"system_id".equals(col))
            .collect(Collectors.toList());
        
        // 构建SQL语句
        StringBuilder insertSql = new StringBuilder();
        insertSql.append("INSERT INTO `").append(tableName).append("` (");
        
        // 添加列名
        List<String> columnNames = new ArrayList<>();
        for (String column : columnOrder) {
            columnNames.add("`" + column + "`");
        }
        insertSql.append(String.join(",", columnNames));
        insertSql.append(") VALUES ");
        
        // 添加参数占位符
        List<String> placeholders = new ArrayList<>();
        for (int i = 0; i < columnOrder.size(); i++) {
            placeholders.add("?");
        }
        String placeholderGroup = "(" + String.join(",", placeholders) + ")";
        
        // 批量插入，每批最多1000条
        int batchSize = 1000;
        int totalCount = dataList.size();
        int batchCount = (totalCount + batchSize - 1) / batchSize;
        
        LOGGER.info("开始批量插入数据到表 {}, 共{}条数据, 分{}批处理", tableName, totalCount, batchCount);
        
        for (int batch = 0; batch < batchCount; batch++) {
            int startIdx = batch * batchSize;
            int endIdx = Math.min(startIdx + batchSize, totalCount);
            List<Map<Integer, Object>> batchData = dataList.subList(startIdx, endIdx);
            
            // 构建当前批次的SQL
            StringBuilder batchSql = new StringBuilder(insertSql);
            List<String> valueSets = new ArrayList<>();
            for (int i = 0; i < batchData.size(); i++) {
                valueSets.add(placeholderGroup);
            }
            batchSql.append(String.join(",", valueSets));
            
            // 准备参数
            List<Object> params = new ArrayList<>();
            for (Map<Integer, Object> row : batchData) {
                // 按列顺序添加参数
                for (int i = 0; i < headMap.size(); i++) {
                    String columnName = formatColumnName(headMap.get(i));
                    if (columnOrder.contains(columnName)) {
                        Object value = row.get(i);
                        
                        // 处理图片标记
                        if (value instanceof String) {
                            String strValue = (String) value;
                            if ("[IMAGE]".equals(strValue) || "[IMAGE_PLACEHOLDER]".equals(strValue)) {
                                // 对于图片标记，如果没有实际图片数据，则插入null
                                value = null;
                            }
                        }
                        
                        params.add(value);
                    }
                }
            }
            
            // 执行批量插入
            try {
                jdbcTemplate.update(batchSql.toString(), params.toArray());
                LOGGER.info("成功插入第{}批数据到表 {}, {}条记录", batch + 1, tableName, batchData.size());
            } catch (Exception e) {
                LOGGER.error("插入数据到表 {} 失败: {}", tableName, e.getMessage());
                throw e;
            }
        }
        
        LOGGER.info("完成数据插入到表 {}, 共插入{}条记录", tableName, totalCount);
    }

    /**
     * 获取表的列类型信息
     */
    private Map<String, String> getColumnTypes(String tableName) {
        String sql = "SELECT COLUMN_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ?";
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(sql, tableName);
        
        Map<String, String> columnTypes = new HashMap<>();
        for (Map<String, Object> column : columns) {
            String columnName = (String) column.get("COLUMN_NAME");
            String dataType = (String) column.get("DATA_TYPE");
            columnTypes.put(columnName, dataType);
        }
        
        return columnTypes;
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
        
        // 获取表的列信息，用于检查图片列
        Map<String, String> columnTypes = getColumnDataTypes(tableName);
        
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String columnName = entry.getKey();
            Object value = entry.getValue();
            
            if (!"system_id".equals(columnName)) {
                // 检查是否为图片数据（Base64编码的图片）
                if (value instanceof String && ((String) value).startsWith("data:image/")) {
                    String dataType = columnTypes.get(columnName);
                    
                    // 如果列类型是BLOB、MEDIUMBLOB或LONGBLOB，则转换为字节数组
                    if (dataType != null && (dataType.contains("BLOB") || dataType.contains("BINARY"))) {
                        LOGGER.info("处理图片数据更新: 表={}, 列={}, 类型={}", tableName, columnName, dataType);
                        
                        try {
                            // 从Base64字符串提取图片数据
                            String base64String = (String) value;
                            int commaIndex = base64String.indexOf(",");
                            if (commaIndex > 0) {
                                String base64Data = base64String.substring(commaIndex + 1);
                                byte[] imageData = Base64.getDecoder().decode(base64Data);
                                
                                LOGGER.info("图片数据已解码，大小: {} 字节", imageData.length);
                                value = imageData;
                            } else {
                                LOGGER.warn("无效的Base64图片数据格式: {}", base64String.substring(0, Math.min(20, base64String.length())) + "...");
                            }
                        } catch (Exception e) {
                            LOGGER.error("处理Base64图片数据时出错: {}", e.getMessage(), e);
                        }
                    }
                }
                
                sql.append("`").append(columnName).append("` = ?, ");
                params.add(value);
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
    
    /**
     * 获取表的列数据类型信息
     * @param tableName 表名
     * @return 列名和数据类型的映射
     */
    private Map<String, String> getColumnDataTypes(String tableName) {
        try {
            String sql = "SELECT COLUMN_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, tableName);
            Map<String, String> columnTypes = new HashMap<>();
            
            for (Map<String, Object> row : results) {
                String columnName = (String) row.get("COLUMN_NAME");
                String dataType = (String) row.get("DATA_TYPE");
                columnTypes.put(columnName, dataType.toUpperCase());
            }
            
            return columnTypes;
        } catch (Exception e) {
            LOGGER.error("获取表 {} 的列数据类型信息失败: {}", tableName, e.getMessage(), e);
            return new HashMap<>();
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