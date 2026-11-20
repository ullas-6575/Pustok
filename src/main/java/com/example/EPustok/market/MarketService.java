package com.example.EPustok.market;
import com.example.EPustok.identity.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service @Transactional public class MarketService {
private final UserRepository users; private final SellerProfiles profiles;
public MarketService(UserRepository users,SellerProfiles profiles){this.users=users;this.profiles=profiles;}
@PreAuthorize("hasRole('BUYER')") public void apply(String email,String shop,String phone,String address){
User user=users.findByEmailIgnoreCase(email).orElseThrow();
SellerProfile old=profiles.findById(user.getId()).orElse(null);
if(old!=null&&!old.getStatus().equals("REJECTED")) throw new ResponseStatusException(HttpStatus.CONFLICT,"Application already submitted");
if(old!=null) {profiles.delete(old);profiles.flush();}
profiles.save(new SellerProfile(user.getId(),shop,phone,address));
}
@PreAuthorize("hasRole('ADMIN')") public void decide(long id,boolean approve){
SellerProfile profile=profiles.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
if(!profile.getStatus().equals("PENDING")) throw new ResponseStatusException(HttpStatus.CONFLICT,"Application already reviewed");
User user=users.findById(id).orElseThrow();
if(!user.getRole().equals("BUYER")) throw new ResponseStatusException(HttpStatus.CONFLICT,"Applicant must be a buyer");
profile.decide(approve);if(approve)user.assignRole(Role.SELLER);
}
}