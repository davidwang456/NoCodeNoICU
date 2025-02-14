CREATE TABLE `sys_user` (
  `user_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(32) NOT NULL COMMENT 'MD5加密后的密码',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否可用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 插入一个默认用户，密码为123456的MD5值
INSERT INTO `sys_user` (`username`, `password`, `enabled`) 
VALUES ('admin', 'e10adc3949ba59abbe56e057f20f883e', 1); 