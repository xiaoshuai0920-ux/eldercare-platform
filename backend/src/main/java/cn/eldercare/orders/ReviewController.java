package cn.eldercare.orders;

import java.util.Map;

import cn.eldercare.common.ApiException;
import cn.eldercare.common.PageQuery;
import cn.eldercare.common.PageResult;
import cn.eldercare.family.FamilyService;
import cn.eldercare.family.OrderActionAccess;
import cn.eldercare.family.PermissionColumn;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ReviewController {
    private final ReviewRepository reviews;
    private final FamilyService family;
    private final OrderActionAccess actionAccess;

    public ReviewController(ReviewRepository reviews, FamilyService family, OrderActionAccess actionAccess) {
        this.reviews = reviews;
        this.family = family;
        this.actionAccess = actionAccess;
    }

    public record ReviewRequest(@Min(1) @Max(5) int stars, @Size(max = 1000) String content) { }
    public record ModerateRequest(@NotBlank @Size(max = 300) String reason) { }

    @GetMapping("/orders/{orderId}/review")
    @PreAuthorize("hasRole('USER')")
    public Map<String, Object> review(@PathVariable long orderId, Authentication auth) {
        Map<String, Object> order = order(orderId, false);
        family.requirePermission(Long.parseLong(auth.getName()),
                ((Number) order.get("elder_id")).longValue(), PermissionColumn.VIEW_SERVICE);
        Map<String, Object> review = reviews.byOrder(orderId);
        if (review == null) throw new ApiException(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "尚未评价");
        return review;
    }

    @PostMapping("/orders/{orderId}/review")
    @PreAuthorize("hasRole('USER')")
    @Transactional
    public void create(@PathVariable long orderId, @Valid @RequestBody ReviewRequest input, Authentication auth) {
        Map<String, Object> order = order(orderId, true);
        long actor = Long.parseLong(auth.getName());
        actionAccess.require(actor, order, "REVIEW");
        if (!"COMPLETED".equals(order.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_STATE_CONFLICT", "仅正常确认完成的订单可以评价");
        }
        try {
            reviews.insert(orderId, actor, input.stars(), input.content() == null ? null : input.content().trim());
        } catch (DuplicateKeyException duplicate) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_EXISTS", "该订单已评价");
        }
    }

    @GetMapping("/admin/reviews")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResult<Map<String, Object>> adminReviews(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        PageQuery query = PageQuery.of(page, size);
        return PageResult.of(reviews.adminPage(query.size(), query.offset()), query, reviews.adminCount());
    }

    @PostMapping("/admin/reviews/{id}/hide")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void hide(@PathVariable long id, @Valid @RequestBody ModerateRequest input, Authentication auth) {
        if (reviews.hide(id, Long.parseLong(auth.getName()), input.reason().trim()) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_STATE_CONFLICT", "评价不存在或已隐藏");
        }
    }

    private Map<String, Object> order(long id, boolean lock) {
        Map<String, Object> order = reviews.order(id, lock);
        if (order == null) throw new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "订单不存在");
        return order;
    }
}
