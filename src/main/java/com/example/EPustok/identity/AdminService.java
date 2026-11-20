package com.example.EPustok.identity;

import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminService {
    private final UserRepository users;
    public AdminService(UserRepository users) { this.users=users; }
    @Transactional(readOnly=true)
    public List<AccountView> accounts(int page) {
        if(page<0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid page");
        return users.findAll(PageRequest.of(page,50,Sort.by("id"))).map(AdminService::view).getContent();
    }
    @Transactional
    public AccountView changeRole(long id, Role role) {
        if(role==Role.SELLER) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use seller application approval");
        if(role==Role.ADMIN) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Admin accounts require controlled provisioning");
        User user=users.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found"));
        if(user.getRole().equals("ADMIN")) throw new ResponseStatusException(HttpStatus.CONFLICT,"Admin accounts cannot be changed here");
        user.assignRole(role);
        return view(users.save(user));
    }
    private static AccountView view(User user) { return new AccountView(user.getId(),user.getName(),user.getEmail(),user.getRole()); }
    public record AccountView(Long id,String name,String email,String role) {}
}
