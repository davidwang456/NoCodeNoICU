package com.davidwang456.excel.service.preview;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class CsvPreviewReader implements PreviewReader {
    @Override
    public List<Map<String, Object>> readPreview(Path file) throws IOException {
        List<Map<String, Object>> result = new ArrayList<>();
        
        try (CSVReader reader = new CSVReaderBuilder(Files.newBufferedReader(file)).build()) {
            
            String[] headers;
			try {
				headers = reader.readNext();
	            if (headers == null) {
	                return result;
	            }
	            
	            String[] line;
	            while ((line = reader.readNext()) != null) {
	                Map<String, Object> row = new LinkedHashMap<>();
	                for (int i = 0; i < headers.length && i < line.length; i++) {
	                    row.put(headers[i], line[i]);
	                }
	                result.add(row);
	            }
			} catch (CsvValidationException | IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

        
        return result;
    }
    }
} 