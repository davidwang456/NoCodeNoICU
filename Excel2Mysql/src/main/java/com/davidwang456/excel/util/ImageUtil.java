package com.davidwang456.excel.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.SQLException;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

/**
 * 图片处理工具类
 */
public class ImageUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImageUtil.class);
    
    /**
     * 将Blob类型的图片转换为Base64编码字符串
     * @param blob 数据库中的Blob对象
     * @return Base64编码的字符串
     */
    public static String blobToBase64(Blob blob) {
        if (blob == null) {
            return null;
        }
        
        try (InputStream inputStream = blob.getBinaryStream()) {
            return inputStreamToBase64(inputStream);
        } catch (SQLException | IOException e) {
            LOGGER.error("将Blob转换为Base64时出错", e);
            return null;
        }
    }
    
    /**
     * 将字节数组转换为Base64编码字符串
     * @param bytes 图片字节数组
     * @return Base64编码的字符串
     */
    public static String bytesToBase64(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        return Base64.getEncoder().encodeToString(bytes);
    }
    
    /**
     * 将输入流转换为Base64编码字符串
     * @param inputStream 图片输入流
     * @return Base64编码的字符串
     * @throws IOException 如果读取输入流时发生错误
     */
    public static String inputStreamToBase64(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return null;
        }
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int bytesRead;
        
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        
        byte[] bytes = outputStream.toByteArray();
        return bytesToBase64(bytes);
    }
    
    /**
     * 判断一个对象是否为图片类型
     * @param obj 要检查的对象
     * @return 如果是图片类型则返回true，否则返回false
     */
    public static boolean isImage(Object obj) {
        return obj instanceof Blob || 
               (obj instanceof byte[] && ((byte[]) obj).length > 0) ||
               (obj instanceof String && ((String) obj).startsWith("data:image/"));
    }
    
    /**
     * 从MultipartFile获取图片的Base64编码
     * @param file 上传的文件
     * @return Base64编码的字符串
     * @throws IOException 如果读取文件时发生错误
     */
    public static String multipartFileToBase64(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }
        
        try (InputStream inputStream = file.getInputStream()) {
            return inputStreamToBase64(inputStream);
        }
    }
    
    /**
     * 获取图片的MIME类型
     * @param base64Image Base64编码的图片
     * @return 图片的MIME类型，如果无法确定则返回"image/png"
     */
    public static String getImageMimeType(String base64Image) {
        if (base64Image == null || base64Image.isEmpty()) {
            return "image/png";
        }
        
        if (base64Image.startsWith("data:image/")) {
            int endIndex = base64Image.indexOf(";");
            if (endIndex > 0) {
                return base64Image.substring(11, endIndex);
            }
        }
        
        // 默认返回PNG类型
        return "image/png";
    }
    
    /**
     * 检测图片字节数组的MIME类型
     * @param imageBytes 图片字节数组
     * @return 图片的MIME类型，如果无法确定则返回"image/png"
     */
    public static String detectMimeType(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length < 8) {
            return "image/png";
        }
        
        // 检查文件头部特征
        if (imageBytes[0] == (byte)0xFF && imageBytes[1] == (byte)0xD8) {
            return "image/jpeg";
        } else if (imageBytes[0] == (byte)0x89 && imageBytes[1] == (byte)0x50 && 
                   imageBytes[2] == (byte)0x4E && imageBytes[3] == (byte)0x47) {
            return "image/png";
        } else if (imageBytes[0] == (byte)0x47 && imageBytes[1] == (byte)0x49 && 
                   imageBytes[2] == (byte)0x46) {
            return "image/gif";
        } else if (imageBytes[0] == (byte)0x42 && imageBytes[1] == (byte)0x4D) {
            return "image/bmp";
        }
        
        // 默认返回PNG类型
        return "image/png";
    }
} 