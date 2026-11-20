package com.example.EPustok.order;
import com.example.EPustok.cart.CartService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import java.math.BigDecimal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.validation.BindingResult;
import org.springframework.web.server.ResponseStatusException;
@Controller @RequestMapping("/buyer") public class CheckoutController {
private final CartService cart; private final CheckoutService checkout;
public CheckoutController(CartService cart,CheckoutService checkout){this.cart=cart;this.checkout=checkout;}
public static class Form {
@NotBlank @Size(max=120) public String name;
@NotBlank @Pattern(regexp="[+0-9() -]{7,30}",message="Enter a valid phone number") public String phone;
@NotBlank @Size(max=500) public String address;
@NotNull public UUID requestId=UUID.randomUUID();
public String getName(){return name;}public void setName(String v){name=v;}
public String getPhone(){return phone;}public void setPhone(String v){phone=v;}
public String getAddress(){return address;}public void setAddress(String v){address=v;}
public UUID getRequestId(){return requestId;}public void setRequestId(UUID v){requestId=v;}
}
private void summary(Authentication a,Model m){var items=cart.items(a.getName());m.addAttribute("items",items);m.addAttribute("total",items.stream().map(CartService.Line::getSubtotal).reduce(BigDecimal.ZERO,BigDecimal::add));}
@GetMapping("/checkout") public String page(Authentication a,Model m){m.addAttribute("form",new Form());summary(a,m);return "checkout";}
@PostMapping("/checkout") public String place(Authentication a,@Valid @ModelAttribute("form") Form form,BindingResult errors,Model m){if(!errors.hasErrors()){try{return "redirect:/buyer/orders/"+checkout.place(a.getName(),form.name,form.phone,form.address,form.requestId);}catch(ResponseStatusException e){m.addAttribute("message",e.getReason());}}summary(a,m);return "checkout";}
@GetMapping("/orders/{id}") public String confirmation(Authentication a,@PathVariable UUID id,Model m){m.addAttribute("order",checkout.order(a.getName(),id));m.addAttribute("items",checkout.items(a.getName(),id));return "order-confirmation";}
}
