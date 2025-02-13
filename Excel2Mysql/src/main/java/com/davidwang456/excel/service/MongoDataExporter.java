package com.davidwang456.excel.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MongoDataExporter implements DataExporter {
    
    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public List<Map<String, Object>> exportData(String tableName) {
        List<Map> rawData = mongoTemplate.findAll(Map.class, tableName);
        return rawData.stream()
                .map(this::removeSystemFields)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getHeaders(String tableName) {
        List<Map<String, Object>> data = exportData(tableName);
        Set<String> headers = new LinkedHashSet<>();
        for (Map<String, Object> row : data) {
            headers.addAll(row.keySet());
        }
        return new ArrayList<>(headers);
    }

    @Override
    public List<String> getTableList() {
        return mongoTemplate.getCollectionNames().stream()
                .filter(name -> !name.startsWith("system."))
                .collect(Collectors.toList());
    }

    private Map<String, Object> removeSystemFields(Map<?, ?> document) {
        Map<String, Object> result = new HashMap<>();
        document.forEach((key, value) -> {
            if (key instanceof String && !key.equals("_id")) {
                result.put((String) key, value);
            }
        });
        return result;
    }
} 