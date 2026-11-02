package com.orangejd.server.controller.admin;

import com.orangejd.common.result.Result;
import com.orangejd.server.service.FileUploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin/common")
@lombok.RequiredArgsConstructor
public class CommonController {

    private final FileUploadService fileUploadService;

    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file,
                                  @RequestParam(defaultValue = "general") String dir) {
        String url = fileUploadService.upload(file, dir);
        return Result.success(url);
    }
}
