/**
 * 多格式文档导入功能
 */
Vue.component('doc-component', {
    template: '#doc-template',
    data: function() {
        return {
            activeTab: 'upload',
            uploadForm: {
                file: null,
                paperName: ''
            },
            fileList: [],
            recognitionResults: [],
            recognitionCurrentPage: 1,
            recognitionPageSize: 10,
            historyRecords: [],
            searchQuery: '',
            searchResults: [],
            processing: false,
            saving: false,
            searching: false,
            progressPercentage: 0,
            currentProcessingFile: '',
            currentProcessingIndex: 0,
            totalFilesToProcess: 0,
            editDialogVisible: false,
            viewDialogVisible: false,
            fullImageDialogVisible: false,
            currentFullImage: '',
            editForm: {
                pageNumber: '',
                content: '',
                paperName: '',
                imageData: ''
            },
            editIndex: -1,
            viewPaperQuestions: [],
            viewCurrentPage: 1,
            viewPageSize: 10,
            currentPaper: null
        };
    },
    computed: {
        paginatedRecognitionResults: function() {
            const start = (this.recognitionCurrentPage - 1) * this.recognitionPageSize;
            const end = start + this.recognitionPageSize;
            return this.recognitionResults.slice(start, end);
        },
        paginatedViewPaperQuestions: function() {
            const start = (this.viewCurrentPage - 1) * this.viewPageSize;
            const end = start + this.viewPageSize;
            return this.viewPaperQuestions.slice(start, end);
        }
    },
    created: function() {
        this.loadPapers();
    },
    methods: {
        /**
         * 处理文件选择变更
         */
        handleFileChange(file, fileList) {
            console.log('文件选择变更:', file, fileList);
            this.fileList = fileList;
        },

        /**
         * 开始处理上传的文件
         */
        startProcess() {
            console.log('开始处理按钮点击');
            if (this.fileList.length === 0) {
                this.$message.warning('请先选择至少一个文件');
                console.log('未选择文件，取消处理');
                return;
            }

            this.processing = true;
            this.progressPercentage = 0;
            this.currentProcessingIndex = 0;
            this.totalFilesToProcess = this.fileList.length;
            this.recognitionResults = [];
            
            console.log('开始处理文件，总数:', this.fileList.length);
            this.processNextFile();
        },

        /**
         * 处理下一个文件
         */
        processNextFile() {
            if (this.currentProcessingIndex >= this.fileList.length) {
                // 所有文件处理完成
                this.processing = false;
                this.progressPercentage = 100;
                this.$message.success('所有文件处理完成');
                console.log('所有文件处理完成');
                this.activeTab = 'result';
                return;
            }

            const file = this.fileList[this.currentProcessingIndex].raw;
            this.currentProcessingFile = file.name;
            
            // 计算进度
            this.progressPercentage = Math.floor((this.currentProcessingIndex / this.totalFilesToProcess) * 100);
            
            const formData = new FormData();
            formData.append('file', file);
            
            console.log('处理文件:', file.name, '文件大小:', file.size, '文件类型:', file.type);
            
            // 发送文件到后端处理
            console.log('发送上传请求到:', '/api/doc/upload');
            axios.post('/api/doc/upload', formData, {
                headers: {
                    'Content-Type': 'multipart/form-data'
                }
            })
            .then(response => {
                console.log('上传请求响应:', response.data);
                if (response.data.success) {
                    const paperName = response.data.paperName;
                    const pages = response.data.questions;
                    
                    // 添加文件名到每个页面对象
                    pages.forEach(page => {
                        page.paperName = paperName;
                    });
                    
                    // 按页码排序
                    pages.sort((a, b) => a.pageNumber - b.pageNumber);
                    
                    // 合并识别结果
                    this.recognitionResults = this.recognitionResults.concat(pages);
                    
                    console.log(`文件 ${file.name} 处理完成, 识别到 ${pages.length} 页`);
                    this.$message.success(`文件 ${file.name} 处理完成, 识别到 ${pages.length} 页`);
                } else {
                    console.error('处理失败:', response.data.message);
                    this.$message.error(`文件 ${file.name} 处理失败: ${response.data.message}`);
                }
                
                // 处理下一个文件
                this.currentProcessingIndex++;
                this.processNextFile();
            })
            .catch(error => {
                console.error('处理出错:', error);
                console.error('错误详情:', error.response ? error.response.data : '无响应数据');
                this.$message.error(`文件 ${file.name} 处理出错: ${error.message}`);
                
                // 处理下一个文件
                this.currentProcessingIndex++;
                this.processNextFile();
            });
        },

        /**
         * 格式化内容，将换行符转换为 HTML 换行标签
         */
        formatContent(content) {
            if (!content) return '';
            return content.replace(/\n/g, '<br>');
        },

        /**
         * 处理识别结果页面变更
         */
        handleRecognitionPageChange(page) {
            this.recognitionCurrentPage = page;
        },

        /**
         * 处理查看文件页面变更
         */
        handleViewPageChange(page) {
            this.viewCurrentPage = page;
        },

        /**
         * 编辑页面
         */
        editPage(row, index) {
            this.editForm = { ...row };
            this.editIndex = index;
            this.editDialogVisible = true;
        },

        /**
         * 确认编辑
         */
        confirmEdit() {
            if (this.editIndex >= 0) {
                // 更新识别结果中的数据
                this.recognitionResults.splice(this.editIndex, 1, { ...this.editForm });
            } else {
                // 更新查看文件对话框中的数据
                const index = this.viewPaperQuestions.findIndex(q => q.id === this.editForm.id);
                if (index >= 0) {
                    this.viewPaperQuestions.splice(index, 1, { ...this.editForm });
                    
                    // 如果有ID，则向后端发送更新请求
                    if (this.editForm.id) {
                        this.updateQuestionInDatabase(this.editForm);
                    }
                }
            }
            
            this.editDialogVisible = false;
        },

        /**
         * 更新数据库中的问题
         */
        updateQuestionInDatabase(question) {
            axios.post('/api/doc/updateQuestion', question)
                .then(response => {
                    if (response.data.success) {
                        this.$message.success('页面更新成功');
                    } else {
                        this.$message.error('页面更新失败: ' + response.data.message);
                    }
                })
                .catch(error => {
                    console.error('更新出错:', error);
                    this.$message.error('页面更新出错: ' + error.message);
                });
        },

        /**
         * 删除页面
         */
        deletePage(row, index) {
            this.$confirm('确认删除该页面?', '提示', {
                confirmButtonText: '确定',
                cancelButtonText: '取消',
                type: 'warning'
            }).then(() => {
                if (index >= 0) {
                    // 从识别结果中删除
                    this.recognitionResults.splice(index, 1);
                    this.$message.success('页面已删除');
                } else if (row.id) {
                    // 从数据库中删除
                    axios.post('/api/doc/deleteQuestion', { id: row.id })
                        .then(response => {
                            if (response.data.success) {
                                const index = this.viewPaperQuestions.findIndex(q => q.id === row.id);
                                if (index >= 0) {
                                    this.viewPaperQuestions.splice(index, 1);
                                }
                                this.$message.success('页面删除成功');
                                
                                // 更新当前文件的页面数量
                                if (this.currentPaper) {
                                    this.currentPaper.questionCount--;
                                    const paperIndex = this.historyRecords.findIndex(p => p.id === this.currentPaper.id);
                                    if (paperIndex >= 0) {
                                        this.historyRecords[paperIndex].questionCount = this.currentPaper.questionCount;
                                    }
                                }
                            } else {
                                this.$message.error('页面删除失败: ' + response.data.message);
                            }
                        })
                        .catch(error => {
                            console.error('删除出错:', error);
                            this.$message.error('页面删除出错: ' + error.message);
                        });
                }
            }).catch(() => {
                // 取消删除
            });
        },

        /**
         * 保存到数据库
         */
        saveToDatabase() {
            if (this.recognitionResults.length === 0) {
                this.$message.warning('没有可保存的识别结果');
                return;
            }

            // 按文件名分组
            const paperGroups = {};
            this.recognitionResults.forEach(page => {
                if (!paperGroups[page.paperName]) {
                    paperGroups[page.paperName] = [];
                }
                paperGroups[page.paperName].push(page);
            });

            this.saving = true;
            const savePromises = [];

            // 保存每个文档组
            for (const paperName in paperGroups) {
                const pages = paperGroups[paperName];
                
                const saveRequest = {
                    paperName: paperName,
                    questions: pages
                };
                
                const promise = axios.post('/api/doc/save', saveRequest)
                    .then(response => {
                        if (response.data.success) {
                            return {
                                success: true,
                                paperName: paperName,
                                paperId: response.data.paperId,
                                count: pages.length
                            };
                        } else {
                            return {
                                success: false,
                                paperName: paperName,
                                message: response.data.message
                            };
                        }
                    })
                    .catch(error => {
                        return {
                            success: false,
                            paperName: paperName,
                            message: error.message
                        };
                    });
                
                savePromises.push(promise);
            }

            // 处理所有保存结果
            Promise.all(savePromises)
                .then(results => {
                    let successCount = 0;
                    let errorCount = 0;
                    
                    results.forEach(result => {
                        if (result.success) {
                            successCount++;
                            console.log(`文档 ${result.paperName} 保存成功，ID: ${result.paperId}，页数: ${result.count}`);
                        } else {
                            errorCount++;
                            console.error(`文档 ${result.paperName} 保存失败: ${result.message}`);
                        }
                    });
                    
                    if (successCount > 0 && errorCount === 0) {
                        this.$message.success(`成功保存 ${successCount} 个文档`);
                        this.recognitionResults = [];
                        this.loadPapers();
                        this.activeTab = 'history';
                    } else if (successCount > 0 && errorCount > 0) {
                        this.$message.warning(`部分文档保存成功: ${successCount} 成功, ${errorCount} 失败`);
                        this.loadPapers();
                    } else {
                        this.$message.error('所有文档保存失败');
                    }
                    
                    this.saving = false;
                });
        },

        /**
         * 加载已保存的文档列表
         */
        loadPapers() {
            axios.get('/api/doc/papers')
                .then(response => {
                    if (response.data.success) {
                        this.historyRecords = response.data.papers;
                        // 格式化日期
                        this.historyRecords.forEach(paper => {
                            if (paper.createTime) {
                                paper.createTime = this.formatDateTime(paper.createTime);
                            }
                        });
                    } else {
                        console.error('加载文档失败:', response.data.message);
                        this.$message.error('加载文档失败: ' + response.data.message);
                    }
                })
                .catch(error => {
                    console.error('加载文档出错:', error);
                    this.$message.error('加载文档出错: ' + error.message);
                });
        },

        /**
         * 格式化日期时间
         */
        formatDateTime(datetime) {
            if (!datetime) return '';
            const date = new Date(datetime);
            return date.toLocaleString();
        },

        /**
         * 搜索文档或问题
         */
        searchPapersOrQuestions() {
            if (!this.searchQuery.trim()) {
                this.loadPapers();
                return;
            }
            
            this.searching = true;
            
            axios.get('/api/doc/search', {
                params: {
                    query: this.searchQuery
                }
            })
            .then(response => {
                if (response.data.success) {
                    this.searchResults = response.data.results;
                    this.historyRecords = this.searchResults;
                    
                    // 格式化日期
                    this.historyRecords.forEach(paper => {
                        if (paper.createTime) {
                            paper.createTime = this.formatDateTime(paper.createTime);
                        }
                    });
                    
                    if (this.searchResults.length === 0) {
                        this.$message.info('未找到与 "' + this.searchQuery + '" 相关的文档');
                    }
                } else {
                    console.error('搜索失败:', response.data.message);
                    this.$message.error('搜索失败: ' + response.data.message);
                }
                this.searching = false;
            })
            .catch(error => {
                console.error('搜索出错:', error);
                this.$message.error('搜索出错: ' + error.message);
                this.searching = false;
            });
        },

        /**
         * 清除搜索
         */
        clearSearch() {
            this.searchQuery = '';
            this.searchResults = [];
            this.loadPapers();
        },

        /**
         * 查看文档
         */
        viewPaper(paper) {
            this.currentPaper = paper;
            this.viewPaperQuestions = [];
            this.viewCurrentPage = 1;
            
            axios.get('/api/doc/paper/' + paper.id)
                .then(response => {
                    if (response.data.success) {
                        this.viewPaperQuestions = response.data.questions;
                        this.viewDialogVisible = true;
                    } else {
                        console.error('加载文档详情失败:', response.data.message);
                        this.$message.error('加载文档详情失败: ' + response.data.message);
                    }
                })
                .catch(error => {
                    console.error('加载文档详情出错:', error);
                    this.$message.error('加载文档详情出错: ' + error.message);
                });
        },

        /**
         * 删除文档
         */
        deletePaper(paper) {
            this.$confirm('确认删除文档 "' + paper.paperName + '"?', '提示', {
                confirmButtonText: '确定',
                cancelButtonText: '取消',
                type: 'warning'
            }).then(() => {
                axios.post('/api/doc/deletePaper', { id: paper.id })
                    .then(response => {
                        if (response.data.success) {
                            const index = this.historyRecords.findIndex(p => p.id === paper.id);
                            if (index >= 0) {
                                this.historyRecords.splice(index, 1);
                            }
                            this.$message.success('文档删除成功');
                        } else {
                            this.$message.error('文档删除失败: ' + response.data.message);
                        }
                    })
                    .catch(error => {
                        console.error('删除出错:', error);
                        this.$message.error('文档删除出错: ' + error.message);
                    });
            }).catch(() => {
                // 取消删除
            });
        },

        /**
         * 显示全屏图像
         */
        showFullImage(imageData) {
            this.currentFullImage = imageData;
            this.fullImageDialogVisible = true;
        }
    }
}); 