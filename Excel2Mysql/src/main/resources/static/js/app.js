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
        handleImport() {
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
                    let successMsg = '导入成功';
                    if (this.uploadData.dataSource === 'BOTH') {
                        successMsg = '已同时导入到 MySQL 和 MongoDB';
                    }
                    this.$message.success(successMsg);
                    this.resetImport();
                })
                .catch((error) => {
                    console.error('Import error:', error);
                    this.$message.error('导入失败');
                })
                .finally(() => {
                    this.importing = false;
                });
        },
        resetImport() {
            this.previewData = [];
            this.previewHeaders = [];
            this.previewTotal = 0;
            this.previewFileName = '';
            this.fileId = '';
            this.$refs.upload.clearFiles();
        },
        beforeUpload() {
            return false;
        }
    }
};

// 管理页面组件
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
            total: 0
        }
    },
    created() {
        this.loadTables();
    },
    methods: {
        loadTables() {
            this.loading = true;
            // 使用 StatsController 中的 API
            if (this.currentDataSource === 'MYSQL') {
                axios.get('/api/dashboard/mysql-stats')
                    .then(response => {
                        this.tables = response.data.tables;
                        if (this.tables.length > 0) {
                            this.currentTable = this.tables[0];
                            this.loadTableData();
                        }
                    })
                    .catch(() => {
                        this.$message.error('加载表格列表失败');
                    })
                    .finally(() => {
                        this.loading = false;
                    });
            } else {
                axios.get('/api/dashboard/mongodb-stats')
                    .then(response => {
                        this.tables = response.data.tables;
                        if (this.tables.length > 0) {
                            this.currentTable = this.tables[0];
                            this.loadTableData();
                        }
                    })
                    .catch(() => {
                        this.$message.error('加载表格列表失败');
                    })
                    .finally(() => {
                        this.loading = false;
                    });
            }
        },
        loadTableData() {
            if (!this.currentTable) return;
            
            this.loading = true;
            const dataSource = this.currentDataSource.toLowerCase();
            axios.get(`/api/dashboard/${dataSource}-data/${this.currentTable}`, {
                params: {
                    page: this.currentPage,
                    size: this.pageSize
                }
            })
                .then(response => {
                    this.tableData = response.data.content;
                    this.total = response.data.total;
                    if (response.data.headers) {
                        this.tableHeaders = response.data.headers;
                    }
                })
                .catch(() => {
                    this.$message.error('加载数据失败');
                })
                .finally(() => {
                    this.loading = false;
                });
        },
        handleDataSourceChange() {
            this.currentTable = '';
            this.tableData = [];
            this.tableHeaders = [];
            this.loadTables();
        },
        handleTableChange() {
            this.currentPage = 1;
            this.loadTableData();
        },
        handleCurrentChange(page) {
            this.currentPage = page;
            this.loadTableData();
        },
        exportData() {
            window.open(`/api/export/${this.currentDataSource}/${this.currentTable}`, '_blank');
        },
        exportToExcel() {
            if (!this.currentTable) return;
            window.open(`/api/excel/exportToExcel/${this.currentDataSource}/${this.currentTable}`, '_blank');
        },
        exportToCsv() {
            if (!this.currentTable) return;
            window.open(`/api/excel/exportToCsv/${this.currentDataSource}/${this.currentTable}`, '_blank');
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
            },
            loading: true
        }
    },
    created() {
        console.log('HomePage component created');
        this.loadStats();
    },
    methods: {
        loadStats() {
            console.log('Loading stats...');
            this.loading = true;
            Promise.all([
                axios.get('/api/dashboard/mysql-stats').catch(err => {
                    console.error('MySQL stats error:', err);
                    return { data: { count: 0, tables: [] } };
                }),
                axios.get('/api/dashboard/mongodb-stats').catch(err => {
                    console.error('MongoDB stats error:', err);
                    return { data: { count: 0, tables: [] } };
                })
            ])
            .then(([mysqlResponse, mongoResponse]) => {
                console.log('MySQL response:', mysqlResponse.data);
                console.log('MongoDB response:', mongoResponse.data);
                this.mysqlStats = {
                    count: mysqlResponse.data.count,
                    lastImport: mysqlResponse.data.lastImport
                };
                this.mongoStats = {
                    count: mongoResponse.data.count,
                    lastImport: mongoResponse.data.lastImport
                };
            })
            .catch(error => {
                console.error('Error loading stats:', error);
                this.$message.error('加载统计信息失败');
            })
            .finally(() => {
                this.loading = false;
            });
        }
    }
};

// 路由配置
const router = new VueRouter({
    mode: 'history',  // 使用 HTML5 history 模式
    routes: [
        { path: '/', redirect: '/home' },
        { path: '/home', component: HomePage },
        { path: '/import', component: ImportPage },
        { path: '/manage', component: ManagePage }
    ]
});

// 添加全局导航守卫
router.beforeEach((to, from, next) => {
    console.log('Route change:', from.path, '->', to.path);
    next();
});

// 创建 Vue 实例
new Vue({
    el: '#app',
    router,
    data: {
        activeMenu: '/home'
    },
    computed: {
        pageTitle() {
            const route = this.$route.path;
            switch (route) {
                case '/home':
                    return '首页';
                case '/import':
                    return '数据导入';
                case '/manage':
                    return '数据管理';
                default:
                    return '';
            }
        }
    },
    methods: {
        logout() {
            window.location.href = '/logout';
        }
    },
    watch: {
        '$route'(to) {
            this.activeMenu = to.path;
            console.log('Route changed to:', to.path);
        }
    },
    mounted() {
        console.log('Vue app mounted');
    }
}); 