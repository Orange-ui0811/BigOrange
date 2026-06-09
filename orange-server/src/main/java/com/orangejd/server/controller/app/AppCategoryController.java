package com.orangejd.server.controller.app;

import com.orangejd.common.result.Result;
import com.orangejd.pojo.vo.CategoryVO;
import com.orangejd.server.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/app/category")
public class AppCategoryController {

    @Autowired
    private CategoryService categoryService;

    @GetMapping
    public Result<List<CategoryVO>> listActive(@RequestParam(required = false) String type) {
        return Result.success(categoryService.listActiveByType(type));
    }
}
