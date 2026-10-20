package cn.eldercare.worker;

import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class WorkerAdminController {
    private final WorkerAdminService service;
    public WorkerAdminController(WorkerAdminService service) { this.service = service; }
    public record WorkerRequest(@NotBlank @Size(min = 4, max = 32) String username,
                                @NotBlank @Size(max = 80) String name,
                                @NotBlank String password,
                                @Size(max = 254) String email) { }
    public record CapabilityRequest(@NotNull Long itemId, @NotNull Long streetId) { }
    public record ScheduleRequest(int weekday, @NotNull LocalTime startTime, @NotNull LocalTime endTime) { }
    public record AcceptingRequest(boolean accepting) { }
    public record LeaveRequest(@NotNull LocalDateTime startAt,@NotNull LocalDateTime endAt,@NotBlank @Size(max=300) String reason) { }
    public record WorkerStateRequest(@NotBlank @Size(max=300) String reason) { }

    @PostMapping("/admin/workers")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Long> create(@Valid @RequestBody WorkerRequest input, Authentication auth) {
        return Map.of("id", service.createWorker(Long.parseLong(auth.getName()), input.username(), input.name(), input.password(), input.email()));
    }

    @GetMapping("/admin/workers")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> workers() { return service.workers(); }

    @PostMapping("/admin/workers/{workerId}/capabilities")
    @PreAuthorize("hasRole('ADMIN')")
    public void capability(@PathVariable long workerId, @Valid @RequestBody CapabilityRequest input) {
        service.capability(workerId, input.itemId(), input.streetId());
    }

    @PostMapping("/admin/workers/{workerId}/schedules")
    @PreAuthorize("hasRole('ADMIN')")
    public void schedule(@PathVariable long workerId, @Valid @RequestBody ScheduleRequest input) {
        service.schedule(workerId, input.weekday(), input.startTime(), input.endTime());
    }

    @GetMapping("/admin/workers/{workerId}/capabilities")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> capabilities(@PathVariable long workerId) { return service.capabilities(workerId); }

    @GetMapping("/admin/workers/{workerId}/schedules")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> schedules(@PathVariable long workerId) { return service.schedules(workerId); }

    @PostMapping("/worker/accepting")
    @PreAuthorize("hasRole('WORKER')")
    public void accepting(@Valid @RequestBody AcceptingRequest input, Authentication auth) {
        service.toggleAccepting(Long.parseLong(auth.getName()), input.accepting());
    }

    @GetMapping("/worker/tasks")
    @PreAuthorize("hasRole('WORKER')")
    public List<Map<String, Object>> tasks(Authentication auth) { return service.tasks(Long.parseLong(auth.getName())); }

    @GetMapping("/worker/tasks/{orderId}")
    @PreAuthorize("hasRole('WORKER')")
    public Map<String,Object> task(@PathVariable long orderId,Authentication auth) { return service.task(Long.parseLong(auth.getName()),orderId); }

    @PostMapping("/worker/leaves")
    @PreAuthorize("hasRole('WORKER')")
    public void requestLeave(@Valid @RequestBody LeaveRequest input,Authentication auth){service.requestLeave(Long.parseLong(auth.getName()),input.startAt(),input.endAt(),input.reason());}

    @GetMapping("/worker/leaves")
    @PreAuthorize("hasRole('WORKER')")
    public List<Map<String,Object>> workerLeaves(Authentication auth){return service.workerLeaves(Long.parseLong(auth.getName()));}

    @GetMapping("/admin/leaves")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String,Object>> adminLeaves(){return service.adminLeaves();}

    @PostMapping("/admin/leaves/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public void approveLeave(@PathVariable long id,Authentication auth){service.decideLeave(Long.parseLong(auth.getName()),id,true);}

    @PostMapping("/admin/leaves/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public void rejectLeave(@PathVariable long id,Authentication auth){service.decideLeave(Long.parseLong(auth.getName()),id,false);}

    @PostMapping("/admin/workers/{workerId}/{action:pause|disable|emergency-disable|enable}")
    @PreAuthorize("hasRole('ADMIN')")
    public void state(@PathVariable long workerId,@PathVariable String action,@Valid @RequestBody WorkerStateRequest input,Authentication auth){
        service.adminWorkerState(Long.parseLong(auth.getName()),workerId,action.toUpperCase(java.util.Locale.ROOT).replace('-','_'),input.reason());
    }
}
