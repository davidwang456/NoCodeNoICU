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

@Api(tags = "Excel动态表管理")
@RestController
public class ExcelController {
    @Autowired
    private DynamicTableService dynamicTableService;

    @ApiOperation("动态创建表并导入数据")
    @PostMapping("/uploadDynamicFile")
    public String uploadDynamicFile(@RequestParam("file") MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String tableName = PinyinUtil.toPinyin(
            originalFilename.substring(0, originalFilename.lastIndexOf("."))
        );
        
        EasyExcel.read(
            file.getInputStream(), 
            new ExcelDataListener(tableName, dynamicTableService))
            .sheet()
            .doRead();
        
        return "表 " + tableName + " 创建并导入数据成功！";
    }
}
