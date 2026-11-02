package com.orangejd.server.controller.app;

import com.orangejd.common.result.PageResult;
import com.orangejd.common.result.Result;
import com.orangejd.pojo.vo.FavoriteVO;
import com.orangejd.server.service.FavoriteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/app/favorite")
@lombok.RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @PostMapping
    public Result<?> favorite(@RequestParam String targetType, @RequestParam Long targetId) {
        favoriteService.favorite(targetType, targetId);
        return Result.success();
    }

    @DeleteMapping
    public Result<?> unfavorite(@RequestParam String targetType, @RequestParam Long targetId) {
        favoriteService.unfavorite(targetType, targetId);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<?> unfavoriteById(@PathVariable Long id) {
        favoriteService.unfavoriteById(id);
        return Result.success();
    }

    @GetMapping("/list")
    public Result<PageResult<FavoriteVO>> myFavorites(@RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return Result.success(favoriteService.listMyFavorites(page, size));
    }
}
