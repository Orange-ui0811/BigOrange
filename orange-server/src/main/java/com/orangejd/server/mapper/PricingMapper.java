package com.orangejd.server.mapper;

import com.orangejd.pojo.entity.AiServicePricing;
import com.orangejd.pojo.entity.PricingChangeLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PricingMapper {

    /** 查询所有定价（启动加载到缓存） */
    List<AiServicePricing> selectAll();

    /** 按 key 查询单条 */
    AiServicePricing selectByKey(@Param("serviceKey") String serviceKey);

    /** 插入或更新定价（ON DUPLICATE KEY UPDATE） */
    int upsert(AiServicePricing pricing);

    /** 插入变更审计日志 */
    int insertChangeLog(PricingChangeLog log);

    /** 查询最近 N 条变更日志 */
    List<PricingChangeLog> selectRecentLogs(@Param("limit") int limit);
}
