package com.davidwang456.excel.service.preview;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class ExcelPreviewReader implements PreviewReader {
    @Override
    public List<Map<String, Object>> readPreview(Path file) throws IOException {
        List<Map<String, Object>> result = new ArrayList<>();
        
        EasyExcel.read(file.toFile(), new AnalysisEventListener<Map<Integer, String>>() {
            private Map<Integer, String> headMap;
            
            @Override
            public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
                this.headMap = headMap;
            }
            
            @Override
            public void invoke(Map<Integer, String> data, AnalysisContext context) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (Map.Entry<Integer, String> entry : headMap.entrySet()) {
                    row.put(entry.getValue(), data.get(entry.getKey()));
                }
                result.add(row);
            }
            
            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
            }
        }).sheet().doRead();
        
        return result;
    }
} 