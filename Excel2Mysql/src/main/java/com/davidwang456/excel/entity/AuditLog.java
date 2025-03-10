package com.davidwang456.excel.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AuditLog {
    private Long id;
    private String action;
    private String operator;
    private LocalDateTime operationTime;
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public String getAction() {
		return action;
	}
	public void setAction(String action) {
		this.action = action;
	}
	public String getOperator() {
		return operator;
	}
	public void setOperator(String operator) {
		this.operator = operator;
	}
	public LocalDateTime getOperationTime() {
		return operationTime;
	}
	public void setOperationTime(LocalDateTime operationTime) {
		this.operationTime = operationTime;
	}
}