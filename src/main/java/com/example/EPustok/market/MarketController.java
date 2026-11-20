package com.example.EPustok.market;
import com.example.EPustok.identity.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Controller public class MarketController {
private final SellerProfiles profiles; private final Categories categories; private final Books books; private final UserRepository users; private final MarketService service;
public MarketController(SellerProfiles p,Categories c,Books b,UserRepository u,MarketService s){profiles=p;categories=c;books=b;users=u;service=s;}
private long uid(Authentication a){return users.findByEmailIgnoreCase(a.getName()).orElseThrow().getId();}
@GetMapping("/buyer/seller-application") public String application(Authentication a,Model m){m.addAttribute("profile",profiles.findById(uid(a)).orElse(null));return "application";}
public record Application(@NotBlank @Size(max=150) String shopName,@NotBlank @Size(max=30) String phone,@NotBlank @Size(max=500) String address){}
@PostMapping("/buyer/seller-application") public String apply(Authentication a,@Valid @ModelAttribute Application form){service.apply(a.getName(),form.shopName(),form.phone(),form.address());return "redirect:/buyer/seller-application";}
@GetMapping("/admin/applications") public String applications(Model m){m.addAttribute("profiles",profiles.findAll());return "applications";}
@PostMapping("/admin/applications/{id}") public String decide(@PathVariable long id,@RequestParam boolean approve){service.decide(id,approve);return "redirect:/admin/applications";}
@GetMapping("/admin/categories") public String categories(Model m){m.addAttribute("categories",categories.findAll());return "categories";}
public record CategoryForm(@NotBlank @Size(max=100) String name){}
@PostMapping("/admin/categories") public String category(@RequestParam(required=false) Long id,@Valid @ModelAttribute CategoryForm f){Category c=id==null?new Category(f.name().trim()):categories.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));c.rename(f.name().trim());categories.save(c);return "redirect:/admin/categories";}
@PostMapping("/admin/categories/{id}/delete") public String deleteCategory(@PathVariable long id){if(books.existsByCategoryId(id))throw new ResponseStatusException(HttpStatus.CONFLICT,"Category contains books");categories.deleteById(id);return "redirect:/admin/categories";}
private void approved(long id){if(!profiles.findById(id).map(p->p.getStatus().equals("APPROVED")).orElse(false))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Approved seller profile required");}
@GetMapping("/seller/books") public String seller(Authentication a,Model m){approved(uid(a));m.addAttribute("books",books.findBySellerId(uid(a)));m.addAttribute("categories",categories.findAll());return "seller-books";}
public record BookForm(@NotBlank @Size(max=255) String title,@NotBlank @Size(max=255) String author,@NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal price,@Min(0) int stock,@NotNull Long categoryId){}
private Book owned(long id,long seller){Book b=books.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));if(!b.getSellerId().equals(seller))throw new ResponseStatusException(HttpStatus.FORBIDDEN);return b;}
@PostMapping("/seller/books") public String save(Authentication a,@RequestParam(required=false) Long id,@Valid @ModelAttribute BookForm f){long seller=uid(a);approved(seller);Book b=id==null?new Book(seller):owned(id,seller);b.update(f.title().trim(),f.author().trim(),f.price(),f.stock(),categories.findById(f.categoryId()).orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown category")));books.save(b);return "redirect:/seller/books";}
@PostMapping("/seller/books/{id}/delete") public String delete(Authentication a,@PathVariable long id){approved(uid(a));books.delete(owned(id,uid(a)));return "redirect:/seller/books";}
}
