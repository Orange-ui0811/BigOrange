package com.orangejd.server.controller.admin;

import com.orangejd.common.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

@RestController
@RequestMapping("/admin/stats")
@lombok.RequiredArgsConstructor
public class AdminStatsController {

    private final DataSource dataSource;

    @GetMapping("/quota-usage")
    public Result<Map<String, Object>> quotaUsage(@RequestParam(defaultValue = "7") int days) {
        Map<String, Object> result = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection()) {
            // 按天统计消耗
            String sql = """
                SELECT DATE(create_time) AS day,
                       SUM(CASE WHEN type='CONSUME' THEN 1 ELSE 0 END) AS consumes,
                       SUM(CASE WHEN type='RECHARGE' THEN 1 ELSE 0 END) AS recharges,
                       SUM(CASE WHEN type='REFUND' THEN 1 ELSE 0 END) AS refunds,
                       SUM(CASE WHEN type='CONSUME' THEN -amount ELSE 0 END) AS consumed,
                       SUM(CASE WHEN type='RECHARGE' THEN amount ELSE 0 END) AS recharged,
                       SUM(CASE WHEN type='REFUND' THEN amount ELSE 0 END) AS refunded
                FROM quota_log
                WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL ? DAY)
                GROUP BY DATE(create_time)
                ORDER BY day DESC
                """;
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, Math.min(days, 90));
            ResultSet rs = ps.executeQuery();
            List<Map<String, Object>> daily = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("day", rs.getString("day"));
                row.put("consumes", rs.getInt("consumes"));
                row.put("consumed", rs.getInt("consumed"));
                row.put("recharges", rs.getInt("recharges"));
                row.put("refunds", rs.getInt("refunds"));
                daily.add(row);
            }
            result.put("daily", daily);

            // 按模块统计
            String sql2 = """
                SELECT description AS module, COUNT(*) AS count, SUM(CASE WHEN type='CONSUME' THEN -amount ELSE 0 END) AS total
                FROM quota_log WHERE type='CONSUME' AND create_time >= DATE_SUB(CURDATE(), INTERVAL ? DAY)
                GROUP BY description ORDER BY total DESC LIMIT 10
                """;
            PreparedStatement ps2 = conn.prepareStatement(sql2);
            ps2.setInt(1, Math.min(days, 90));
            ResultSet rs2 = ps2.executeQuery();
            List<Map<String, Object>> byModule = new ArrayList<>();
            while (rs2.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("module", rs2.getString("module"));
                row.put("count", rs2.getInt("count"));
                row.put("total", rs2.getInt("total"));
                byModule.add(row);
            }
            result.put("byModule", byModule);
        } catch (Exception e) {
            return Result.error("统计查询失败: " + e.getMessage());
        }
        return Result.success(result);
    }
}
