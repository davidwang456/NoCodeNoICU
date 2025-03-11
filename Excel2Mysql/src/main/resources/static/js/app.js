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