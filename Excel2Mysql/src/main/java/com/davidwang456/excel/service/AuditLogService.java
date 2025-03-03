package com.davidwang456.excel.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class AuditLogService {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void init() {
        // 创建审计日志表
        jdbcTemplate.execute(
            "CREATE TABLE IF NOT EXISTS audit_log (" +
            "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
            "action VARCHAR(50) NOT NULL," +
            "operator VARCHAR(100) NOT NULL," +
            "operation_time DATETIME NOT NULL," +
            "content VARCHAR(1024)" +
            ")"
        );
    }

    public void logAudit(String action, String operator) {
        logAudit(action, operator, null);
    }

    public void logAudit(String action, String operator, String content) {
        if (content != null && content.length() > 1024) {
            content = content.substring(0, 1024);
        }
        String sql = "INSERT INTO audit_log (action, operator, operation_time, content) VALUES (?, ?, ?, ?)";
        jdbcTemplate.update(sql, action, operator, LocalDateTime.now(), content);
    }

    // 定义审计动作常量
    public static final String ACTION_LOGIN = "用户登录";
    public static final String ACTION_LOGOUT = "用户登出";
    public static final String ACTION_UPLOAD = "上传文件";
    public static final String ACTION_EXPORT = "导出文件";
}