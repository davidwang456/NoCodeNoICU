package com.davidwang456.excel.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.davidwang456.excel.dto.DataAssetStats;
import com.davidwang456.excel.service.DataAssetService;
import com.davidwang456.excel.service.MongoTableService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
public class DataAssetController {
    
    @Autowired
    private DataAssetService dataAssetService;
    
    @Autowired
    private MongoTableService mongoTableService;
    
    @GetMapping("/mysql")
    public DataAssetStats getMySQLStats() {
        return dataAssetService.getMySQLStats();
    }
    
    @GetMapping("/mongodb")
    public DataAssetStats getMongoDBStats() {
        return dataAssetService.getMongoDBStats();
    }

    @GetMapping("/mongodb-data/{collectionName}")
    public ResponseEntity<?> getMongoDBData(
            @PathVariable String collectionName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            // 获取保存的列顺序
            List<String> columnOrder = mongoTableService.getColumnOrder(collectionName);
            if (columnOrder == null || columnOrder.isEmpty()) {
                return ResponseEntity.badRequest().body("未找到表的列顺序信息");
            }

            // 使用列顺序获取数据
            Map<String, Object> result = mongoTableService.getDataWithOrder(collectionName, columnOrder, page, size);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("获取MongoDB数据失败: " + e.getMessage());
        }
    }
} 