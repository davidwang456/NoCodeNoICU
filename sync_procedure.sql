DELIMITER //

DROP PROCEDURE IF EXISTS sync_vuln_advisories //

CREATE PROCEDURE sync_vuln_advisories()
BEGIN
    -- 声明变量
    DECLARE batch_size INT DEFAULT 5000;
    DECLARE total_inserted INT DEFAULT 0;
    DECLARE current_batch INT DEFAULT 0;
    DECLARE total_records INT DEFAULT 0;
    DECLARE min_id INT;
    DECLARE max_id INT;
    DECLARE current_min_id INT;
    DECLARE current_max_id INT;
    DECLARE done BOOLEAN DEFAULT FALSE;
    
    -- 获取增量表中的ID范围
    SELECT MIN(id), MAX(id), COUNT(*) 
    INTO min_id, max_id, total_records 
    FROM vulnerablility_advisories;
    
    -- 如果没有记录，直接返回
    IF total_records = 0 THEN
        SELECT '增量表中没有数据，无需同步' AS message;
        LEAVE sync_vuln_advisories;
    END IF;
    
    -- 记录开始同步
    SELECT CONCAT('开始同步数据，增量表总记录数: ', total_records, 
                 '，ID范围: ', min_id, ' - ', max_id) AS message;
    
    -- 设置初始ID范围
    SET current_min_id = min_id;
    
    -- 循环处理数据，每次处理一个ID范围
    WHILE current_min_id <= max_id DO
        -- 计算当前批次的最大ID
        SET current_max_id = current_min_id + batch_size - 1;
        
        -- 当前批次号
        SET current_batch = current_batch + 1;
        
        -- 开始事务
        START TRANSACTION;
        
        -- 插入数据，跳过hkey已存在的记录
        INSERT INTO all_vuln_advisories 
            (vulnerability_id, platform, segment, package, value, hkey, created_time)
        SELECT 
            va.vlunerability_id, 
            va.platform, 
            va.segment, 
            va.package, 
            va.value, 
            va.hkey, 
            va.created_at
        FROM 
            vulnerablility_advisories va
        LEFT JOIN 
            all_vuln_advisories ava ON va.hkey = ava.hkey
        WHERE 
            va.id BETWEEN current_min_id AND current_max_id
            AND ava.id IS NULL;
        
        -- 获取插入的记录数
        SET total_inserted = total_inserted + ROW_COUNT();
        
        -- 提交事务
        COMMIT;
        
        -- 记录当前批次处理情况
        SELECT CONCAT('已完成第 ', current_batch, ' 批次，ID范围: ', 
                     current_min_id, ' - ', current_max_id,
                     '，累计插入: ', total_inserted, ' 条记录') AS message;
        
        -- 更新下一批次的起始ID
        SET current_min_id = current_max_id + 1;
        
        -- 休眠100毫秒，避免对数据库造成过大压力
        DO SLEEP(0.1);
    END WHILE;
    
    -- 记录同步完成
    SELECT CONCAT('同步完成，共处理 ', total_records, ' 条记录，实际插入 ', 
                 total_inserted, ' 条记录') AS message;
END //

DELIMITER ;

-- 调用存储过程示例
-- CALL sync_vuln_advisories();

-- 查看同步结果示例
-- SELECT COUNT(*) FROM all_vuln_advisories; 