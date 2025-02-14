package com.davidwang456.excel.model;

public class ImportConfirmRequest {
    private String fileName;
    private String dataSource;

    public String getFileName() {
        return fileName != null ? fileName.trim() : null;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getDataSource() {
        return dataSource;
    }

    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String toString() {
        return "ImportConfirmRequest{" +
                "fileName='" + fileName + '\'' +
                ", dataSource='" + dataSource + '\'' +
                '}';
    }
} 