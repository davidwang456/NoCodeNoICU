package com.davidwang456.excel.model;

import java.util.List;
import java.util.Map;

public class PreviewResult {
    private List<String> headers;
    private List<Map<String, Object>> content;
    private int total;
    private String fileId;
    private String tableName;

    public PreviewResult(List<String> headers, List<Map<String, Object>> content, int total, String fileId,String tableName) {
        this.headers = headers;
        this.content = content;
        this.total = total;
        this.fileId = fileId;
        this.setTableName(tableName);
    }

    public List<String> getHeaders() {
        return headers;
    }

    public List<Map<String, Object>> getContent() {
        return content;
    }

    public int getTotal() {
        return total;
    }

    public String getFileId() {
        return fileId;
    }

	public String getTableName() {
		return tableName;
	}

	public void setTableName(String tableName) {
		this.tableName = tableName;
	}
} 