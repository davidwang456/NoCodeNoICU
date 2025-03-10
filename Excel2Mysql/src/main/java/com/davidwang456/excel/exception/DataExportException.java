package com.davidwang456.excel.exception;

/**
 * 数据导出相关异常
 */
public class DataExportException extends ExcelException {
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public static final String ERROR_CODE_EXPORT_FAILED = "E001";
    public static final String ERROR_CODE_TABLE_NOT_FOUND = "E002";
    public static final String ERROR_CODE_COLUMN_NOT_FOUND = "E003";
    public static final String ERROR_CODE_IMAGE_PROCESSING = "E004";
    
    public DataExportException(String message, String errorCode, String tableName) {
        super(message, errorCode, tableName);
    }
    
    public DataExportException(String message, String errorCode, String tableName, Throwable cause) {
        super(message, errorCode, tableName, cause);
    }
    
    public static DataExportException exportFailed(String tableName, String reason) {
        return new DataExportException(
            String.format("导出表 %s 数据失败: %s", tableName, reason),
            ERROR_CODE_EXPORT_FAILED,
            tableName
        );
    }
    
    public static DataExportException tableNotFound(String tableName) {
        return new DataExportException(
            String.format("表 %s 不存在", tableName),
            ERROR_CODE_TABLE_NOT_FOUND,
            tableName
        );
    }
    
    public static DataExportException columnNotFound(String tableName, String columnName) {
        return new DataExportException(
            String.format("表 %s 中未找到列 %s", tableName, columnName),
            ERROR_CODE_COLUMN_NOT_FOUND,
            tableName
        );
    }
    
    public static DataExportException imageProcessingError(String tableName, String columnName, String reason) {
        return new DataExportException(
            String.format("处理表 %s 中的图片列 %s 失败: %s", tableName, columnName, reason),
            ERROR_CODE_IMAGE_PROCESSING,
            tableName
        );
    }
} 