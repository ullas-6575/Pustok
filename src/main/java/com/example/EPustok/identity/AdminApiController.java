package com.example.EPustok.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
public class AdminApiController {
    private final AdminService admin;
    public AdminApiController(AdminService admin) { this.admin=admin; }
    @GetMapping public List<AdminService.AccountView> users(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="50") int size) {
        return admin.accounts(page, size);
    }
    @PatchMapping("/{id}/role")
    public AdminService.AccountView role(@PathVariable long id,@Valid @RequestBody RoleRequest request) { return admin.changeRole(id,request.role()); }
    @PatchMapping("/{id}/enabled")
    public AdminService.AccountView enabled(@PathVariable long id,@Valid @RequestBody EnabledRequest request) { return admin.setEnabled(id,request.enabled()); }
    public record RoleRequest(@NotNull Role role) {}
    public record EnabledRequest(@NotNull Boolean enabled) {}
}
