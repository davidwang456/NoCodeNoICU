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
            fileId: ''
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
                    this.previewData = response.data.content;
                    this.previewHeaders = response.data.headers;
                    this.previewTotal = response.data.total;
                    this.previewFileName = file.name;
                    this.fileId = response.data.fileId;
                })
                .catch(() => {
                    this.$message.error('预览失败');
                })
                .finally(() => {
                    this.previewLoading = false;
                });
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
            editForm: {}
        }
    },
    created() {
        this.fetchTableList();
    },
    methods: {
        handleDataSourceChange() {
            this.currentTable = '';
            this.tableData = [];
            this.tableHeaders = [];
            this.fetchTableList();
        },
        fetchTableList() {
            axios.get(`/api/excel/tables?dataSource=${this.currentDataSource}`)
                .then(response => {
                    this.tables = response.data;
                    // 清空当前表和数据
                    this.currentTable = '';
                    this.tableData = [];
                    this.tableHeaders = [];
                    // 只有在确认有表存在的情况下才选择第一个表并加载数据
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
            axios.get(`/api/excel/data?tableName=${this.currentTable}&dataSource=${this.currentDataSource}`)
                .then(response => {
                    // 获取原始数据
                    const originalData = response.data.content;
                    const originalHeaders = response.data.headers;
                    
                    // 调试日志
                    console.log('Original data:', originalData);
                    
                    // 过滤掉 system_id 从显示的表头中
                    this.tableHeaders = originalHeaders.filter(header => header !== 'system_id');
                    
                    // 保留原始数据，包括 system_id
                    this.tableData = originalData;
                    
                    this.total = response.data.total;
                })
                .catch(() => {
                    this.$message.error('加载数据失败');
                })
                .finally(() => {
                    this.loading = false;
                });
        },
        handleCurrentChange(page) {
            this.currentPage = page;
            this.loadTableData();
        },
        handleEdit(row) {
            // 调试日志
            console.log('Edit row:', row);
            
            // 确保复制完整的行数据，包括 system_id
            this.editForm = JSON.parse(JSON.stringify(row));
            
            // 调试日志
            console.log('Edit form:', this.editForm);
            
            this.editDialogVisible = true;
        },
        handleDelete(row) {
            this.$confirm('确认删除该条数据?', '提示', {
                confirmButtonText: '确定',
                cancelButtonText: '取消',
                type: 'warning'
            }).then(() => {
                // 将 dataSource 转换为小写以匹配后端期望的格式
                const dataSource = this.currentDataSource.toLowerCase();
                // 使用第一列的值作为 id
                const id = row[this.tableHeaders[0]];
                
                // 修改为匹配后端 URL 格式
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
        confirmEdit() {
            // 调试日志
            console.log('Confirming edit with form:', this.editForm);
            
            const dataSource = this.currentDataSource.toLowerCase();
            const id = this.editForm.system_id;
            
            // 调试日志
            console.log('Using system_id:', id);
            
            if (!id && id !== 0) {
                this.$message.error('系统编号不存在，无法更新数据');
                return;
            }
            
            axios.put(`/api/dashboard/upd/${dataSource}/${this.currentTable}/${id}`, this.editForm)
                .then(() => {
                    this.$message.success('修改成功');
                    this.editDialogVisible = false;
                    this.loadTableData();
                })
                .catch((error) => {
                    console.error('Update error:', error);
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
    { path: '/manage', component: ManagePage }
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