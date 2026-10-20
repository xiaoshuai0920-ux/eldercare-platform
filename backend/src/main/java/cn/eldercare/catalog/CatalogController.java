package cn.eldercare.catalog;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import cn.eldercare.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import java.sql.Statement;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {
    private final JdbcTemplate jdbc;
    public CatalogController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record ItemRequest(@NotBlank String category, @NotBlank @Size(max = 100) String name,
                              @NotBlank @Size(max = 1000) String description, @NotBlank String pricingModel,
                              @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
                              @Min(1) @Max(1440) int minMinutes, @Min(1) @Max(1440) int maxMinutes,
                              @NotNull LocalTime availableFrom, @NotNull LocalTime availableTo,
                              @Min(0) int advanceMinutes, @NotBlank @Size(max = 500) String ruleText) { }
    public record AreaRequest(@NotBlank String level, Long parentId, @NotBlank @Size(max = 80) String name) { }

    @GetMapping("/areas")
    public List<Map<String, Object>> areas() {
        return jdbc.queryForList("SELECT a.id,a.parent_id,a.level,a.name,CONCAT_WS(' ',grand.name,parent.name,a.name) AS full_name FROM service_area a LEFT JOIN service_area parent ON parent.id=a.parent_id LEFT JOIN service_area grand ON grand.id=parent.parent_id WHERE a.active=1 ORDER BY a.level,a.id");
    }

    @GetMapping("/items")
    public List<Map<String, Object>> items(@RequestParam(required = false) Long streetId,
                                           @RequestParam(required = false) String category) {
        String sql = "SELECT DISTINCT i.id,i.category,i.name,i.description,i.pricing_model,i.unit_price,i.min_minutes,i.max_minutes,i.available_from,i.available_to,i.rule_text FROM service_item i LEFT JOIN service_item_area a ON a.item_id=i.id WHERE i.active=1";
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        if (streetId != null) { sql += " AND a.street_id=?"; args.add(streetId); }
        if (category != null && !category.isBlank()) { sql += " AND i.category=?"; args.add(category); }
        return jdbc.queryForList(sql + " ORDER BY i.id DESC", args.toArray());
    }

    @GetMapping("/items/{id}")
    public Map<String, Object> item(@PathVariable long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM service_item WHERE id=? AND active=1", id);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "服务不存在或已下架");
        return rows.getFirst();
    }

    @GetMapping("/items/{id}/areas")
    public List<Map<String,Object>> publicItemAreas(@PathVariable long id) {
        item(id);
        return coverageAreas(id);
    }

    @GetMapping("/admin/items")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> adminItems() {
        return jdbc.queryForList("SELECT id,category,name,description,pricing_model,unit_price,min_minutes,max_minutes,available_from,available_to,advance_minutes,rule_text,active FROM service_item ORDER BY id DESC");
    }

    @GetMapping("/admin/items/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> adminItem(@PathVariable long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM service_item WHERE id=?", id);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "服务项目不存在");
        return rows.getFirst();
    }

    @PutMapping("/admin/items/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void updateItem(@PathVariable long id, @Valid @RequestBody ItemRequest input) {
        validateItem(input);
        int changed = jdbc.update("UPDATE service_item SET category=?,name=?,description=?,pricing_model=?,unit_price=?,min_minutes=?,max_minutes=?,available_from=?,available_to=?,advance_minutes=?,rule_text=? WHERE id=?",
                input.category(),input.name().trim(),input.description().trim(),input.pricingModel(),input.unitPrice(),input.minMinutes(),input.maxMinutes(),
                input.availableFrom(),input.availableTo(),input.advanceMinutes(),input.ruleText().trim(),id);
        if (changed == 0) throw new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "服务项目不存在");
    }

    @PostMapping("/admin/areas")
    @PreAuthorize("hasRole('ADMIN')")
    public void createArea(@Valid @RequestBody AreaRequest input) {
        if (!List.of("CITY","DISTRICT","STREET").contains(input.level())) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LEVEL", "区域层级无效");
        if (input.level().equals("CITY") != (input.parentId() == null)) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PARENT", "区域上级无效");
        if (input.parentId() != null) {
            List<String> parents = jdbc.query("SELECT level FROM service_area WHERE id=? AND active=1", (row, n) -> row.getString(1), input.parentId());
            String expected = input.level().equals("STREET") ? "DISTRICT" : "CITY";
            if (parents.isEmpty() || !parents.getFirst().equals(expected)) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PARENT", "区域上级层级无效");
        }
        jdbc.update("INSERT INTO service_area(parent_id,level,name,active) VALUES(?,?,?,1)", input.parentId(), input.level(), input.name().trim());
    }

    @PostMapping("/admin/items")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Long> createItem(@Valid @RequestBody ItemRequest input) {
        validateItem(input);
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO service_item(category,name,description,pricing_model,unit_price,min_minutes,max_minutes,available_from,available_to,advance_minutes,rule_text,active) VALUES(?,?,?,?,?,?,?,?,?,?,?,0)", Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, input.category()); statement.setString(2, input.name().trim());
            statement.setString(3, input.description().trim()); statement.setString(4, input.pricingModel());
            statement.setBigDecimal(5, input.unitPrice()); statement.setInt(6, input.minMinutes()); statement.setInt(7, input.maxMinutes());
            statement.setObject(8, input.availableFrom()); statement.setObject(9, input.availableTo());
            statement.setInt(10, input.advanceMinutes()); statement.setString(11, input.ruleText().trim());
            return statement;
        }, key);
        return Map.of("id", key.getKey().longValue());
    }

    @PostMapping("/admin/items/{id}/areas/{streetId}")
    @PreAuthorize("hasRole('ADMIN')")
    public void cover(@PathVariable long id, @PathVariable long streetId) {
        adminItem(id);
        Integer street = jdbc.queryForObject("SELECT COUNT(*) FROM service_area WHERE id=? AND level='STREET' AND active=1", Integer.class, streetId);
        if (street == null || street == 0) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STREET", "请选择有效街道");
        jdbc.update("INSERT IGNORE INTO service_item_area(item_id,street_id) VALUES(?,?)", id, streetId);
    }

    @GetMapping("/admin/items/{id}/areas")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String,Object>> itemAreas(@PathVariable long id) {
        adminItem(id);
        return coverageAreas(id);
    }

    private List<Map<String,Object>> coverageAreas(long id) {
        return jdbc.queryForList("SELECT street.id,CONCAT_WS(' ',city.name,district.name,street.name) AS full_name FROM service_item_area cover JOIN service_area street ON street.id=cover.street_id JOIN service_area district ON district.id=street.parent_id JOIN service_area city ON city.id=district.parent_id WHERE cover.item_id=? AND street.active=1 AND district.active=1 AND city.active=1 ORDER BY street.id",id);
    }

    @DeleteMapping("/admin/items/{id}/areas/{streetId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void removeCoverage(@PathVariable long id,@PathVariable long streetId) {
        List<Map<String,Object>> items=jdbc.queryForList("SELECT id,active FROM service_item WHERE id=? FOR UPDATE",id);
        if(items.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"SERVICE_NOT_FOUND","服务项目不存在");
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM service_item_area WHERE item_id=?",Integer.class,id);
        if(cn.eldercare.common.SqlValues.booleanValue(items.getFirst().get("active")) && count!=null && count<=1)
            throw new ApiException(HttpStatus.CONFLICT,"LAST_COVERAGE","已上架项目须保留至少一个覆盖街道，请先下架");
        if(jdbc.update("DELETE FROM service_item_area WHERE item_id=? AND street_id=?",id,streetId)!=1)
            throw new ApiException(HttpStatus.NOT_FOUND,"COVERAGE_NOT_FOUND","该覆盖街道不存在");
    }

    @PostMapping("/admin/items/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    public void publish(@PathVariable long id) {
        adminItem(id);
        Integer covered = jdbc.queryForObject("SELECT COUNT(*) FROM service_item_area a JOIN service_area s ON s.id=a.street_id WHERE a.item_id=? AND s.active=1", Integer.class, id);
        if (covered == null || covered == 0) throw new ApiException(HttpStatus.CONFLICT, "NO_COVERAGE", "服务项目至少需要一个覆盖街道");
        jdbc.update("UPDATE service_item SET active=1 WHERE id=?", id);
    }

    @PostMapping("/admin/items/{id}/unpublish")
    @PreAuthorize("hasRole('ADMIN')")
    public void unpublish(@PathVariable long id) {
        if (jdbc.update("UPDATE service_item SET active=0 WHERE id=?", id) == 0)
            throw new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "服务项目不存在");
    }

    private void validateItem(ItemRequest input) {
        if (!List.of("MEAL","CLEANING","ESCORT").contains(input.category())
                || !List.of("FIXED","HOURLY").contains(input.pricingModel())
                || input.minMinutes() > input.maxMinutes() || !input.availableFrom().isBefore(input.availableTo())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ITEM_RULE", "服务项目规则无效");
        }
    }
}
