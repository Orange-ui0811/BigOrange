package com.orangejd.server.controller.app;

import com.orangejd.common.result.Result;
import com.orangejd.server.service.FileUploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/app/common")
public class AppCommonController {

    @Autowired
    private FileUploadService fileUploadService;

    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file,
                                  @RequestParam(defaultValue = "general") String dir) {
        return Result.success(fileUploadService.upload(file, dir));
    }
}
