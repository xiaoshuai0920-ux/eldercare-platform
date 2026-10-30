package cn.eldercare.orders;

import java.time.LocalDateTime;
import java.util.Map;

import cn.eldercare.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService service;
    public OrderController(OrderService service) { this.service = service; }

    public record CreateRequest(@NotNull Long elderId, @NotNull Long itemId, @NotNull Long streetId,
                                @NotBlank @Size(max = 300) String address, @NotNull LocalDateTime startAt,
                                @Min(1) @Max(1440) int durationMinutes) { }
    public record AssignRequest(@NotNull Long workerId) { }
    public record RejectRequest(@NotBlank @Size(max = 300) String reason) { }

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public Map<String, Long> create(@Valid @RequestBody CreateRequest input,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                    Authentication auth) {
        if (key == null || key.isBlank() || key.length() > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "请提供有效请求标识");
        long id = service.create(actor(auth), new OrderService.CreateOrder(input.elderId(), input.itemId(), input.streetId(), input.address(), input.startAt(), input.durationMinutes()), key);
        return Map.of("id", id);
    }

    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public OrderService.Page<Map<String, Object>> list(@RequestParam(defaultValue = "1") @Min(1) int page,
                                                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                                       @RequestParam(required = false) String group,
                                                       Authentication auth) {
        return service.userOrders(actor(auth), page, size, group);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public Map<String, Object> get(@PathVariable long id, Authentication auth) { return service.userOrder(actor(auth), id); }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('USER')")
    public void cancel(@PathVariable long id, Authentication auth) { service.cancel(actor(auth), id); }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasRole('USER')")
    public void confirm(@PathVariable long id, Authentication auth) { service.confirm(actor(auth), id); }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public void assign(@PathVariable long id, @Valid @RequestBody AssignRequest input, Authentication auth) {
        service.assign(actor(auth), id, input.workerId());
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasRole('WORKER')")
    public void accept(@PathVariable long id, Authentication auth) { service.respondToAssignment(actor(auth), id, true, null); }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('WORKER')")
    public void reject(@PathVariable long id, @Valid @RequestBody RejectRequest input, Authentication auth) {
        service.respondToAssignment(actor(auth), id, false, input.reason());
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('WORKER')")
    public void start(@PathVariable long id, Authentication auth) { service.start(actor(auth), id); }

    @PostMapping("/{id}/submit-completion")
    @PreAuthorize("hasRole('WORKER')")
    public void submit(@PathVariable long id, Authentication auth) { service.submitCompletion(actor(auth), id); }

    private long actor(Authentication auth) { return Long.parseLong(auth.getName()); }
}
