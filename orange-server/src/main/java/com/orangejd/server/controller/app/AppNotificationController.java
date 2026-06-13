package com.orangejd.server.controller.app;

import com.orangejd.common.result.Result;
import com.orangejd.pojo.entity.Notification;
import com.orangejd.server.service.INotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/app/notification")
public class AppNotificationController {

    @Autowired private INotificationService notificationService;

    @GetMapping("/list")
    public Result<List<Notification>> list(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return Result.success(notificationService.listMyNotifications(page, size));
    }

    @PutMapping("/{id}/read")
    public Result<?> markRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return Result.success();
    }

    @GetMapping("/unread-count")
    public Result<Integer> unreadCount() {
        return Result.success(notificationService.unreadCount());
    }

    @DeleteMapping("/batch")
    public Result<?> batchDelete(@RequestBody java.util.List<Long> ids) {
        notificationService.batchDelete(ids);
        return Result.success();
    }
}
