package com.davidwang456.excel.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.davidwang456.excel.dto.DataAssetStats;
import com.davidwang456.excel.service.DataAssetService;

@RestController
@RequestMapping("/api/stats")
public class DataAssetController {
    
    @Autowired
    private DataAssetService dataAssetService;
    
    @GetMapping("/mysql")
    public DataAssetStats getMySQLStats() {
        return dataAssetService.getMySQLStats();
    }
    
    @GetMapping("/mongodb")
    public DataAssetStats getMongoDBStats() {
        return dataAssetService.getMongoDBStats();
    }
} 