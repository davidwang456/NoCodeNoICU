package com.davidwang456.excel;

import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.alibaba.excel.EasyExcel;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import com.davidwang456.excel.util.PinyinUtil;
import com.davidwang456.excel.enums.DataSourceType;
import com.davidwang456.excel.service.MongoTableService;
import com.davidwang456.excel.service.ExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

@Api(tags = "Excel动态表管理")
@RestController
public class ExcelController {
    @Autowired
    private DynamicTableService dynamicTableService;
    
    @Autowired
    private MongoTableService mongoTableService;

    @Autowired
    private ExportService exportService;

    @ApiOperation("动态创建表并导入数据")
    @PostMapping("/uploadDynamicFile")
    public String uploadDynamicFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "dataSource", defaultValue = "MYSQL") DataSourceType dataSource) 
            throws IOException {
        
        String originalFilename = file.getOriginalFilename();
        String fileExtension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        String tableName = PinyinUtil.toPinyin(
            originalFilename.substring(0, originalFilename.lastIndexOf("."))
        );
        
        if ("csv".equals(fileExtension)) {
            CsvDataListener csvListener = new CsvDataListener(tableName, dynamicTableService, mongoTableService, dataSource);
            file.getInputStream().mark(0);
            csvListener.processData(file.getInputStream());
        } else {
            EasyExcel.read(
                file.getInputStream(), 
                new ExcelDataListener(tableName, dynamicTableService, mongoTableService, dataSource))
                .sheet()
                .doRead();
        }
        
        String message;
        switch (dataSource) {
            case MYSQL:
                message = "MySQL表 " + tableName + " 创建并导入数据成功！";
                break;
            case MONGODB:
                message = "MongoDB集合 " + tableName + " 创建并导入数据成功！";
                break;
            case BOTH:
                message = "MySQL表和MongoDB集合 " + tableName + " 创建并导入数据成功！";
                break;
            default:
                message = "数据导入成功！";
        }
        return message;
    }

    @ApiOperation("获取可导出的表名列表")
    @GetMapping("/tables")
    public List<String> getTableList(@RequestParam(value = "dataSource", defaultValue = "MYSQL") String dataSource) {
        return exportService.getTableList(dataSource);
    }

    @ApiOperation("导出数据到Excel")
    @GetMapping("/exportToExcel")
    public ResponseEntity<byte[]> exportToExcel(
            @RequestParam String tableName,
            @RequestParam(defaultValue = "MYSQL") String dataSource) throws IOException {
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        exportService.exportToExcel(tableName, dataSource, outputStream);

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
            @RequestParam(defaultValue = "MYSQL") String dataSource) throws IOException {
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        exportService.exportToCsv(tableName, dataSource, outputStream);

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
}
