package com.davidwang456.excel.dto;

import java.util.List;

public class DataAssetStats {
    private int count;
    private List<String> tables;

    public DataAssetStats(int count, List<String> tables) {
        this.count = count;
        this.tables = tables;
    }

    public int getCount() {
        return count;
    }

    public List<String> getTables() {
        return tables;
    }
} 