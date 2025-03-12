// 导入页面组件
const ImportPage = {
    template: '#import-template',
    data() {
        return {
            uploadData: {
                dataSource: 'MYSQL'
            },
            previewData: [],
            previewHeaders: [],
            previewPage: 1,
            previewPageSize: 10,
            previewTotal: 0,
            previewLoading: false,
            importing: false,
            previewFileName: '',
            fileId: '',
            imageDialogVisible: false,
            currentImage: ''
        }
    },
    methods: {
        handleFileChange(file) {
            const formData = new FormData();
            formData.append('file', file.raw);
            formData.append('dataSource', this.uploadData.dataSource);
            
            this.previewLoading = true;
            axios.post('/api/excel/preview', formData)
                .then(response => {
                    this.previewPage = 1; // 重置页码
                    this.previewData = response.data.content;
                    this.previewHeaders = response.data.headers;
                    this.previewTotal = response.data.total;
                    this.previewFileName = file.name;
                    this.fileId = response.data.fileId;
                    
                    let hasImages = false;
                    this.previewData.forEach(row => {
                        Object.keys(row).forEach(key => {
                            const value = row[key];
                            if (typeof value === 'string' && value.startsWith('data:image/')) {
                                hasImages = true;
                                console.log('检测到图片数据:', key);
                            }
                        });
                    });
                    
                    if (hasImages) {
                        this.$message.success('成功加载包含图片的Excel文件');
                    }
                })
                .catch(() => {
                    this.$message.error('预览失败');
                })
                .finally(() => {
                    this.previewLoading = false;
                });
        },
        handleCurrentChange(page) {
            console.log(`页码变更: ${this.previewPage} -> ${page}`);
            this.previewPage = page;
            this.previewLoading = true;
            
            axios.get(`/api/excel/previewData?fileName=${this.fileId}&page=${page}&size=${this.previewPageSize}`)
                .then(response => {
                    this.previewData = response.data.content;
                    this.previewTotal = response.data.total;
                })
                .catch(() => {
                    this.$message.error('加载预览数据失败');
                })
                .finally(() => {
                    this.previewLoading = false;
                });
        },
        showImageDialog(imageUrl) {
            this.currentImage = imageUrl;
            this.imageDialogVisible = true;
        },
        confirmImport() {
            if (!this.previewFileName || !this.fileId) {
                this.$message.warning('请先选择文件');
                return;
            }
            
            this.importing = true;
            const data = {
                fileName: this.fileId,
                dataSource: this.uploadData.dataSource
            };
            
            axios.post('/api/excel/confirmImport', data)
                .then(() => {
                    this.$message.success('导入成功');
                    this.resetForm();
                })
                .catch(() => {
                    this.$message.error('导入失败');
                })
                .finally(() => {
                    this.importing = false;
                });
        },
        cancelImport() {
            if (this.previewFileName) {
                axios.post('/api/excel/cancelImport', {
                    fileName: this.previewFileName
                })
                .then(() => {
                    this.resetForm();
                })
                .catch(error => {
                    console.error('取消导入失败：', error);
                });
            }
            this.resetForm();
        },
        resetForm() {
            this.previewData = [];
            this.previewHeaders = [];
            this.previewTotal = 0;
            this.previewFileName = '';
            this.fileId = '';
            this.importing = false;
            this.imageDialogVisible = false;
            this.currentImage = '';
            this.previewPage = 1;
        }
    }
};

// 首页组件
const HomePage = {
    template: '#home-template',
    data() {
        return {
            mysqlStats: {
                count: 0,
                lastImport: null
            },
            mongoStats: {
                count: 0,
                lastImport: null
            }
        }
    },
    created() {
        this.fetchStats();
    },
    methods: {
        fetchStats() {
            axios.get('/api/stats/mysql')
                .then(response => {
                    this.mysqlStats = response.data;
                })
                .catch(() => {
                    this.$message.error('获取MySQL统计信息失败');
                });

            axios.get('/api/stats/mongodb')
                .then(response => {
                    this.mongoStats = response.data;
                })
                .catch(() => {
                    this.$message.error('获取MongoDB统计信息失败');
                });
        }
    }
};

// OCR页面组件
const OCRPage = {
    template: '#ocr-template',
    data() {
        return {
            activeTab: 'upload',
            uploadForm: {
                name: ''
            },
            fileList: [],
            processing: false,
            progressPercentage: 0,
            currentProcessingFile: '',
            recognitionResults: [],
            paginatedRecognitionResults: [], // 分页后的识别结果
            recognitionCurrentPage: 1, // 当前页码
            recognitionPageSize: 10, // 每页显示数量
            historyRecords: [],
            editDialogVisible: false,
            viewDialogVisible: false,
            fullImageDialogVisible: false,
            currentFullImage: '',
            editForm: {
                id: null,
                questionNumber: '',
                questionType: '',
                content: '',
                paperName: '',
                paperId: null,
                year: '',
                yearDate: null,
                imageData: '',
                useImageOnly: false
            },
            viewPaperQuestions: [],
            paginatedViewPaperQuestions: [], // 分页后的题目列表
            viewCurrentPage: 1, // 当前页码
            viewPageSize: 10, // 每页显示数量
            saving: false,
            batchEditDialogVisible: false,
            batchEditForm: {
                questionType: '',
                useImageOnly: '',
                applyRange: 'all',
                startIndex: 1,
                endIndex: 1
            },
            currentViewPaper: null,
            currentEditIndex: -1
        }
    },
    created() {
        this.fetchHistoryRecords();
        // 初始化分页数据
        this.updatePaginatedRecognitionResults();
    },
    methods: {
        handleFileChange(file) {
            // 检查文件类型
            const allowedTypes = ['application/pdf', 'image/jpeg', 'image/png'];
            if (!allowedTypes.includes(file.raw.type)) {
                this.$message.error('只支持 PDF、JPG、PNG 格式文件');
                return false;
            }
            
            // 添加到文件列表
            this.fileList = [file];
        },
        startOCR() {
            if (this.fileList.length === 0) {
                this.$message.warning('请先选择文件');
                return;
            }
            
            this.processing = true;
            this.progressPercentage = 0;
            this.recognitionResults = [];
            this.currentProcessingFile = this.fileList[0].name;
            
            // 获取文件名（不含扩展名）作为默认文档名称
            let fileName = this.fileList[0].name;
            let paperName = this.uploadForm.name;
            
            // 如果用户没有输入文档名称，则使用文件名（不含扩展名）
            if (!paperName) {
                // 移除扩展名
                paperName = fileName.replace(/\.[^/.]+$/, "");
            }
            
            console.log("处理PDF文件 - 文件名:", fileName, "文档名称:", paperName);
            
            // 创建FormData对象
            const formData = new FormData();
            formData.append('file', this.fileList[0].raw);
            formData.append('paperName', paperName);
            
            // 模拟进度
            const progressInterval = setInterval(() => {
                if (this.progressPercentage < 90) {
                    this.progressPercentage += 10;
                }
            }, 500);
            
            // 发送请求
            axios.post('/api/pdf/upload', formData)
                .then(response => {
                    clearInterval(progressInterval);
                    this.progressPercentage = 100;
                    
                    if (response.data.success) {
                        // 获取识别结果
                        let questions = response.data.questions || [];
                        
                        console.log("PDF处理成功，获取到", questions.length, "页内容");
                        
                        // 检查是否有年份信息
                        let hasYear = false;
                        if (questions.length > 0 && questions[0].year) {
                            hasYear = true;
                            console.log("从服务器返回的结果中获取到年份:", questions[0].year);
                        }
                        
                        // 如果没有年份信息，设置当前年份
                        if (!hasYear) {
                            const currentYear = new Date().getFullYear().toString();
                            console.log("未获取到年份信息，设置为当前年份:", currentYear);
                            questions = questions.map(q => ({
                                ...q,
                                year: currentYear
                            }));
                        }
                        
                        // 按照页码排序
                        questions.sort((a, b) => {
                            // 提取页码中的数字部分
                            const numA = parseInt(a.questionNumber.replace(/\D/g, '')) || 0;
                            const numB = parseInt(b.questionNumber.replace(/\D/g, '')) || 0;
                            return numA - numB;
                        });
                        
                        this.recognitionResults = questions;
                        this.recognitionCurrentPage = 1; // 重置页码
                        this.updatePaginatedRecognitionResults(); // 更新分页数据
                        this.$message.success('PDF处理成功，共提取 ' + this.recognitionResults.length + ' 页内容');
                        this.activeTab = 'result';
                    } else {
                        this.$message.error('PDF处理失败：' + response.data.errorMessage);
                    }
                })
                .catch(error => {
                    clearInterval(progressInterval);
                    this.progressPercentage = 0;
                    this.$message.error('PDF处理失败：' + (error.response?.data?.errorMessage || error.message || '未知错误'));
                })
                .finally(() => {
                    setTimeout(() => {
                        this.processing = false;
                    }, 500);
                });
        },
        formatContent(content) {
            if (!content) return '';
            return content.replace(/\n/g, '<br>');
        },
        editQuestion(question, index) {
            this.currentEditIndex = index;
            this.editForm = {
                id: question.id,
                questionNumber: question.questionNumber,
                questionType: question.questionType,
                content: question.content,
                imageData: question.imageData,
                paperName: question.paperName,
                paperId: question.paperId,
                year: question.year,
                yearDate: question.year ? new Date(question.year) : null,
                useImageOnly: question.useImageOnly || false
            };
            this.editDialogVisible = true;
        },
        confirmEdit() {
            // 更新年份
            if (this.editForm.yearDate) {
                this.editForm.year = this.editForm.yearDate.getFullYear().toString();
            }
            
            // 如果是编辑预览结果中的题目
            if (this.activeTab === 'result' && this.currentEditIndex >= 0) {
                // 直接更新预览结果中的题目
                this.recognitionResults[this.currentEditIndex] = {
                    ...this.recognitionResults[this.currentEditIndex],
                    questionNumber: this.editForm.questionNumber,
                    questionType: this.editForm.questionType,
                    content: this.editForm.content,
                    paperName: this.editForm.paperName,
                    paperId: this.editForm.paperId,
                    year: this.editForm.year,
                    useImageOnly: this.editForm.useImageOnly
                };
                
                // 重新排序
                this.recognitionResults.sort((a, b) => {
                    const numA = parseInt(a.questionNumber.replace(/\D/g, '')) || 0;
                    const numB = parseInt(b.questionNumber.replace(/\D/g, '')) || 0;
                    return numA - numB;
                });
                
                // 更新分页数据
                this.updatePaginatedRecognitionResults();
                
                this.$message.success('题目已更新');
                this.editDialogVisible = false;
                return;
            }
            
            // 如果是编辑已保存的题目
            if (this.editForm.id) {
                axios.put('/api/pdf/questions/' + this.editForm.id, {
                    id: this.editForm.id,
                    questionNumber: this.editForm.questionNumber,
                    questionType: this.editForm.questionType,
                    content: this.editForm.content,
                    imageData: this.editForm.imageData,
                    paperId: this.editForm.paperId,
                    paperName: this.editForm.paperName,
                    year: this.editForm.year,
                    useImageOnly: this.editForm.useImageOnly
                })
                .then(response => {
                    if (response.data.success) {
                        this.$message.success('题目更新成功');
                        // 刷新试卷详情
                        if (this.viewDialogVisible && this.currentViewPaper) {
                            this.viewPaper(this.currentViewPaper);
                        }
                    } else {
                        this.$message.error('题目更新失败：' + response.data.errorMessage);
                    }
                })
                .catch(error => {
                    this.$message.error('题目更新失败：' + (error.response?.data?.errorMessage || error.message || '未知错误'));
                });
            }
            
            this.editDialogVisible = false;
        },
        deleteQuestion(question, index) {
            // 如果是删除预览结果中的题目
            if (this.activeTab === 'result' && index >= 0) {
                this.$confirm('确定要删除这道题目吗？', '提示', {
                    confirmButtonText: '确定',
                    cancelButtonText: '取消',
                    type: 'warning'
                }).then(() => {
                    this.recognitionResults.splice(index, 1);
                    // 更新分页数据
                    this.updatePaginatedRecognitionResults();
                    this.$message.success('题目已删除');
                }).catch(() => {});
                return;
            }
            
            // 如果是删除已保存的题目
            if (question.id) {
                this.$confirm('确定要删除这道题目吗？', '提示', {
                    confirmButtonText: '确定',
                    cancelButtonText: '取消',
                    type: 'warning'
                }).then(() => {
                    axios.delete('/api/pdf/questions/' + question.id)
                        .then(response => {
                            if (response.data.success) {
                                this.$message.success('题目删除成功');
                                // 刷新试卷详情
                                if (this.viewDialogVisible && this.currentViewPaper) {
                                    // 从当前视图中移除该题目
                                    const index = this.viewPaperQuestions.findIndex(q => q.id === question.id);
                                    if (index !== -1) {
                                        this.viewPaperQuestions.splice(index, 1);
                                        this.updatePaginatedViewPaperQuestions(); // 更新分页数据
                                    } else {
                                        // 如果在当前视图中找不到，则重新加载整个试卷
                                        this.viewPaper(this.currentViewPaper);
                                    }
                                }
                            } else {
                                this.$message.error('题目删除失败：' + response.data.errorMessage);
                            }
                        })
                        .catch(error => {
                            this.$message.error('题目删除失败：' + (error.response?.data?.errorMessage || error.message || '未知错误'));
                        });
                }).catch(() => {});
            }
        },
        saveToDatabase() {
            if (this.recognitionResults.length === 0) {
                this.$message.warning('没有可保存的内容');
                return;
            }
            
            this.saving = true;
            
            // 获取试卷名称和年份
            let paperName = this.uploadForm.name || '';
            
            // 如果uploadForm.name为空，尝试从recognitionResults获取
            if (!paperName && this.recognitionResults.length > 0 && this.recognitionResults[0].paperName) {
                paperName = this.recognitionResults[0].paperName;
            }
            
            // 如果仍然为空，使用默认名称
            if (!paperName) {
                paperName = "未命名文档";
                console.log("使用默认文档名称：未命名文档");
            }
            
            // 获取年份
            let year = '';
            if (this.recognitionResults.length > 0 && this.recognitionResults[0].year) {
                year = this.recognitionResults[0].year;
            } else {
                // 如果没有年份，使用当前年份
                year = new Date().getFullYear().toString();
                console.log("使用当前年份：" + year);
            }
            
            console.log("保存文档 - 名称:", paperName, "年份:", year, "页面数量:", this.recognitionResults.length);
            
            // 确保每个题目都有paperName和year字段
            const questions = this.recognitionResults.map(q => {
                return {
                    ...q,
                    paperName: paperName,
                    year: year
                };
            });
            
            // 检查questions数组是否有内容
            if (!questions || questions.length === 0) {
                this.$message.error('没有可保存的内容');
                this.saving = false;
                return;
            }
            
            // 构建请求数据
            const requestData = {
                paperName: paperName,
                year: year,
                questions: questions
            };
            
            console.log("发送保存请求:", JSON.stringify(requestData).substring(0, 200) + "...");
            
            // 发送保存请求
            axios.post('/api/pdf/save', requestData)
                .then(response => {
                    this.saving = false;
                    console.log("保存成功:", response.data);
                    this.$message.success('保存成功');
                    
                    // 清空表单和结果
                    this.recognitionResults = [];
                    this.fileList = [];
                    this.uploadForm.name = '';
                    this.activeTab = 'history';
                    this.fetchHistoryRecords();
                })
                .catch(error => {
                    this.saving = false;
                    console.error("保存失败:", error.response?.data || error);
                    this.$message.error('保存失败：' + (error.response?.data?.errorMessage || error.message || '未知错误'));
                });
        },
        fetchHistoryRecords() {
            axios.get('/api/pdf/papers')
                .then(response => {
                    if (response.data.success) {
                        this.historyRecords = response.data.data || [];
                    } else {
                        this.$message.error('加载历史记录失败：' + response.data.errorMessage);
                    }
                })
                .catch(error => {
                    this.$message.error('加载历史记录失败：' + (error.response?.data?.errorMessage || error.message || '未知错误'));
                });
        },
        viewPaper(paper) {
            this.currentViewPaper = paper;
            this.viewCurrentPage = 1; // 重置页码
            
            axios.get('/api/pdf/papers/' + paper.id)
                .then(response => {
                    if (response.data.success) {
                        // 获取题目列表
                        let questions = response.data.data.questions || [];
                        
                        // 按照数字感知的方式对题目编号进行排序
                        questions.sort((a, b) => {
                            // 提取题目编号中的数字部分
                            const numA = parseInt(a.questionNumber.replace(/\D/g, '')) || 0;
                            const numB = parseInt(b.questionNumber.replace(/\D/g, '')) || 0;
                            return numA - numB;
                        });
                        
                        this.viewPaperQuestions = questions;
                        this.updatePaginatedViewPaperQuestions(); // 更新分页数据
                        this.viewDialogVisible = true;
                    } else {
                        this.$message.error('获取试卷详情失败：' + response.data.errorMessage);
                    }
                })
                .catch(error => {
                    this.$message.error('获取试卷详情失败：' + (error.response?.data?.errorMessage || error.message || '未知错误'));
                });
        },
        // 处理分页变化
        handleViewPageChange(page) {
            this.viewCurrentPage = page;
            this.updatePaginatedViewPaperQuestions();
        },
        
        // 更新分页后的题目列表
        updatePaginatedViewPaperQuestions() {
            const start = (this.viewCurrentPage - 1) * this.viewPageSize;
            const end = start + this.viewPageSize;
            this.paginatedViewPaperQuestions = this.viewPaperQuestions.slice(start, end);
        },
        deletePaper(paper) {
            this.$confirm('确定要删除这份试卷吗？', '提示', {
                confirmButtonText: '确定',
                cancelButtonText: '取消',
                type: 'warning'
            }).then(() => {
                axios.delete('/api/pdf/papers/' + paper.id)
                    .then(response => {
                        if (response.data.success) {
                            this.$message.success('试卷删除成功');
                            this.fetchHistoryRecords();
                        } else {
                            this.$message.error('试卷删除失败：' + response.data.errorMessage);
                        }
                    })
                    .catch(error => {
                        this.$message.error('试卷删除失败：' + (error.response?.data?.errorMessage || error.message || '未知错误'));
                    });
            }).catch(() => {});
        },
        showFullImage(imageUrl) {
            this.currentFullImage = imageUrl;
            this.fullImageDialogVisible = true;
        },
        batchEditQuestions() {
            this.batchEditForm = {
                questionType: '',
                useImageOnly: '',
                applyRange: 'all',
                startIndex: 1,
                endIndex: Math.min(10, this.recognitionResults.length)
            };
            this.batchEditDialogVisible = true;
        },
        confirmBatchEdit() {
            let startIndex = 0;
            let endIndex = this.recognitionResults.length - 1;
            
            if (this.batchEditForm.applyRange === 'range') {
                startIndex = Math.max(0, this.batchEditForm.startIndex - 1);
                endIndex = Math.min(this.recognitionResults.length - 1, this.batchEditForm.endIndex - 1);
            }
            
            let updateCount = 0;
            
            for (let i = startIndex; i <= endIndex; i++) {
                if (i >= this.recognitionResults.length) break;
                
                if (this.batchEditForm.questionType) {
                    this.recognitionResults[i].questionType = this.batchEditForm.questionType;
                    updateCount++;
                }
                
                if (this.batchEditForm.useImageOnly !== '') {
                    this.recognitionResults[i].useImageOnly = this.batchEditForm.useImageOnly;
                    updateCount++;
                }
            }
            
            // 重新排序
            this.recognitionResults.sort((a, b) => {
                const numA = parseInt(a.questionNumber.replace(/\D/g, '')) || 0;
                const numB = parseInt(b.questionNumber.replace(/\D/g, '')) || 0;
                return numA - numB;
            });
            
            // 更新分页数据
            this.updatePaginatedRecognitionResults();
            
            this.$message.success('已更新 ' + updateCount + ' 道题目');
            this.batchEditDialogVisible = false;
        },
        // 处理识别结果分页变化
        handleRecognitionPageChange(page) {
            this.recognitionCurrentPage = page;
            this.updatePaginatedRecognitionResults();
        },
        
        // 更新分页后的识别结果
        updatePaginatedRecognitionResults() {
            const start = (this.recognitionCurrentPage - 1) * this.recognitionPageSize;
            const end = start + this.recognitionPageSize;
            this.paginatedRecognitionResults = this.recognitionResults.slice(start, end);
        }
    }
};

// 数据管理页面组件
const ManagePage = {
    template: '#manage-template',
    data() {
        return {
            currentDataSource: 'MYSQL',
            currentTable: '',
            tables: [],
            tableData: [],
            tableHeaders: [],
            loading: false,
            currentPage: 1,
            pageSize: 10,
            total: 0,
            editDialogVisible: false,
            editForm: {},
            imageDialogVisible: false,
            currentImage: ''
        }
    },
    created() {
        this.fetchTableList();
    },
    methods: {
        showImageDialog(imageUrl) {
            this.currentImage = imageUrl;
            this.imageDialogVisible = true;
        },
        handleDataSourceChange() {
            this.currentTable = '';
            this.tableData = [];
            this.tableHeaders = [];
            this.currentPage = 1; // 重置页码
            this.fetchTableList();
        },
        fetchTableList() {
            axios.get(`/api/excel/tables?dataSource=${this.currentDataSource}`)
                .then(response => {
                    this.tables = response.data;
                    this.currentTable = '';
                    this.tableData = [];
                    this.tableHeaders = [];
                    this.currentPage = 1; // 重置页码
                    if (this.tables && this.tables.length > 0) {
                        this.currentTable = this.tables[0];
                        this.loadTableData();
                    }
                })
                .catch(() => {
                    this.$message.error('获取表列表失败');
                });
        },
        loadTableData() {
            if (!this.currentTable) return;
            
            this.loading = true;
            console.log(`加载表数据: 表=${this.currentTable}, 数据源=${this.currentDataSource}, 页码=${this.currentPage}, 每页大小=${this.pageSize}`);
            axios.get(`/api/excel/data?tableName=${this.currentTable}&dataSource=${this.currentDataSource}&page=${this.currentPage}&size=${this.pageSize}`)
                .then(response => {
                    const originalData = response.data.content;
                    const originalHeaders = response.data.headers;
                    
                    console.log('后端返回的表头顺序:', originalHeaders);
                    
                    // 确保使用数组保存表头，保持顺序
                    this.tableHeaders = Array.isArray(originalHeaders) ? [...originalHeaders] : [];
                    console.log('前端使用的表头顺序:', this.tableHeaders);
                    
                    // 确保数据按照表头顺序组织
                    this.tableData = originalData.map(row => {
                        const orderedRow = {};
                        this.tableHeaders.forEach(header => {
                            orderedRow[header] = row[header];
                        });
                        return orderedRow;
                    });
                    
                    this.total = response.data.total;
                    console.log(`数据加载成功: 总数=${this.total}, 当前页数据量=${this.tableData.length}`);
                })
                .catch((error) => {
                    console.error('加载数据失败:', error);
                    this.$message.error('加载数据失败');
                })
                .finally(() => {
                    this.loading = false;
                });
        },
        handleCurrentChange(page) {
            console.log(`页码变更: ${this.currentPage} -> ${page}`);
            this.currentPage = page;
            this.loadTableData();
        },
        handleEdit(row) {
            // 创建一个新的有序对象，按照表头顺序组织字段
            const orderedEditForm = {};
            this.tableHeaders.forEach(header => {
                orderedEditForm[header] = row[header];
            });
            
            this.editForm = orderedEditForm;
            this.editDialogVisible = true;
        },
        handleDelete(row) {
            this.$confirm('确认删除该条数据?', '提示', {
                confirmButtonText: '确定',
                cancelButtonText: '取消',
                type: 'warning'
            }).then(() => {
                const dataSource = this.currentDataSource.toLowerCase();
                const id = row[this.tableHeaders[0]];
                
                axios.delete(`/api/dashboard/del/${dataSource}/${this.currentTable}/${id}`)
                    .then(() => {
                        this.$message.success('删除成功');
                        this.loadTableData();
                    })
                    .catch((error) => {
                        this.$message.error('删除失败: ' + (error.response?.data || '未知错误'));
                    });
            }).catch(() => {});
        },
        handleImageUpload(file, header) {
            if (!file.raw.type.startsWith('image/')) {
                this.$message.error('只能上传图片文件!');
                return;
            }
            
            const reader = new FileReader();
            reader.onload = (e) => {
                const base64 = e.target.result;
                this.editForm[header] = base64;
            };
            reader.readAsDataURL(file.raw);
        },
        confirmEdit() {
            const dataSource = this.currentDataSource.toLowerCase();
            
            let id;
            if (dataSource === 'mysql') {
                id = this.editForm.system_id;
            } else {
                const mongoId = this.editForm._id;
                if (typeof mongoId === 'object') {
                    if (mongoId.$oid) {
                        id = mongoId.$oid;
                    }
                    else if (mongoId.timestamp) {
                        id = mongoId.timestamp;
                    }
                    else {
                        id = mongoId;
                    }
                } else {
                    id = mongoId;
                }
            }
            
            if (!id && id !== 0) {
                this.$message.error('ID不存在，无法更新数据');
                return;
            }
            
            axios.put(`/api/dashboard/upd/${dataSource}/${this.currentTable}/${id}`, this.editForm)
                .then(() => {
                    this.$message.success('修改成功');
                    this.editDialogVisible = false;
                    this.loadTableData();
                })
                .catch((error) => {
                    this.$message.error('修改失败: ' + (error.response?.data || '未知错误'));
                });
        },
        exportToExcel() {
            window.location.href = `/api/excel/exportToExcel?tableName=${this.currentTable}&dataSource=${this.currentDataSource}`;
        },
        exportToCsv() {
            window.location.href = `/api/excel/exportToCsv?tableName=${this.currentTable}&dataSource=${this.currentDataSource}`;
        }
    }
};

// 注册路由
const routes = [
    { path: '/', redirect: '/home' },
    { path: '/home', component: HomePage },
    { path: '/import', component: ImportPage },
    { path: '/manage', component: ManagePage },
    { path: '/ocr', component: OCRPage }
];

const router = new VueRouter({
    routes
});

// 创建Vue实例
new Vue({
    el: '#app',
    router,
    data: {
        activeMenu: '/home'
    },
    methods: {
        logout() {
            axios.post('/logout')
                .then(() => {
                    sessionStorage.clear();
                    localStorage.clear();
                    window.location.href = '/login';
                })
                .catch(() => {
                    this.$message.error('退出失败');
                });
        }
    },
    watch: {
        '$route'(to) {
            this.activeMenu = to.path;
        }
    }
});