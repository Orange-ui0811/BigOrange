package com.orangejd.server.mapper;

import com.orangejd.pojo.entity.WorkSaleOption;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WorkSaleOptionMapper {

    int insert(WorkSaleOption option);

    int insertBatch(@Param("list") List<WorkSaleOption> list);

    List<WorkSaleOption> selectByWorkId(@Param("workId") Long workId);

    int deleteByWorkId(@Param("workId") Long workId);
}
