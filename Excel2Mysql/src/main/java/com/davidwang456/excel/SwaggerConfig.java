package com.davidwang456.excel;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.oas.annotations.EnableOpenApi;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

/**
 * Swagger API 文档配置
 */
@Configuration
@EnableOpenApi
public class SwaggerConfig {
    
    @Bean
    public Docket defaultApi(){
        return new Docket(DocumentationType.OAS_30)
                .groupName("默认接口")
                .apiInfo(apiInfo())
                .enable(true)
                .select()
                .apis(RequestHandlerSelectors.basePackage("com.davidwang456.excel"))
                .paths(PathSelectors.any())
                .build();
    }
    
    @Bean
    public Docket excelApi() {
        return new Docket(DocumentationType.OAS_30)
                .groupName("Excel处理接口")
                .apiInfo(apiInfo())
                .enable(true)
                .select()
                .apis(RequestHandlerSelectors.basePackage("com.davidwang456.excel.controller"))
                .paths(PathSelectors.any())
                .build();
    }
    
    private ApiInfo apiInfo(){
        return new ApiInfoBuilder()
                .title("Excel2Mysql API文档")
                .description("Excel导入MySQL数据库工具接口文档")
                .contact(new Contact("davidwang456", "www.davidwang456.com", "davidwang456@sina.com"))
                .version("1.0.0")
                .build();
    }
}
