package cn.eldercare.catalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.eldercare.common.ApiException;
import cn.eldercare.family.FamilyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbc;
    private final FamilyService family;
    public RecommendationController(JdbcTemplate jdbc,FamilyService family){this.jdbc=jdbc;this.family=family;}
    public record TagRequest(@NotBlank @Size(max=60) String tag){}
    private record Ranked(Map<String,Object> item,int matches){}

    @GetMapping("/catalog/recommendations")
    @PreAuthorize("hasRole('USER')")
    public List<Map<String,Object>> recommend(@RequestParam long elderId,@RequestParam long streetId,Authentication auth){
        family.requirePermission(Long.parseLong(auth.getName()),elderId,"can_view_service");
        List<Map<String,Object>> items=jdbc.queryForList("SELECT DISTINCT i.id,i.category,i.name,i.description,i.pricing_model,i.unit_price,i.min_minutes,i.max_minutes,i.rule_text FROM service_item i JOIN service_item_area a ON a.item_id=i.id WHERE i.active=1 AND a.street_id=? ORDER BY i.id DESC",streetId);
        List<Ranked> ranked=new ArrayList<>();
        for(Map<String,Object> item:items){
            List<String> matches=jdbc.query("SELECT t.tag FROM service_item_tag t JOIN elder_preference_tag p ON p.tag=t.tag AND p.elder_id=? WHERE t.item_id=? ORDER BY t.tag",(row,n)->row.getString(1),elderId,item.get("id"));
            Map<String,Object> result=new LinkedHashMap<>(item);
            result.put("reason",matches.isEmpty()?"服务覆盖所选街道":"符合偏好："+String.join("、",matches));
            ranked.add(new Ranked(result,matches.size()));
        }
        ranked.sort(Comparator.comparingInt(Ranked::matches).reversed());
        return ranked.stream().map(Ranked::item).toList();
    }

    @GetMapping("/elders/{elderId}/preferences")
    @PreAuthorize("hasRole('USER')")
    public List<String> preferences(@PathVariable long elderId,Authentication auth){
        family.requirePermission(Long.parseLong(auth.getName()),elderId,"can_view_service");
        return jdbc.query("SELECT tag FROM elder_preference_tag WHERE elder_id=? ORDER BY tag",(row,n)->row.getString(1),elderId);
    }

    @PostMapping("/elders/{elderId}/preferences")
    @PreAuthorize("hasRole('USER')")
    public void addPreference(@PathVariable long elderId,@Valid @RequestBody TagRequest input,Authentication auth){
        family.requirePermission(Long.parseLong(auth.getName()),elderId,"can_manage_family");
        jdbc.update("INSERT IGNORE INTO elder_preference_tag(elder_id,tag) VALUES(?,?)",elderId,input.tag().trim());
    }

    @DeleteMapping("/elders/{elderId}/preferences/{tag}")
    @PreAuthorize("hasRole('USER')")
    public void removePreference(@PathVariable long elderId,@PathVariable String tag,Authentication auth){
        family.requirePermission(Long.parseLong(auth.getName()),elderId,"can_manage_family");
        jdbc.update("DELETE FROM elder_preference_tag WHERE elder_id=? AND tag=?",elderId,tag);
    }

    @GetMapping("/catalog/admin/items/{itemId}/tags")
    @PreAuthorize("hasRole('ADMIN')")
    public List<String> itemTags(@PathVariable long itemId){return jdbc.query("SELECT tag FROM service_item_tag WHERE item_id=? ORDER BY tag",(row,n)->row.getString(1),itemId);}

    @PostMapping("/catalog/admin/items/{itemId}/tags")
    @PreAuthorize("hasRole('ADMIN')")
    public void addItemTag(@PathVariable long itemId,@Valid @RequestBody TagRequest input){
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM service_item WHERE id=?",Integer.class,itemId);
        if(count==null||count==0)throw new ApiException(HttpStatus.NOT_FOUND,"SERVICE_NOT_FOUND","服务项目不存在");
        jdbc.update("INSERT IGNORE INTO service_item_tag(item_id,tag) VALUES(?,?)",itemId,input.tag().trim());
    }

    @DeleteMapping("/catalog/admin/items/{itemId}/tags/{tag}")
    @PreAuthorize("hasRole('ADMIN')")
    public void removeItemTag(@PathVariable long itemId,@PathVariable String tag){jdbc.update("DELETE FROM service_item_tag WHERE item_id=? AND tag=?",itemId,tag);}
}
