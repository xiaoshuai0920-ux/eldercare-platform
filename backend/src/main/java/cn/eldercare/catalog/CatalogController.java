package cn.eldercare.catalog;

import java.util.List;
import java.util.Map;

import cn.eldercare.common.ApiException;
import cn.eldercare.common.SqlValues;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {
    private final CatalogRepository catalog;

    public CatalogController(CatalogRepository catalog) { this.catalog = catalog; }

    @GetMapping("/areas")
    public List<Map<String, Object>> areas() {
        return catalog.areas();
    }

    @GetMapping("/items")
    public List<Map<String, Object>> items(@RequestParam(required = false) Long streetId,
                                           @RequestParam(required = false) String category) {
        return catalog.items(streetId, category);
    }

    @GetMapping("/items/{id}")
    public Map<String, Object> item(@PathVariable long id) {
        Map<String, Object> item = catalog.publicItem(id);
        if (item == null) throw new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "服务不存在或已下架");
        return item;
    }

    @GetMapping("/items/{id}/areas")
    public List<Map<String, Object>> publicItemAreas(@PathVariable long id) {
        item(id);
        return catalog.coverageAreas(id);
    }

    @GetMapping("/admin/items")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> adminItems() {
        return catalog.adminItems();
    }

    @GetMapping("/admin/items/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> adminItem(@PathVariable long id) {
        Map<String, Object> item = catalog.adminItem(id);
        if (item == null) throw new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "服务项目不存在");
        return item;
    }

    @PutMapping("/admin/items/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void updateItem(@PathVariable long id, @Valid @RequestBody ItemRequest input) {
        validateItem(input);
        if (catalog.updateItem(id, input) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "服务项目不存在");
        }
    }

    @PostMapping("/admin/areas")
    @PreAuthorize("hasRole('ADMIN')")
    public void createArea(@Valid @RequestBody AreaRequest input) {
        if (!List.of("CITY", "DISTRICT", "STREET").contains(input.level())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LEVEL", "区域层级无效");
        }
        if (input.level().equals("CITY") != (input.parentId() == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PARENT", "区域上级无效");
        }
        if (input.parentId() != null) {
            String expected = input.level().equals("STREET") ? "DISTRICT" : "CITY";
            if (!expected.equals(catalog.parentLevel(input.parentId()))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PARENT", "区域上级层级无效");
            }
        }
        catalog.createArea(input.parentId(), input.level(), input.name());
    }

    @PostMapping("/admin/items")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Long> createItem(@Valid @RequestBody ItemRequest input) {
        validateItem(input);
        return Map.of("id", catalog.createItem(input));
    }

    @PostMapping("/admin/items/{id}/areas/{streetId}")
    @PreAuthorize("hasRole('ADMIN')")
    public void cover(@PathVariable long id, @PathVariable long streetId) {
        adminItem(id);
        if (!catalog.isActiveStreet(streetId)) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STREET", "请选择有效街道");
        catalog.cover(id, streetId);
    }

    @GetMapping("/admin/items/{id}/areas")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> itemAreas(@PathVariable long id) {
        adminItem(id);
        return catalog.coverageAreas(id);
    }

    @DeleteMapping("/admin/items/{id}/areas/{streetId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void removeCoverage(@PathVariable long id, @PathVariable long streetId) {
        Map<String, Object> item = catalog.itemForUpdate(id);
        if (item == null) throw new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "服务项目不存在");
        if (SqlValues.booleanValue(item.get("active")) && catalog.countCoverage(id) <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "LAST_COVERAGE", "已上架项目须保留至少一个覆盖街道，请先下架");
        }
        if (catalog.deleteCoverage(id, streetId) != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, "COVERAGE_NOT_FOUND", "该覆盖街道不存在");
        }
    }

    @PostMapping("/admin/items/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    public void publish(@PathVariable long id) {
        adminItem(id);
        if (catalog.countActiveCoverage(id) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "NO_COVERAGE", "服务项目至少需要一个覆盖街道");
        }
        catalog.publish(id);
    }

    @PostMapping("/admin/items/{id}/unpublish")
    @PreAuthorize("hasRole('ADMIN')")
    public void unpublish(@PathVariable long id) {
        if (catalog.unpublish(id) == 0) throw new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "服务项目不存在");
    }

    private void validateItem(ItemRequest input) {
        if (!List.of("MEAL", "CLEANING", "ESCORT").contains(input.category())
                || !List.of("FIXED", "HOURLY").contains(input.pricingModel())
                || input.minMinutes() > input.maxMinutes() || !input.availableFrom().isBefore(input.availableTo())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ITEM_RULE", "服务项目规则无效");
        }
    }
}
