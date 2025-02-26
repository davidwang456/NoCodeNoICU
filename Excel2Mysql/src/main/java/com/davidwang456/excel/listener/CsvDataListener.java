package com.davidwang456.excel.listener;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davidwang456.excel.service.MysqlTableService;
import com.davidwang456.excel.service.MongoTableService;
import com.davidwang456.excel.enums.DataSourceType;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CsvDataListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(CsvDataListener.class);
    private static final int BATCH_COUNT = 100;
    private final String tableName;
    private final MysqlTableService mysqlService;
    private final MongoTableService mongoService;
    private final DataSourceType dataSource;
    private List<Map<Integer, String>> dataList = new ArrayList<>();
    private Map<Integer, String> headMap = new HashMap<>();
    private Map<Integer, String> dataTypeMap = new HashMap<>();

    public CsvDataListener(String tableName, MysqlTableService mysqlService, 
            MongoTableService mongoService, DataSourceType dataSource) {
        this.tableName = tableName;
        this.mysqlService = mysqlService;
        this.mongoService = mongoService;
        this.dataSource = dataSource;
    }

    public void processData(InputStream inputStream) throws IOException {
        // 将输入流转换为字节数组，以支持多次读取
        byte[] bytes = toByteArray(inputStream);
        
        // 第一次读取用于检测分隔符
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
            String firstLine = reader.readLine();
            char separator = detectDelimiter(firstLine);
            
            // 第二次读取用于处理数据
            try (BufferedReader csvReader = new BufferedReader(
                    new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
                
                // 配置CSV解析器
                CSVParser parser = new CSVParserBuilder()
                        .withSeparator(separator)
                        .build();
                
                try (CSVReader reader2 = new CSVReaderBuilder(csvReader)
                        .withCSVParser(parser)
                        .build()) {
                    
                    // 读取表头
                    String[] headers = reader2.readNext();
                    if (headers != null) {
                        for (int i = 0; i < headers.length; i++) {
                            headMap.put(i, headers[i]);
                        }
                    }

                    // 创建表或集合
                    if (dataSource == DataSourceType.MYSQL || dataSource == DataSourceType.BOTH) {
                        mysqlService.createTable(tableName, headMap, dataTypeMap);
                    }
                    if (dataSource == DataSourceType.MONGODB || dataSource == DataSourceType.BOTH) {
                        mongoService.createCollection(tableName);
                    }

                    // 读取数据
                    List<String[]> allRows = reader2.readAll();
                    boolean isFirstRow = true;
                    
                    for (String[] row : allRows) {
                        Map<Integer, String> rowData = new HashMap<>();
                        for (int i = 0; i < row.length; i++) {
                            String value = row[i];
                            rowData.put(i, value);
                            
                            // 分析第一行数据的类型
                            if (isFirstRow) {
                                String dataType = inferDataType(value);
                                dataTypeMap.put(i, dataType);
                            }
                        }
                        
                        if (isFirstRow) {
                            isFirstRow = false;
                        }

                        dataList.add(rowData);
                        if (dataList.size() >= BATCH_COUNT) {
                            saveData();
                            dataList.clear();
                        }
                    }
                    
                    // 保存剩余数据
                    if (!dataList.isEmpty()) {
                        saveData();
                    }
                } catch (CsvException e) {
                    LOGGER.error("CSV解析错误", e);
                    throw new IOException("CSV解析错误", e);
                }
            }
        }
    }

    private char detectDelimiter(String line) {
        if (line.contains("\t")) {
            return '\t';
        }
        return ',';
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

    private byte[] toByteArray(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = input.read(buffer)) != -1) {
            output.write(buffer, 0, n);
        }
        return output.toByteArray();
    }
} 