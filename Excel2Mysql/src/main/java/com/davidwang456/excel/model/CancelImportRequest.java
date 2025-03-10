package com.davidwang456.excel.model;

import lombok.Data;

/**
 * 取消导入请求
 */
@Data
public class CancelImportRequest {
    /**
     * 文件名
     */
    private String fileName;

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}
} 