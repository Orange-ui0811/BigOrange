package com.orangejd.server.service;

import com.orangejd.server.dto.BeginnerProjectView;
import com.orangejd.server.dto.CareerProjectView;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class TrendRecommendationSanitizer {
    @SuppressWarnings("unchecked")
    public void sanitize(Map<String, Object> result, List<BeginnerProjectView> beginner,
                         List<CareerProjectView> career) {
        Set<String> allowed = beginner.stream().map(BeginnerProjectView::htmlUrl)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        career.stream().map(CareerProjectView::htmlUrl).filter(Objects::nonNull).forEach(allowed::add);
        if (!(result.get("recommendations") instanceof List<?> recommendations)) return;
        for (Object recommendation : recommendations) {
            if (!(recommendation instanceof Map<?, ?> raw)) continue;
            Map<String, Object> item = (Map<String, Object>) raw;
            if (!(item.get("references") instanceof List<?> references)) continue;
            item.put("references", references.stream().filter(reference -> reference instanceof Map<?, ?> map
                    && map.get("url") != null && allowed.contains(String.valueOf(map.get("url"))))
                    .map(reference -> (Object) reference).toList());
        }
    }
}
