package com.davidwang456.excel;

import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
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

@Api(tags = "Excel动态表管理")
@RestController
public class ExcelController {
    @Autowired
    private DynamicTableService dynamicTableService;
    
    @Autowired
    private MongoTableService mongoTableService;

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
}
