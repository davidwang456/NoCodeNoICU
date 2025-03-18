package com.davidwang456.excel.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CharacterEncodingFilter;

/**
 * 由于Spring Boot已经自动配置了CharacterEncodingFilter，
 * 我们不需要再定义一个同名的Bean。
 * 这个类可以被删除，或者保留但不创建重复的Bean。
 */
@Configuration
public class CharacterEncodingConfig {

    // 注释掉这个Bean定义，因为Spring Boot已经自动配置了一个同名的Bean
    /*
    @Bean
    public FilterRegistrationBean<CharacterEncodingFilter> characterEncodingFilter() {
        FilterRegistrationBean<CharacterEncodingFilter> registrationBean = new FilterRegistrationBean<>();
        CharacterEncodingFilter characterEncodingFilter = new CharacterEncodingFilter();
        characterEncodingFilter.setEncoding("UTF-8");
        characterEncodingFilter.setForceEncoding(true);
        registrationBean.setFilter(characterEncodingFilter);
        registrationBean.addUrlPatterns("/*");
        return registrationBean;
    }
    */
} 