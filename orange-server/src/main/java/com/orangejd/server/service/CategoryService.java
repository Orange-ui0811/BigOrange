package com.orangejd.server.service;

import com.orangejd.pojo.dto.CategoryDTO;
import com.orangejd.pojo.vo.CategoryVO;

import java.util.List;

public interface CategoryService {

    CategoryVO create(CategoryDTO dto);

    CategoryVO update(Long id, CategoryDTO dto);

    void delete(Long id);

    List<CategoryVO> listAll();

    List<CategoryVO> listActiveByType(String type);
}
