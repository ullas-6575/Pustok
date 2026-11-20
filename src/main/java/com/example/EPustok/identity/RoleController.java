package com.example.EPustok.identity;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class RoleController {
    private final AdminService admin;
    public RoleController(AdminService admin) { this.admin=admin; }
    @GetMapping("/admin")
    public String admin(@RequestParam(defaultValue="0") int page, Model model) {
        model.addAttribute("accounts",admin.accounts(page)); model.addAttribute("page",page); return "admin";
    }
    @PostMapping("/admin/users/{id}/role")
    public String role(@PathVariable long id,@RequestParam Role role) { admin.changeRole(id,role); return "redirect:/admin"; }
    @GetMapping("/seller")
    public String seller(Model model) { return "redirect:/seller/books"; }
    @GetMapping("/buyer")
    public String buyer(Model model) { model.addAttribute("role","BUYER"); return "workspace"; }
}
