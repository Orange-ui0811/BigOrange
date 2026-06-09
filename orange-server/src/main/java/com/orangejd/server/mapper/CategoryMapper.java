package com.orangejd.server.mapper;

import com.orangejd.pojo.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CategoryMapper {

    int insert(Category category);

    int update(Category category);

    int deleteById(@Param("id") Long id);

    Category selectById(@Param("id") Long id);

    List<Category> selectAll();

    List<Category> selectByType(@Param("type") String type);

    List<Category> selectActiveByType(@Param("type") String type);

    int countByTypeAndName(@Param("type") String type, @Param("name") String name);
}
