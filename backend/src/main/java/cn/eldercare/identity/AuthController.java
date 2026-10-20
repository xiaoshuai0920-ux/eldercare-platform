package cn.eldercare.identity;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
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

    public record EmailRequest(@NotBlank @Email String email) { }
    public record RegisterRequest(@NotBlank @Size(max = 80) String name, @NotBlank @Email String email,
                                  @Pattern(regexp = "\\d{6}") String code, @Size(min = 8, max = 72) String password) { }
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) { }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken());
    }

    @PostMapping("/verify-email")
    public AuthService.VerificationResult verification(@Valid @RequestBody EmailRequest request) {
        return service.requestCode(request.email());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> register(@Valid @RequestBody RegisterRequest request) {
        service.register(request.name(), request.email(), request.code(), request.password());
        return Map.of("status", "CREATED");
    }

    @PostMapping("/login")
    public AuthService.SessionUser login(@Valid @RequestBody LoginRequest data, HttpServletRequest request, HttpServletResponse response) {
        return service.login(data.email(), data.password(), request, response);
    }

    @GetMapping("/me")
    public AuthService.SessionUser me(Authentication auth) {
        return service.current(Long.parseLong(auth.getName()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        if (request.getSession(false) != null) request.getSession(false).invalidate();
    }
}
