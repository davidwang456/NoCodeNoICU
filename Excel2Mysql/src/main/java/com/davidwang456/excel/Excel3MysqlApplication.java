package com.davidwang456.excel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Autowired;
import com.davidwang456.excel.service.AuditLogService;

@SpringBootApplication
public class Excel3MysqlApplication implements CommandLineRunner {

    @Autowired
    private AuditLogService auditLogService;

    public static void main(String[] args) {
        SpringApplication.run(Excel3MysqlApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        auditLogService.init();
    }
}
