package com.davidwang456.excel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.davidwang456.excel.service.AuditLogService;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

@SpringBootApplication
@Configuration
public class Excel3MysqlApplication implements CommandLineRunner, WebMvcConfigurer {

    @Autowired
    private AuditLogService auditLogService;

    public static void main(String[] args) {
        // 设置默认编码为UTF-8
        System.setProperty("file.encoding", "UTF-8");
        // 确保JVM使用UTF-8编码
        Charset.defaultCharset();
        
        // 打印当前使用的编码，用于调试
        System.out.println("当前系统默认编码: " + Charset.defaultCharset());
        System.out.println("当前文件编码: " + System.getProperty("file.encoding"));
        
        SpringApplication.run(Excel3MysqlApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        auditLogService.init();
    }
    
    @Bean
    public HttpMessageConverter<String> responseBodyConverter() {
        return new StringHttpMessageConverter(StandardCharsets.UTF_8);
    }

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.add(responseBodyConverter());
    }
}
