package cn.eldercare.catalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.eldercare.common.ApiException;
import cn.eldercare.family.FamilyService;
import cn.eldercare.family.PermissionColumn;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RecommendationController {
    private final RecommendationRepository recommendations;
    private final FamilyService family;

    public RecommendationController(RecommendationRepository recommendations, FamilyService family) {
        this.recommendations = recommendations;
        this.family = family;
    }

    public record TagRequest(@NotBlank @Size(max = 60) String tag) { }
    private record Ranked(Map<String, Object> item, int matches) { }

    @GetMapping("/catalog/recommendations")
    @PreAuthorize("hasRole('USER')")
    public List<Map<String, Object>> recommend(@RequestParam long elderId, @RequestParam long streetId, Authentication auth) {
        family.requirePermission(Long.parseLong(auth.getName()), elderId, PermissionColumn.VIEW_SERVICE);
        List<Ranked> ranked = new ArrayList<>();
        for (Map<String, Object> item : recommendations.itemsByStreet(streetId)) {
            List<String> matches = recommendations.matchedTags(elderId, item.get("id"));
            Map<String, Object> result = new LinkedHashMap<>(item);
            result.put("reason", matches.isEmpty() ? "服务覆盖所选街道" : "符合偏好：" + String.join("、", matches));
            ranked.add(new Ranked(result, matches.size()));
        }
        ranked.sort(Comparator.comparingInt(Ranked::matches).reversed());
        return ranked.stream().map(Ranked::item).toList();
    }

    @GetMapping("/elders/{elderId}/preferences")
    @PreAuthorize("hasRole('USER')")
    public List<String> preferences(@PathVariable long elderId, Authentication auth) {
        family.requirePermission(Long.parseLong(auth.getName()), elderId, PermissionColumn.VIEW_SERVICE);
        return recommendations.preferences(elderId);
    }

    @PostMapping("/elders/{elderId}/preferences")
    @PreAuthorize("hasRole('USER')")
    public void addPreference(@PathVariable long elderId, @Valid @RequestBody TagRequest input, Authentication auth) {
        family.requirePermission(Long.parseLong(auth.getName()), elderId, PermissionColumn.MANAGE_FAMILY);
        recommendations.addPreference(elderId, input.tag());
    }

    @DeleteMapping("/elders/{elderId}/preferences/{tag}")
    @PreAuthorize("hasRole('USER')")
    public void removePreference(@PathVariable long elderId, @PathVariable String tag, Authentication auth) {
        family.requirePermission(Long.parseLong(auth.getName()), elderId, PermissionColumn.MANAGE_FAMILY);
        recommendations.removePreference(elderId, tag);
    }

    @GetMapping("/catalog/admin/items/{itemId}/tags")
    @PreAuthorize("hasRole('ADMIN')")
    public List<String> itemTags(@PathVariable long itemId) {
        return recommendations.itemTags(itemId);
    }

    @PostMapping("/catalog/admin/items/{itemId}/tags")
    @PreAuthorize("hasRole('ADMIN')")
    public void addItemTag(@PathVariable long itemId, @Valid @RequestBody TagRequest input) {
        if (!recommendations.itemExists(itemId)) throw new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "服务项目不存在");
        recommendations.addItemTag(itemId, input.tag());
    }

    @DeleteMapping("/catalog/admin/items/{itemId}/tags/{tag}")
    @PreAuthorize("hasRole('ADMIN')")
    public void removeItemTag(@PathVariable long itemId, @PathVariable String tag) {
        recommendations.removeItemTag(itemId, tag);
    }
}
