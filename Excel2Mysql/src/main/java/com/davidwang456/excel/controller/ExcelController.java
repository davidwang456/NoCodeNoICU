package com.davidwang456.excel.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.davidwang456.excel.model.CancelImportRequest;
import com.davidwang456.excel.model.ImportConfirmRequest;
import com.davidwang456.excel.model.PreviewResult;
import com.davidwang456.excel.service.ExportService;
import com.davidwang456.excel.service.PreviewService;
import com.davidwang456.excel.service.AuditLogService;
import javax.servlet.http.HttpSession;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@Api(tags = "Excel动态表管理")
@RestController
@RequestMapping("/api/excel")
public class ExcelController {
    @Autowired
    private ExportService exportService;

    @Autowired
    private PreviewService previewService;
    
    @Autowired
    private AuditLogService auditLogService;

    @ApiOperation("获取可导出的表名列表")
    @GetMapping("/tables")
    public List<String> getTableList(@RequestParam(value = "dataSource", defaultValue = "MYSQL") String dataSource) {
        return exportService.getTableList(dataSource);
    }

    @ApiOperation("导出数据到Excel")
    @GetMapping("/exportToExcel")
    public ResponseEntity<byte[]> exportToExcel(
            @RequestParam String tableName,
            @RequestParam(defaultValue = "MYSQL") String dataSource,
            HttpSession session) throws IOException {
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        exportService.exportToExcel(tableName, dataSource, outputStream);
        
        // 记录导出操作到审计日志
        String username = (String) session.getAttribute("user");
        String content = String.format("数据源: %s, 表名: %s, 格式: Excel", dataSource, tableName);
        auditLogService.logAudit(AuditLogService.ACTION_EXPORT, username, content);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        String filename = URLEncoder.encode(tableName + System.currentTimeMillis() + ".xlsx", "UTF-8");
        headers.setContentDispositionFormData("attachment", filename);

        return ResponseEntity.ok()
                .headers(headers)
                .body(outputStream.toByteArray());
    }

    @ApiOperation("导出数据到CSV")
    @GetMapping("/exportToCsv")
    public ResponseEntity<byte[]> exportToCsv(
            @RequestParam String tableName,
            @RequestParam(defaultValue = "MYSQL") String dataSource,
            HttpSession session) throws IOException {
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        exportService.exportToCsv(tableName, dataSource, outputStream);
        
        // 记录导出操作到审计日志
        String username = (String) session.getAttribute("user");
        String content = String.format("数据源: %s, 表名: %s, 格式: CSV", dataSource, tableName);
        auditLogService.logAudit(AuditLogService.ACTION_EXPORT, username, content);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        String filename = URLEncoder.encode(tableName + System.currentTimeMillis() + ".csv", "UTF-8");
        headers.setContentDispositionFormData("attachment", filename);

        return ResponseEntity.ok()
                .headers(headers)
                .body(outputStream.toByteArray());
    }

    @ApiOperation("分页查询数据")
    @GetMapping("/data")
    public Map<String, Object> getData(
            @RequestParam String tableName,
            @RequestParam(defaultValue = "MYSQL") String dataSource,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return exportService.getPageData(tableName, dataSource, page, size);
    }

    @PostMapping("/preview")
    public ResponseEntity<?> previewFile(@RequestParam("file") MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        
        String fileExtension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        
        // 保存文件到临时目录，但保留原始文件名信息
        Path tempFile = Files.createTempFile("preview_", "." + fileExtension);
        file.transferTo(tempFile.toFile());
        
        // 读取预览数据，传递原始文件名
        PreviewResult result = previewService.previewFile(tempFile, fileExtension, originalFilename);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("headers", result.getHeaders());
        response.put("content", result.getContent());
        response.put("total", result.getTotal());
        response.put("fileId", result.getFileId());
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/previewData")
    public Map<String, Object> getPreviewData(
            @RequestParam String fileName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return previewService.getPreviewData(fileName, page, size);
    }

    @PostMapping("/confirmImport")
    public ResponseEntity<?> confirmImport(@RequestBody ImportConfirmRequest request, HttpSession session) {
        System.out.println("Received import request: " + request);
        try {
            if ("BOTH".equals(request.getDataSource())) {
                // 同时导入到 MySQL 和 MongoDB，先获取预览数据的副本
                PreviewResult previewData = previewService.getPreviewResult(request.getFileName());
                if (previewData == null) {
                    throw new IllegalStateException("预览数据不存在，fileId: " + request.getFileName());
                }
                
                // 使用同一份预览数据进行两次导入
                previewService.importDataWithPreview(previewData, "MYSQL");
                previewService.importDataWithPreview(previewData, "MONGODB");
                
                // 记录导入操作到审计日志
                String username = (String) session.getAttribute("user");
                String content = String.format("数据源: BOTH, 文件名: %s", request.getFileName());
                auditLogService.logAudit(AuditLogService.ACTION_UPLOAD, username, content);
                
                // 最后清理预览数据
                previewService.cancelImport(request.getFileName());
            } else {
                previewService.importData(request.getFileName(), request.getDataSource());
                
                // 记录导入操作到审计日志
                String username = (String) session.getAttribute("user");
                String content = String.format("数据源: %s, 文件名: %s", request.getDataSource(), request.getFileName());
                auditLogService.logAudit(AuditLogService.ACTION_UPLOAD, username, content);
            }
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            System.err.println("Import failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", e.getMessage()));
        }
    }

    @PostMapping("/cancelImport")
    public ResponseEntity<?> cancelImport(
            @RequestBody CancelImportRequest request) {
        previewService.cancelImport(request.getFileName());
        return ResponseEntity.ok().build();
    }
}