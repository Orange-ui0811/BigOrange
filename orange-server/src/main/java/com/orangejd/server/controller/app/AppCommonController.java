package com.orangejd.server.controller.app;

import com.orangejd.common.result.Result;
import com.orangejd.server.service.FileUploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/app/common")
@lombok.RequiredArgsConstructor
public class AppCommonController {

    private final FileUploadService fileUploadService;

    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file,
                                  @RequestParam(defaultValue = "general") String dir) {
        return Result.success(fileUploadService.upload(file, dir));
    }

    @PostMapping("/upload/video")
    public Result<String> uploadVideo(@RequestParam("file") MultipartFile file) {
        return Result.success(fileUploadService.uploadVideo(file));
    }
}
