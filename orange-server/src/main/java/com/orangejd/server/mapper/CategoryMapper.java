package com.orangejd.server.mapper;

import com.orangejd.pojo.entity.Category;
import com.orangejd.server.annotation.AutoFill;
import com.orangejd.common.enumeration.OperationType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CategoryMapper {

    @AutoFill(OperationType.INSERT)
    int insert(Category category);

    @AutoFill(OperationType.UPDATE)
    int update(Category category);

    int deleteById(@Param("id") Long id);

    Category selectById(@Param("id") Long id);

    List<Category> selectAll();

    List<Category> selectByType(@Param("type") String type);

    List<Category> selectActiveByType(@Param("type") String type);

    int countByTypeAndName(@Param("type") String type, @Param("name") String name);
}
