# Excel2Mysql 数据管理系统

这是一个用于管理Excel数据的Web应用程序，支持Excel导入到MySQL/MongoDB，以及OCR图像识别功能。

## 功能特点

- Excel数据导入到MySQL/MongoDB
- 数据预览和确认
- 数据管理和编辑
- OCR图像识别（支持PDF和图片文件）
- 热重载开发模式

## 技术栈

- 后端：Spring Boot 2.5.1
- 前端：Vue.js + Element UI
- 数据库：MySQL + MongoDB
- OCR：Tesseract OCR + PDFBox

## 安装和配置

### 1. 安装依赖

确保您的系统已安装以下软件：

- JDK 1.8+
- Maven 3.6+
- MySQL 5.7+
- MongoDB 4.0+

### 2. 配置数据库

在 `application.properties` 文件中配置数据库连接：

```properties
# MySQL配置
spring.datasource.url=jdbc:mysql://localhost:3306/ww?characterEncoding=UTF-8&useSSL=false&useLegacyDatetimeCode=false&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=@Wangwei456

# MongoDB配置
spring.data.mongodb.uri=mongodb://localhost:27017/excel_data
```

### 3. 配置OCR

OCR功能需要Tesseract OCR引擎和语言数据文件：

1. 下载Tesseract语言数据文件：
   - 中文简体：https://github.com/tesseract-ocr/tessdata/raw/main/chi_sim.traineddata
   - 英文：https://github.com/tesseract-ocr/tessdata/raw/main/eng.traineddata

2. 将下载的文件放入项目根目录下的 `tessdata` 文件夹中。

## 运行应用

### 开发模式

```bash
mvn spring-boot:run
```

### 生产模式

```bash
mvn clean package
java -jar target/Excel2Mysql-3.8.0-SNAPSHOT.jar
```

## OCR功能使用说明

OCR功能支持从PDF和图片文件中识别文本，特别适合处理文件和试题。

### 使用步骤

1. 在左侧导航栏点击"图片导入数据"
2. 在上传文件标签页中：
   - 选择文件年份
   - 输入文件名称
   - 选择PDF或图片文件
   - 点击"开始识别"按钮
3. 系统会自动处理文件并识别其中的题目
4. 在识别结果标签页中：
   - 查看识别出的题目
   - 编辑或删除题目
   - 点击"保存到数据库"按钮保存结果
5. 在检索文件标签页中：
   - 查看已保存的文件
   - 查看文件详情
   - 删除文件

### 提高识别准确率的建议

1. 使用清晰的原始文件
2. PDF文件优于图片文件
3. 图片文件建议使用300DPI以上的分辨率
4. 黑白文档比彩色文档识别效果更好
5. 文字与背景对比度越高越好

## 许可证

MIT 