package cn.eldercare.family;

import java.time.LocalDate;
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
@PreAuthorize("hasRole('USER')")
public class FamilyController {
    private final FamilyService service;
    public FamilyController(FamilyService service) { this.service = service; }

    public record CreateElderRequest(@NotBlank @Size(max = 80) String fullName, LocalDate birthDate, boolean declaresAuthorization) { }
    public record InviteRequest(@NotBlank @Size(max = 32) String username, @NotNull FamilyService.Permissions permissions) { }

    @PostMapping("/elders")
    public FamilyService.ElderView create(@Valid @RequestBody CreateElderRequest input, Authentication auth) {
        return service.createElder(actor(auth), input.fullName(), input.birthDate(), input.declaresAuthorization());
    }

    @GetMapping("/elders")
    public List<FamilyService.ElderView> list(Authentication auth) { return service.listElders(actor(auth)); }

    @GetMapping("/elders/{elderId}")
    public FamilyService.ElderView get(@PathVariable long elderId, Authentication auth) { return service.getElder(actor(auth), elderId); }

    @PostMapping("/elders/{elderId}/invitations")
    public Map<String, Long> invite(@PathVariable long elderId, @Valid @RequestBody InviteRequest input, Authentication auth) {
        return Map.of("id", service.invite(actor(auth), elderId, input.username(), input.permissions()));
    }

    @GetMapping("/family/invitations")
    public List<Map<String, Object>> invitations(Authentication auth) { return service.pendingInvitations(actor(auth)); }

    @PostMapping("/family/invitations/{id}/accept")
    public void accept(@PathVariable long id, Authentication auth) { service.respond(actor(auth), id, true); }

    @PostMapping("/family/invitations/{id}/reject")
    public void reject(@PathVariable long id, Authentication auth) { service.respond(actor(auth), id, false); }

    @GetMapping("/elders/{elderId}/grants")
    public List<Map<String, Object>> grants(@PathVariable long elderId, Authentication auth) { return service.grants(actor(auth), elderId); }

    @GetMapping("/elders/{elderId}/permissions")
    public FamilyService.Permissions permissions(@PathVariable long elderId, Authentication auth) {
        return service.permissions(actor(auth), elderId);
    }

    @PostMapping("/elders/{elderId}/grants/{accountId}/revoke")
    public void revoke(@PathVariable long elderId, @PathVariable long accountId, Authentication auth) {
        service.revoke(actor(auth), elderId, accountId);
    }

    private long actor(Authentication auth) { return Long.parseLong(auth.getName()); }
}
