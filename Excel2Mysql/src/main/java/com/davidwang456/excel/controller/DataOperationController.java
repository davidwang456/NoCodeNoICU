package com.davidwang456.excel.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.davidwang456.excel.service.DynamicTableService;
import com.davidwang456.excel.service.MongoTableService;

import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DataOperationController {

    @Autowired
    private DynamicTableService dynamicTableService;

    @Autowired
    private MongoTableService mongoTableService;

    @DeleteMapping("/del/{dataSource}/{tableName}/{id}")
    public ResponseEntity<?> deleteData(
            @PathVariable String dataSource,
            @PathVariable String tableName,
            @PathVariable String id) {
        try {
            if ("mysql".equalsIgnoreCase(dataSource)) {
                dynamicTableService.deleteData(tableName, id);
            } else if ("mongodb".equalsIgnoreCase(dataSource)) {
                mongoTableService.deleteData(tableName, id);
            } else {
                return ResponseEntity.badRequest().body("不支持的数据源类型");
            }
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("删除失败: " + e.getMessage());
        }
    }

    @PutMapping("/upd/{dataSource}/{tableName}/{id}")
    public ResponseEntity<?> updateData(
            @PathVariable String dataSource,
            @PathVariable String tableName,
            @PathVariable String id,
            @RequestBody Map<String, Object> data) {
        try {
            if ("mysql".equalsIgnoreCase(dataSource)) {
                dynamicTableService.updateData(tableName, id, data);
            } else if ("mongodb".equalsIgnoreCase(dataSource)) {
                mongoTableService.updateData(tableName, id, data);
            } else {
                return ResponseEntity.badRequest().body("不支持的数据源类型");
            }
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("更新失败: " + e.getMessage());
        }
    }
} 