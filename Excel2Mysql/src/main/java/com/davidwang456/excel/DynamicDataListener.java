package com.davidwang456.excel;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.metadata.CellExtra;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

public class DynamicDataListener extends AnalysisEventListener<Map<Integer, String>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicDataListener.class);
    private static final int BATCH_COUNT = 100;
    private List<Map<Integer, String>> dataList = new ArrayList<>();
    private Map<Integer, String> headMap = new HashMap<>();
    private Map<Integer, String> dataTypeMap = new HashMap<>(); // 用于存储每列的数据类型
    private String tableName;
    private DynamicTableService tableService;
    private boolean isFirstRow = true;

    public DynamicDataListener(String tableName, DynamicTableService tableService) {
        this.tableName = tableName;
        this.tableService = tableService;
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
        this.headMap = headMap;
        // 根据表头创建表
        tableService.createTable(tableName, headMap, dataTypeMap);
    }

    private void saveData() {
        if (!dataList.isEmpty()) {
            tableService.batchInsertData(tableName, headMap, dataList);
        }
    }
} 