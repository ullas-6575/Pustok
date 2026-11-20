package com.example.EPustok.cart;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
@Controller @RequestMapping("/buyer/cart") public class CartController {
private final CartService cart;
public CartController(CartService cart){this.cart=cart;}
@GetMapping public String view(Authentication a,Model m){var items=cart.items(a.getName());m.addAttribute("items",items);m.addAttribute("total",items.stream().map(CartService.Line::getSubtotal).reduce(BigDecimal.ZERO,BigDecimal::add));return "cart";}
@PostMapping("/add") public String add(Authentication a,@RequestParam long bookId,@RequestParam(defaultValue="1") int quantity,RedirectAttributes flash){try{cart.add(a.getName(),bookId,quantity);flash.addFlashAttribute("message","Book added to cart");}catch(ResponseStatusException e){flash.addFlashAttribute("message",e.getReason());}return "redirect:/buyer/cart";}
@PostMapping("/{bookId}/update") public String update(Authentication a,@PathVariable long bookId,@RequestParam int quantity,RedirectAttributes flash){try{cart.update(a.getName(),bookId,quantity);}catch(ResponseStatusException e){flash.addFlashAttribute("message",e.getReason());}return "redirect:/buyer/cart";}
@PostMapping("/{bookId}/remove") public String remove(Authentication a,@PathVariable long bookId){cart.remove(a.getName(),bookId);return "redirect:/buyer/cart";}
}
