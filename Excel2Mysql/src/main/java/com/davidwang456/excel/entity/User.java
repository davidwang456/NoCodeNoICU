package com.davidwang456.excel.entity;

import lombok.Data;
import java.util.Date;

@Data
public class User {
    private Long userId;
    private String username;
    private String password;  // MD5加密后的密码
    private Boolean enabled;  // 是否可用
    private Date createTime;
    private Date updateTime;
} 