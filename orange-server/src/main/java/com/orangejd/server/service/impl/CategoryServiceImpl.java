package com.orangejd.server.service.impl;

import com.orangejd.common.constant.MessageConstant;
import com.orangejd.common.exception.BaseException;
import com.orangejd.pojo.dto.CategoryDTO;
import com.orangejd.pojo.entity.Category;
import com.orangejd.pojo.vo.CategoryVO;
import com.orangejd.server.mapper.CategoryMapper;
import com.orangejd.server.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private CategoryMapper categoryMapper;

    @Override
    public CategoryVO create(CategoryDTO dto) {
        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new BaseException(MessageConstant.CATEGORY_NAME_EMPTY);
        }
        if (dto.getType() == null || (!"WORK".equals(dto.getType()) && !"REQUIREMENT".equals(dto.getType()))) {
            throw new BaseException(MessageConstant.CATEGORY_TYPE_INVALID);
        }

        // 检查同类型下名称重复
        int count = categoryMapper.countByTypeAndName(dto.getType(), dto.getName().trim());
        if (count > 0) {
            throw new BaseException(MessageConstant.CATEGORY_NAME_DUPLICATE);
        }

        Category category = new Category();
        category.setName(dto.getName().trim());
        category.setType(dto.getType());
        category.setSort(dto.getSort() != null ? dto.getSort() : 0);
        category.setStatus(dto.getStatus() != null ? dto.getStatus() : "ACTIVE");
        categoryMapper.insert(category);

        return toVO(category);
    }

    @Override
    public CategoryVO update(Long id, CategoryDTO dto) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BaseException(MessageConstant.CATEGORY_NOT_FOUND);
        }

        // 检查名称重复（排除自身）
        if (dto.getName() != null && !dto.getName().trim().isEmpty()) {
            int count = categoryMapper.countByTypeAndName(
                    dto.getType() != null ? dto.getType() : category.getType(),
                    dto.getName().trim());
            if (count > 0 && !category.getName().equals(dto.getName().trim())) {
                throw new BaseException(MessageConstant.CATEGORY_NAME_DUPLICATE);
            }
        }

        category.setName(dto.getName() != null ? dto.getName().trim() : category.getName());
        category.setType(dto.getType() != null ? dto.getType() : category.getType());
        category.setSort(dto.getSort() != null ? dto.getSort() : category.getSort());
        category.setStatus(dto.getStatus() != null ? dto.getStatus() : category.getStatus());
        categoryMapper.update(category);

        return toVO(categoryMapper.selectById(id));
    }

    @Override
    public void delete(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BaseException(MessageConstant.CATEGORY_NOT_FOUND);
        }
        // Phase 3+ 时此处可扩展：检查是否有关联作品/需求
        categoryMapper.deleteById(id);
    }

    @Override
    public List<CategoryVO> listAll() {
        return categoryMapper.selectAll().stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public List<CategoryVO> listActiveByType(String type) {
        if (type != null && !"WORK".equals(type) && !"REQUIREMENT".equals(type)) {
            throw new BaseException(MessageConstant.CATEGORY_TYPE_INVALID);
        }
        List<Category> list;
        if (type == null || type.isEmpty()) {
            list = categoryMapper.selectAll();
            list = list.stream()
                    .filter(c -> "ACTIVE".equals(c.getStatus()))
                    .collect(Collectors.toList());
        } else {
            list = categoryMapper.selectActiveByType(type);
        }
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    private CategoryVO toVO(Category category) {
        CategoryVO vo = new CategoryVO();
        vo.setId(category.getId());
        vo.setName(category.getName());
        vo.setType(category.getType());
        vo.setSort(category.getSort());
        vo.setStatus(category.getStatus());
        vo.setCreateTime(category.getCreateTime());
        vo.setUpdateTime(category.getUpdateTime());
        return vo;
    }
}
