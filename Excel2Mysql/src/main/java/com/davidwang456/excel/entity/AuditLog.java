package com.davidwang456.excel.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AuditLog {
    private Long id;
    private String action;
    private String operator;
    private LocalDateTime operationTime;
}