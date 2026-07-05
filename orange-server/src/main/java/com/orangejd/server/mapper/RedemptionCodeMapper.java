package com.orangejd.server.mapper;

import com.orangejd.pojo.entity.RedemptionCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RedemptionCodeMapper {
    int insert(RedemptionCode record);
    RedemptionCode selectByCode(@Param("code") String code);
    RedemptionCode selectByCodeForUpdate(@Param("code") String code);
    int updateUsed(@Param("code") String code, @Param("usedBy") Long usedBy);
    List<RedemptionCode> selectAll(@Param("offset") int offset, @Param("limit") int limit);
    long countAll();
}
