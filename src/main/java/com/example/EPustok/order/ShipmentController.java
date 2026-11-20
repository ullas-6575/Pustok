package com.example.EPustok.order;
import java.util.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;
@Controller public class ShipmentController {
private final ShipmentService service;
public ShipmentController(ShipmentService service){this.service=service;}
@GetMapping("/seller/orders") public String mine(Authentication a,@RequestParam(defaultValue="0") int page,Model m){var orders=service.mine(a.getName(),page);Map<Object,Object> items=new HashMap<>();for(var order:orders)items.put(order.get("id"),service.items(a.getName(),(UUID)order.get("id")));m.addAttribute("orders",orders);m.addAttribute("shipmentItems",items);m.addAttribute("page",page);return "seller-orders";}
@PostMapping("/seller/orders/{id}/status") public String update(Authentication a,@PathVariable UUID id,@RequestParam OrderTracking.Status status,RedirectAttributes flash){try{service.advance(a.getName(),id,status);}catch(ResponseStatusException e){flash.addFlashAttribute("message",e.getReason());}return "redirect:/seller/orders";}
@PostMapping("/admin/orders/{id}/shipments/{seller}/status") public String admin(@PathVariable UUID id,@PathVariable long seller,@RequestParam OrderTracking.Status status,RedirectAttributes flash){try{service.adminAdvance(id,seller,status);}catch(ResponseStatusException e){flash.addFlashAttribute("message",e.getReason());}return "redirect:/admin/orders/"+id;}
}
