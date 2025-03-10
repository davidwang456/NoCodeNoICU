package com.davidwang456.excel.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 */
@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    @ExceptionHandler(ExcelException.class)
    public ResponseEntity<Map<String, Object>> handleExcelException(ExcelException ex) {
        logger.error("Excel处理异常: code={}, table={}, message={}", 
                    ex.getErrorCode(), ex.getTableName(), ex.getMessage(), ex);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("errorCode", ex.getErrorCode());
        response.put("message", ex.getMessage());
        response.put("tableName", ex.getTableName());
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception ex) {
        logger.error("系统异常: ", ex);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("errorCode", "SYS001");
        response.put("message", "系统内部错误");
        
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
} 