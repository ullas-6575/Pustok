package com.example.EPustok.market;
import com.example.EPustok.identity.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Seller application, admin review, category and seller book forms. HTML form handlers report failures by flashing
 * the reason to the page they redirect to; they do not return raw error pages.
 */
@Controller public class MarketController {
static final int ADMIN_PAGE_SIZE = 20;
private final SellerProfiles profiles; private final Categories categories; private final Books books; private final UserRepository users; private final MarketService service;
public MarketController(SellerProfiles p,Categories c,Books b,UserRepository u,MarketService s){profiles=p;categories=c;books=b;users=u;service=s;}
private long uid(Authentication a){return users.findByEmailIgnoreCase(a.getName()).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED)).getId();}

private static String failed(RedirectAttributes flash,String redirect,String reason){
String text=reason==null?"The request could not be completed.":reason;
flash.addFlashAttribute("message",text);
flash.addFlashAttribute("error",text);
return "redirect:"+redirect;
}
private static String failed(RedirectAttributes flash,String redirect,ResponseStatusException e){return failed(flash,redirect,e.getReason());}

@GetMapping("/buyer/seller-application") public String application(Authentication a,Model m){m.addAttribute("profile",profiles.findById(uid(a)).orElse(null));return "application";}
public record Application(@NotBlank @Size(max=150) String shopName,@NotBlank @Size(max=30) String phone,@NotBlank @Size(max=500) String address){}
@PostMapping("/buyer/seller-application") public String apply(Authentication a,@Valid @ModelAttribute Application form,BindingResult binding,RedirectAttributes flash){
String back="/buyer/seller-application";
if(binding.hasErrors()) return failed(flash,back,"Enter a shop name (up to 150 characters), a phone number and an address (up to 500 characters).");
try{
service.apply(a.getName(),form.shopName(),form.phone(),form.address());
flash.addFlashAttribute("message","Your application has been submitted for review.");
}catch(ResponseStatusException e){return failed(flash,back,e);}
return "redirect:"+back;
}

@GetMapping("/admin/applications") public String applications(@RequestParam(defaultValue="0") int page,Model m){
Page<SellerProfile> result=profiles.findAll(PageRequest.of(Math.max(page,0),ADMIN_PAGE_SIZE,Sort.by(Sort.Direction.DESC,"id")));
m.addAttribute("profiles",List.copyOf(result.getContent()));
m.addAttribute("currentPage",result.getNumber());
m.addAttribute("totalPages",result.getTotalPages());
m.addAttribute("hasPrevious",result.hasPrevious());
m.addAttribute("hasNext",result.hasNext());
return "applications";
}
@PostMapping("/admin/applications/{id}") public String decide(@PathVariable long id,@RequestParam boolean approve,RedirectAttributes flash){
String back="/admin/applications";
try{
service.decide(id,approve);
flash.addFlashAttribute("message",approve?"Seller application approved.":"Seller application rejected.");
}catch(ResponseStatusException e){return failed(flash,back,e);}
return "redirect:"+back;
}

@GetMapping("/admin/categories") public String categories(Model m){m.addAttribute("categories",categories.findAll());return "categories";}
public record CategoryForm(@NotBlank @Size(max=100) String name){}
/** Creates or renames a category. Names are unique: a duplicate (checked here, backed by the unique column) is a 409 flashed to the page. */
@PostMapping("/admin/categories") public String category(@RequestParam(required=false) Long id,@Valid @ModelAttribute CategoryForm f,BindingResult binding,RedirectAttributes flash){
String back="/admin/categories";
if(binding.hasErrors()) return failed(flash,back,"Category name is required (up to 100 characters).");
try{
String name=f.name().trim();
Category c;
if(id==null){
if(categories.existsByName(name)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Category name already exists");
c=new Category(name);
}else{
c=categories.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Category not found"));
if(categories.existsByNameAndIdNot(name,id)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Category name already exists");
c.rename(name);
}
categories.save(c);
flash.addFlashAttribute("message",id==null?"Category added.":"Category renamed.");
}catch(ResponseStatusException e){return failed(flash,back,e);
}catch(DataIntegrityViolationException e){return failed(flash,back,"Category name already exists");}
return "redirect:"+back;
}
@PostMapping("/admin/categories/{id}/delete") public String deleteCategory(@PathVariable long id,RedirectAttributes flash){
String back="/admin/categories";
try{
if(!categories.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Category not found");
if(books.existsByCategoryId(id)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Category contains books");
categories.deleteById(id);
flash.addFlashAttribute("message","Category deleted.");
}catch(ResponseStatusException e){return failed(flash,back,e);}
return "redirect:"+back;
}

private void approved(long id){if(!profiles.findById(id).map(p->p.getStatus().equals("APPROVED")).orElse(false))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Approved seller profile required");}
@GetMapping("/seller/books") public String seller(Authentication a,Model m){approved(uid(a));m.addAttribute("books",books.findBySellerId(uid(a)));m.addAttribute("categories",categories.findAll());return "seller-books";}
public record BookForm(@NotBlank @Size(max=255) String title,@NotBlank @Size(max=255) String author,@NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal price,@Min(0) int stock,@NotNull Long categoryId){}
private Book owned(long id,long seller){Book b=books.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Book not found"));if(!b.getSellerId().equals(seller))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"You can only change your own books");return b;}
@PostMapping("/seller/books") public String save(Authentication a,@RequestParam(required=false) Long id,@Valid @ModelAttribute BookForm f,BindingResult binding,RedirectAttributes flash){
String back="/seller/books";
if(binding.hasErrors()) return failed(flash,back,"Check the book details: title and author are required (up to 255 characters), the price must be at least 0.01 and stock cannot be negative.");
try{
long seller=uid(a);approved(seller);
Book b=id==null?new Book(seller):owned(id,seller);
b.update(f.title().trim(),f.author().trim(),f.price(),f.stock(),categories.findById(f.categoryId()).orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown category")));
books.save(b);
flash.addFlashAttribute("message",id==null?"Book added to your shelf.":"Book updated.");
}catch(ResponseStatusException e){return failed(flash,back,e);}
return "redirect:"+back;
}
@PostMapping("/seller/books/{id}/delete") public String delete(Authentication a,@PathVariable long id,RedirectAttributes flash){
String back="/seller/books";
try{
long seller=uid(a);approved(seller);
books.delete(owned(id,seller));
flash.addFlashAttribute("message","Book removed from your shelf.");
}catch(ResponseStatusException e){return failed(flash,back,e);}
return "redirect:"+back;
}
}
