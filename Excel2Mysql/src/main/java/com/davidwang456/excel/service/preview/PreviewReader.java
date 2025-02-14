package com.davidwang456.excel.service.preview;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface PreviewReader {
    List<Map<String, Object>> readPreview(Path file) throws IOException;
} 