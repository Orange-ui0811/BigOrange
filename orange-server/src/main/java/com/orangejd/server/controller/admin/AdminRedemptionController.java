package com.orangejd.server.controller.admin;

import com.orangejd.common.context.BaseContext;
import com.orangejd.common.result.Result;
import com.orangejd.pojo.entity.RedemptionCode;
import com.orangejd.server.mapper.RedemptionCodeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/redemption")
public class AdminRedemptionController {

    private static final String CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 48;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    private RedemptionCodeMapper mapper;

    @PostMapping("/generate")
    public Result<List<String>> generate(@RequestBody Map<String, Object> body) {
        int count = body.containsKey("count") ? ((Number) body.get("count")).intValue() : 10;
        int quota = body.containsKey("quota") ? ((Number) body.get("quota")).intValue() : 500;
        if (count < 1 || count > 500) return Result.error("数量范围 1-500");
        if (quota < 1 || quota > 1000000) return Result.error("配额范围 1-1000000");

        Long adminId = BaseContext.getCurrentId();
        List<String> codes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String code = generateCode();
            RedemptionCode rc = new RedemptionCode();
            rc.setCode(code);
            rc.setQuota(quota);
            rc.setStatus(1);
            rc.setCreatedBy(adminId);
            mapper.insert(rc);
            codes.add(code);
        }
        return Result.success(codes);
    }

    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "50") int size) {
        int safeSize = com.orangejd.common.util.PageUtil.capSize(size);
        int offset = com.orangejd.common.util.PageUtil.offset(page, safeSize);
        List<RedemptionCode> list = mapper.selectAll(offset, safeSize);
        long total = mapper.countAll();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", list);
        result.put("total", total);
        return Result.success(result);
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
