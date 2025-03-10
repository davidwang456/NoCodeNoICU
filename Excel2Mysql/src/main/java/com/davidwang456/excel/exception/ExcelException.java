package com.davidwang456.excel.exception;

/**
 * Excel处理相关异常的基类
 */
public class ExcelException extends RuntimeException {
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private final String errorCode;
    private final String tableName;
    
    public ExcelException(String message, String errorCode, String tableName) {
        super(message);
        this.errorCode = errorCode;
        this.tableName = tableName;
    }
    
    public ExcelException(String message, String errorCode, String tableName, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.tableName = tableName;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public String getTableName() {
        return tableName;
    }
} 