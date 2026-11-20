package com.example.EPustok.order;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;
@Controller public class OrderTrackingController {
private final OrderTracking orders;
public OrderTrackingController(OrderTracking orders){this.orders=orders;}
@GetMapping("/buyer/orders") public String mine(Authentication a,@RequestParam(defaultValue="0") int page,Model m){m.addAttribute("orders",orders.mine(a.getName(),page));m.addAttribute("adminView",false);m.addAttribute("page",page);return "orders";}
@GetMapping("/admin/orders") public String all(@RequestParam(defaultValue="0") int page,Model m){m.addAttribute("orders",orders.all(page));m.addAttribute("adminView",true);m.addAttribute("page",page);return "orders";}
@GetMapping("/admin/orders/{id}") public String detail(@PathVariable UUID id,Model m){m.addAttribute("order",orders.detail(id));m.addAttribute("items",orders.items(id));m.addAttribute("adminView",true);return "order-confirmation";}
@PostMapping("/admin/orders/{id}/status") public String status(@PathVariable UUID id,@RequestParam OrderTracking.Status status,RedirectAttributes flash){try{orders.advance(id,status);}catch(ResponseStatusException e){flash.addFlashAttribute("message",e.getReason());}return "redirect:/admin/orders/"+id;}
}
