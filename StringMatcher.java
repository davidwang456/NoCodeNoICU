package com.exchange.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 字符串匹配工具类
 */
public class StringMatcher {
    
    private static final Logger logger = LoggerFactory.getLogger(StringMatcher.class);
    
    /**
     * 判断长字符串是否包容短字符串
     * 短字符串A以-分割，最后一位是数字，例如POC-T1
     * 长字符串B包容A的条件：
     * 1. B包含A的完整内容
     * 2. 如果A的最后部分是数字，则B中对应位置后不能紧跟其他数字
     * 
     * 例如：
     * A="POC-T1", B="EPDD-POC-T1WE" => 返回true
     * A="POC-T1", B="EPDD-POC-T12WE" => 返回false
     * 
     * @param shortStr 短字符串A
     * @param longStr 长字符串B
     * @return 是否包容
     */
    public static boolean isContained(String shortStr, String longStr) {
        if (shortStr == null || longStr == null || shortStr.isEmpty() || longStr.isEmpty()) {
            return false;
        }
        
        // 检查短字符串是否符合要求（以-分割且最后一位是数字）
        if (!isValidShortString(shortStr)) {
            logger.warn("短字符串不符合要求: {}", shortStr);
            return false;
        }
        
        // 使用正则表达式匹配
        // 构建正则表达式：匹配短字符串，且如果短字符串最后是数字，则后面不能紧跟数字
        String lastChar = shortStr.substring(shortStr.length() - 1);
        String regex;
        
        if (Character.isDigit(lastChar.charAt(0))) {
            // 如果最后一位是数字，则后面不能紧跟数字
            regex = Pattern.quote(shortStr) + "(?![0-9])";
        } else {
            // 如果最后一位不是数字，则直接匹配
            regex = Pattern.quote(shortStr);
        }
        
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(longStr);
        
        return matcher.find();
    }
    
    /**
     * 检查短字符串是否符合要求（以-分割且最后一位是数字）
     * 
     * @param str 待检查的字符串
     * @return 是否符合要求
     */
    private static boolean isValidShortString(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        
        // 检查最后一位是否是数字
        char lastChar = str.charAt(str.length() - 1);
        if (!Character.isDigit(lastChar)) {
            return false;
        }
        
        // 检查是否包含'-'
        return str.contains("-");
    }
} 