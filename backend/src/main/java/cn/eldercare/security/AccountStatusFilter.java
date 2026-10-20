package cn.eldercare.security;

import java.io.IOException;

import cn.eldercare.identity.AccountEntity;
import cn.eldercare.identity.AccountMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class AccountStatusFilter extends OncePerRequestFilter {
    private final AccountMapper accounts;
    public AccountStatusFilter(AccountMapper accounts) { this.accounts = accounts; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication.getName().matches("\\d+")) {
            AccountEntity account = accounts.selectById(Long.parseLong(authentication.getName()));
            if (account == null || !"ACTIVE".equals(account.getStatus())) {
                SecurityContextHolder.clearContext();
                if (request.getSession(false) != null) request.getSession(false).invalidate();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":\"ACCOUNT_INACTIVE\",\"message\":\"账号已失效\",\"details\":{}}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
