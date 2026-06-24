package com.orangejd.server.controller.admin;

import com.orangejd.common.utils.ExcelExportUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

@RestController
@RequestMapping("/admin/report")
public class ReportController {

    @GetMapping("/export")
    public void export(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=report.xlsx");
        ExcelExportUtil.writeSimpleReport(
                response.getOutputStream(),
                "report",
                Arrays.asList("metric", "value"),
                Collections.singletonList(Arrays.asList("demo", "1"))
        );
    }
}

