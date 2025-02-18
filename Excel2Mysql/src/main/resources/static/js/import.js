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
            currentFile: null
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
                    this.previewData = response.data.data;
                    this.previewHeaders = response.data.headers;
                })
                .catch(error => {
                    this.$message.error('预览失败：' + error.message);
                })
                .finally(() => {
                    this.previewLoading = false;
                });
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
        }
    }
});