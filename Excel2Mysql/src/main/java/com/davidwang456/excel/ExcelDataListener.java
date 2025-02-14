package com.davidwang456.excel;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import com.davidwang456.excel.service.MongoTableService;
import com.davidwang456.excel.enums.DataSourceType;
import com.davidwang456.excel.service.DynamicTableService;

public class ExcelDataListener extends AnalysisEventListener<Map<Integer, String>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExcelDataListener.class);
    private static final int BATCH_COUNT = 100;
    private List<Map<Integer, String>> dataList = new ArrayList<>();
    private Map<Integer, String> headMap = new HashMap<>();
    private Map<Integer, String> dataTypeMap = new HashMap<>(); // 用于存储每列的数据类型
    private final String tableName;
    private final DynamicTableService mysqlService;
    private final MongoTableService mongoService;
    private final DataSourceType dataSource;
    private boolean isFirstRow = true;
    private List<String> headers = new ArrayList<>();
    private List<String> orderedHeaders = new ArrayList<>(); // 新增：保存有序的列名

    public ExcelDataListener(String tableName, DynamicTableService mysqlService, 
            MongoTableService mongoService, DataSourceType dataSource) {
        this.tableName = tableName;
        this.mysqlService = mysqlService;
        this.mongoService = mongoService;
        this.dataSource = dataSource;
    }

    @Override
    public void invoke(Map<Integer, String> data, AnalysisContext context) {
        if (isFirstRow) {
            // 第一行数据用于判断数据类型
            analyzeDataTypes(data);
            isFirstRow = false;
        }
        dataList.add(data);
        if (dataList.size() >= BATCH_COUNT) {
            saveData();
            dataList.clear();
        }
    }

    private void analyzeDataTypes(Map<Integer, String> data) {
        for (Map.Entry<Integer, String> entry : data.entrySet()) {
            String value = entry.getValue();
            String dataType = inferDataType(value);
            dataTypeMap.put(entry.getKey(), dataType);
        }
    }

    private String inferDataType(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "VARCHAR(255)";
        }
        
        try {
            Integer.parseInt(value);
            return "INT";
        } catch (NumberFormatException e) {
            try {
                Double.parseDouble(value);
                return "DECIMAL(10,2)";
            } catch (NumberFormatException e2) {
                if (value.length() > 255) {
                    return "TEXT";
                }
                return "VARCHAR(255)";
            }
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        saveData();
        LOGGER.info("所有数据解析完成！");
    }

    @Override
    public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
        this.headMap = headMap;  // 保存表头映射
        // 保持Excel中的列顺序
        orderedHeaders = new ArrayList<>(headMap.values());
        
        // 先创建表/集合
        if (dataSource == DataSourceType.MYSQL || dataSource == DataSourceType.BOTH) {
            mysqlService.createTable(tableName, headMap, dataTypeMap);
            // 创建表后保存列顺序
            mysqlService.saveColumnOrder(tableName, orderedHeaders);
        }
        if (dataSource == DataSourceType.MONGODB || dataSource == DataSourceType.BOTH) {
            mongoService.createCollection(tableName);
            // 创建集合后保存列顺序
            mongoService.saveColumnOrder(tableName, orderedHeaders);
        }
    }

    private void saveData() {
        if (!dataList.isEmpty()) {
            if (dataSource == DataSourceType.MYSQL || dataSource == DataSourceType.BOTH) {
                mysqlService.batchInsertData(tableName, headMap, dataList);
            }
            if (dataSource == DataSourceType.MONGODB || dataSource == DataSourceType.BOTH) {
                mongoService.batchInsertData(tableName, headMap, dataList);
            }
        }
    }
} 