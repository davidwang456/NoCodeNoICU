package com.davidwang456.excel.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    public boolean validateUser(String username, String password) {
       // String md5Password = DigestUtils.md5DigestAsHex(password.getBytes(StandardCharsets.UTF_8));
        String sql = "SELECT COUNT(1) FROM sys_user WHERE username = ? AND password = ? AND enabled = 1";
        int count = jdbcTemplate.queryForObject(sql, Integer.class, username, password);
        return count > 0;
    }
} 