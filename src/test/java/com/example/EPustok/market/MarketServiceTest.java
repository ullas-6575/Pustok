package com.example.EPustok.market;
import com.example.EPustok.identity.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class MarketServiceTest {
@Test void approvalGrantsSellerAndCannotBeRepeated(){
var users=mock(UserRepository.class);var profiles=mock(SellerProfiles.class);
var user=new User("Buyer","buyer@example.com","hash");var profile=new SellerProfile(1L,"Shop","0123","Dhaka");
when(users.findById(1L)).thenReturn(Optional.of(user));when(profiles.findById(1L)).thenReturn(Optional.of(profile));
var service=new MarketService(users,profiles);service.decide(1,true);
assertEquals("SELLER",user.getRole());assertEquals("APPROVED",profile.getStatus());
assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.decide(1,false));
}
@Test void rejectionKeepsBuyerAccess(){
var users=mock(UserRepository.class);var profiles=mock(SellerProfiles.class);var user=new User("Buyer","b@example.com","hash");var profile=new SellerProfile(1L,"Shop","0123","Dhaka");
when(users.findById(1L)).thenReturn(Optional.of(user));when(profiles.findById(1L)).thenReturn(Optional.of(profile));new MarketService(users,profiles).decide(1,false);
assertEquals("BUYER",user.getRole());assertEquals("REJECTED",profile.getStatus());
}
}
