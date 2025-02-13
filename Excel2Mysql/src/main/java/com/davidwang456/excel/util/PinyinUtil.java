package com.davidwang456.excel.util;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PinyinUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(PinyinUtil.class);
    private static final HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();

    static {
        format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
    }

    public static String toPinyin(String chinese) {
        if (chinese == null || chinese.trim().isEmpty()) {
            return "";
        }

        StringBuilder pinyinBuilder = new StringBuilder();
        char[] chars = chinese.trim().toCharArray();

        try {
            for (char c : chars) {
                if (Character.toString(c).matches("[\\u4E00-\\u9FA5]+")) {
                    // 中文字符
                    String[] pinyinArray = PinyinHelper.toHanyuPinyinStringArray(c, format);
                    if (pinyinArray != null && pinyinArray.length > 0) {
                        pinyinBuilder.append(pinyinArray[0]);
                    }
                } else if (Character.toString(c).matches("[a-zA-Z0-9_]")) {
                    // 英文字母、数字和下划线保持不变
                    pinyinBuilder.append(Character.toLowerCase(c));
                } else {
                    // 其他字符转为下划线
                    pinyinBuilder.append('_');
                }
            }
        } catch (BadHanyuPinyinOutputFormatCombination e) {
            LOGGER.error("转换拼音失败: {}", chinese, e);
            return chinese.toLowerCase().replaceAll("[^a-z0-9_]", "_");
        }

        return pinyinBuilder.toString();
    }
} 