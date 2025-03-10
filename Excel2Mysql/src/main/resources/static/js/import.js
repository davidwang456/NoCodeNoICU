Vue.component('import-view', {
    template: '#import-template',
    data() {
        return {
            uploadData: {
                dataSource: 'MYSQL'
            },
            previewData: [],
            previewHeaders: [],
            previewLoading: false,
            importing: false,
            currentFile: null,
            totalRows: 0,
            imageDialogVisible: false,
            currentImage: ''
        };
    },
    methods: {
        handleFileChange(file) {
            this.currentFile = file;
            this.previewLoading = true;
            const formData = new FormData();
            formData.append('file', file.raw);
            formData.append('dataSource', this.uploadData.dataSource);

            axios.post('/api/excel/preview', formData)
                .then(response => {
                    if (response.data.success) {
                        // 保存文件ID，用于后续确认导入
                        this.currentFile = {
                            name: response.data.fileId,
                            raw: file.raw
                        };
                        
                        // 处理预览数据
                        this.previewData = response.data.content || [];
                        this.previewHeaders = response.data.headers || [];
                        
                        // 记录总行数
                        this.totalRows = response.data.total || 0;
                        
                        // 检查是否有图片数据
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
                    } else {
                        this.$message.error('预览失败：' + (response.data.error || '未知错误'));
                    }
                })
                .catch(error => {
                    console.error('预览错误:', error);
                    this.$message.error('预览失败：' + (error.response?.data?.error || error.message || '未知错误'));
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
            if (!this.currentFile) {
                this.$message.warning('请先选择文件');
                return;
            }
            this.importing = true;

            axios.post('/api/excel/confirmImport', {
                fileName: this.currentFile.name,
                dataSource: this.uploadData.dataSource
            })
                .then(response => {
                    this.$message.success('导入成功');
                    this.resetForm();
                })
                .catch(error => {
                    this.$message.error('导入失败：' + error.message);
                })
                .finally(() => {
                    this.importing = false;
                });
        },
        cancelImport() {
            if (this.currentFile) {
                axios.post('/api/excel/cancelImport', {
                    fileName: this.currentFile.name
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
            this.currentFile = null;
            this.previewData = [];
            this.previewHeaders = [];
            this.totalRows = 0;
            this.imageDialogVisible = false;
            this.currentImage = '';
        }
    }
});