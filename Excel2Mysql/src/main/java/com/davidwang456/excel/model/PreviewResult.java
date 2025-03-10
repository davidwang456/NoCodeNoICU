package com.davidwang456.excel.model;

import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.AllArgsConstructor;

/**
 * 预览结果
 */
@Data
@AllArgsConstructor
public class PreviewResult {
    /**
     * 表头
     */
    private List<String> headers;
    
    /**
     * 内容
     */
    private List<Map<String, Object>> content;
    
    /**
     * 总记录数
     */
    private int total;
    
    /**
     * 文件ID
     */
    private String fileId;
    
    /**
     * 表名
     */
    private String tableName;

	public List<String> getHeaders() {
		return headers;
	}

	public void setHeaders(List<String> headers) {
		this.headers = headers;
	}

	public List<Map<String, Object>> getContent() {
		return content;
	}

	public void setContent(List<Map<String, Object>> content) {
		this.content = content;
	}

	public int getTotal() {
		return total;
	}

	public void setTotal(int total) {
		this.total = total;
	}

	public String getFileId() {
		return fileId;
	}

	public void setFileId(String fileId) {
		this.fileId = fileId;
	}

	public String getTableName() {
		return tableName;
	}

	public void setTableName(String tableName) {
		this.tableName = tableName;
	}
} 