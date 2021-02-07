# NoCodeNoICU
目标：
我们的口号是减少代码，减少ICU,减少996
功能实现：
  1.支持利用excel/cs/txt/pdf/word等格式的文件导入到关系型数据库或者非关系型数据如mongo，elasticsearch，solr等，支持全量和增量更新
    也支持直接连接数据源如mysql，pg等关系型数据和非关系型数据库mongo，redis，elasticsearch，solr等
  2.配置好查询语句即可一键发布到服务端，提供http服务
  3.支持数据可视化展示分析
  4.支持模板方式统一数据通用查询，暂时支持两张表的join
  5.支持数据调用明细查询
  6.支持数据大屏展示
  7.支持数据权限后台统一配置
  8.数据全景(数字资产统计)
后端技术实现：
  spring boot +postgresql +mongo +elasticsearch
  tika/EasyExcel/poi
  DaVinci
  datax/databus
前端：
  vue
