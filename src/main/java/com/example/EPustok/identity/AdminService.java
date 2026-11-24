package com.example.EPustok.identity;

import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminService {
    static final int DEFAULT_SIZE = 50;
    static final int MAX_SIZE = 100;
    static final int MAX_PAGE = 10_000;

    private final UserRepository users;
    private final JdbcTemplate jdbc;

    public AdminService(UserRepository users, JdbcTemplate jdbc) {
        this.users = users;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly=true)
    public List<AccountView> accounts(int page, int size) {
        if (page < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page");
        int safePage = Math.min(page, MAX_PAGE);
        int safeSize = Math.max(1, Math.min(MAX_SIZE, size));
        return users.findAll(PageRequest.of(safePage, safeSize, Sort.by("id"))).map(AdminService::view).getContent();
    }

    @Transactional
    public AccountView changeRole(long id, Role role) {
        User user = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.getRole().equals("ADMIN")) throw new ResponseStatusException(HttpStatus.CONFLICT, "Admin accounts cannot be changed here");
        if (user.getRole().equals(role.name())) return view(user);
        if (role == Role.SELLER) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use seller application approval");
        if (role == Role.ADMIN) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin accounts require controlled provisioning");
        boolean demotingSeller = user.getRole().equals("SELLER") && role == Role.BUYER;
        user.assignRole(role);
        if (demotingSeller) {
            // Without this the seller profile stays APPROVED and the account is stuck: MarketService.apply only accepts REJECTED.
            jdbc.update("update seller_profile set status = 'REJECTED', version = version + 1 where id = ? and status = 'APPROVED'", id);
        }
        return view(users.save(user));
    }

    /**
     * Disabling takes effect on the next request, because JwtFilter loads the account on every call
     * and rejects disabled users, so existing tokens stop working without a revocation list entry.
     */
    @Transactional
    public AccountView setEnabled(long id, boolean enabled) {
        User user = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.getRole().equals("ADMIN")) throw new ResponseStatusException(HttpStatus.CONFLICT, "Admin accounts cannot be changed here");
        user.setEnabled(enabled);
        return view(users.save(user));
    }

    private static AccountView view(User user) {
        return new AccountView(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.isEnabled());
    }

    public record AccountView(Long id, String name, String email, String role, boolean enabled) {}
}
