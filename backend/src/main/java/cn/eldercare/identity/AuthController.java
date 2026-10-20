package cn.eldercare.identity;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service) { this.service = service; }

    public record RegisterRequest(@NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{4,32}$") String username,
                                  @Size(max = 80) String name,
                                  @Size(max = 254) String email,
                                  @NotBlank String password) { }
    public record LoginRequest(@NotBlank String username, @NotBlank String password) { }
    public record PasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) { }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> register(@Valid @RequestBody RegisterRequest request) {
        service.register(request.username(), request.name(), request.email(), request.password());
        return Map.of("status", "CREATED");
    }

    @PostMapping("/login")
    public AuthService.SessionUser login(@Valid @RequestBody LoginRequest data, HttpServletRequest request, HttpServletResponse response) {
        return service.login(data.username(), data.password(), request, response);
    }

    @GetMapping("/me")
    public AuthService.SessionUser me(Authentication auth) {
        return service.current(Long.parseLong(auth.getName()));
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody PasswordRequest input, Authentication auth) {
        service.changePassword(Long.parseLong(auth.getName()), input.currentPassword(), input.newPassword());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        if (request.getSession(false) != null) request.getSession(false).invalidate();
    }
}
