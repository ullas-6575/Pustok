package com.example.EPustok.identity;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RoleController {
    private final AdminService admin;
    public RoleController(AdminService admin) { this.admin=admin; }
    @GetMapping("/admin")
    public String admin(@RequestParam(defaultValue="0") int page, Model model) {
        model.addAttribute("accounts", admin.accounts(page, AdminService.DEFAULT_SIZE));
        model.addAttribute("page", Math.min(page, AdminService.MAX_PAGE));
        return "admin";
    }
    @PostMapping("/admin/users/{id}/role")
    public String role(@PathVariable long id, @RequestParam Role role, RedirectAttributes redirect) {
        admin.changeRole(id, role);
        redirect.addFlashAttribute("message", "Account role updated.");
        return "redirect:/admin";
    }
    @PostMapping("/admin/users/{id}/enabled")
    public String enabled(@PathVariable long id, @RequestParam boolean enabled, RedirectAttributes redirect) {
        admin.setEnabled(id, enabled);
        redirect.addFlashAttribute("message", enabled ? "Account enabled." : "Account disabled.");
        return "redirect:/admin";
    }
    @GetMapping("/seller")
    public String seller(Model model) { return "redirect:/seller/books"; }
    @GetMapping("/buyer")
    public String buyer(Model model) { model.addAttribute("role","BUYER"); return "workspace"; }
}
