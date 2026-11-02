package com.orangejd.server.controller.admin;

import com.orangejd.common.result.Result;
import com.orangejd.pojo.dto.CategoryDTO;
import com.orangejd.pojo.vo.CategoryVO;
import com.orangejd.server.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/category")
@lombok.RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categoryService;

    @PostMapping
    public Result<CategoryVO> create(@RequestBody CategoryDTO dto) {
        return Result.success(categoryService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<CategoryVO> update(@PathVariable Long id, @RequestBody CategoryDTO dto) {
        return Result.success(categoryService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.success();
    }

    @GetMapping
    public Result<List<CategoryVO>> listAll() {
        return Result.success(categoryService.listAll());
    }
}
