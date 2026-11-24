package com.example.EPustok.market;
import com.example.EPustok.identity.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
class MarketServiceTest {
@Test void approvalGrantsSellerAndCannotBeRepeated(){
var users=mock(UserRepository.class);var profiles=mock(SellerProfiles.class);var listings=mock(ResellListings.class);
var user=new User("Buyer","buyer@example.com","hash");var profile=new SellerProfile(1L,"Shop","0123","Dhaka");
when(users.findById(1L)).thenReturn(Optional.of(user));when(profiles.findById(1L)).thenReturn(Optional.of(profile));
var service=new MarketService(users,profiles,listings,mock(ExchangeOffers.class));service.decide(1,true);
assertEquals("SELLER",user.getRole());assertEquals("APPROVED",profile.getStatus());
assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.decide(1,false));
}
@Test void rejectionKeepsBuyerAccess(){
var users=mock(UserRepository.class);var profiles=mock(SellerProfiles.class);var listings=mock(ResellListings.class);var user=new User("Buyer","b@example.com","hash");var profile=new SellerProfile(1L,"Shop","0123","Dhaka");
when(users.findById(1L)).thenReturn(Optional.of(user));when(profiles.findById(1L)).thenReturn(Optional.of(profile));new MarketService(users,profiles,listings,mock(ExchangeOffers.class)).decide(1,false);
assertEquals("BUYER",user.getRole());assertEquals("REJECTED",profile.getStatus());
}

// ---- resell listing ownership, state and order rules ----
private static final long OWNER = 2L, OTHER = 3L, ADMIN = 9L, LISTING = 5L;

private static <T> T withId(T target, String field, long id) {
    try {
        var f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, id);
        return target;
    } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
}

private static User user(long id, String email, String role) {
    User u = withId(new User("U", email, "hash"), "id", id);
    if (!role.equals("BUYER")) u.assignRole(Role.valueOf(role));
    return u;
}

private static ResellListing listing(String type) {
    return new ResellListing(OWNER, type, "Clean Code", "Robert Martin", "GOOD",
            "RESELL".equals(type) ? new BigDecimal("350.00") : null, "RESELL".equals(type) ? null : "Atomic Habits", "Hardcover");
}

private record Fixture(UserRepository users, SellerProfiles profiles, ResellListings listings, MarketService service) {}

private Fixture fixture(ResellListing listing) {
    var users = mock(UserRepository.class);
    var listings = mock(ResellListings.class);
    when(users.findByEmailIgnoreCase("owner@example.com")).thenReturn(Optional.of(user(OWNER, "owner@example.com", "SELLER")));
    when(users.findByEmailIgnoreCase("other@example.com")).thenReturn(Optional.of(user(OTHER, "other@example.com", "BUYER")));
    when(users.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(user(ADMIN, "admin@example.com", "ADMIN")));
    when(listings.findById(LISTING)).thenReturn(Optional.ofNullable(listing));
    return new Fixture(users, mock(SellerProfiles.class), listings, new MarketService(users, mock(SellerProfiles.class), listings, mock(ExchangeOffers.class)));
}

private static void assertStatus(HttpStatus expected, org.junit.jupiter.api.function.Executable action) {
    var ex = assertThrows(ResponseStatusException.class, action);
    assertEquals(expected, ex.getStatusCode());
}

@Test void ownerClosesOwnAvailableListing() {
    var f = fixture(listing("RESELL"));
    when(f.listings().closeAvailable(LISTING, OWNER)).thenReturn(1);
    f.service().closeResell("owner@example.com", LISTING);
    verify(f.listings()).closeAvailable(LISTING, OWNER);
}

@Test void closingAnAlreadyClosedListingReturnsConflict() {
    var f = fixture(listing("RESELL"));
    when(f.listings().closeAvailable(LISTING, OWNER)).thenReturn(0);
    assertStatus(HttpStatus.CONFLICT, () -> f.service().closeResell("owner@example.com", LISTING));
}

@Test void otherUserCannotCloseListing() {
    var f = fixture(listing("RESELL"));
    assertStatus(HttpStatus.FORBIDDEN, () -> f.service().closeResell("other@example.com", LISTING));
    verify(f.listings(), never()).closeAvailable(anyLong(), anyLong());
}

@Test void closingUnknownListingIsNotFound() {
    var f = fixture(null);
    assertStatus(HttpStatus.NOT_FOUND, () -> f.service().closeResell("owner@example.com", LISTING));
}

@Test void adminCanCloseAnyListing() {
    var f = fixture(listing("RESELL"));
    when(f.listings().closeAvailable(LISTING, OWNER)).thenReturn(1);
    f.service().closeResell("admin@example.com", LISTING);
    verify(f.listings()).closeAvailable(LISTING, OWNER);
}

@Test void ownerEditsPriceConditionAndNotesOfAvailableResellListing() {
    var f = fixture(listing("RESELL"));
    when(f.listings().updateAvailable(LISTING, OWNER, "LIKE_NEW", new BigDecimal("280.00"), "Signed copy")).thenReturn(1);
    f.service().updateResell("owner@example.com", LISTING, "LIKE_NEW", new BigDecimal("280.00"), "Signed copy");
    verify(f.listings()).updateAvailable(LISTING, OWNER, "LIKE_NEW", new BigDecimal("280.00"), "Signed copy");
}

@Test void editingClosedListingReturnsConflict() {
    var f = fixture(listing("RESELL"));
    when(f.listings().updateAvailable(eq(LISTING), eq(OWNER), any(), any(), any())).thenReturn(0);
    assertStatus(HttpStatus.CONFLICT, () -> f.service().updateResell("owner@example.com", LISTING, "GOOD", new BigDecimal("300.00"), ""));
}

@Test void otherUserAndAdminCannotEditListing() {
    var f = fixture(listing("RESELL"));
    assertStatus(HttpStatus.FORBIDDEN, () -> f.service().updateResell("other@example.com", LISTING, "GOOD", new BigDecimal("300.00"), ""));
    assertStatus(HttpStatus.FORBIDDEN, () -> f.service().updateResell("admin@example.com", LISTING, "GOOD", new BigDecimal("300.00"), ""));
    verify(f.listings(), never()).updateAvailable(anyLong(), anyLong(), any(), any(), any());
}

@Test void resellEditRejectsZeroPriceWithoutSaving() {
    var f = fixture(listing("RESELL"));
    assertStatus(HttpStatus.BAD_REQUEST, () -> f.service().updateResell("owner@example.com", LISTING, "GOOD", BigDecimal.ZERO, ""));
    verify(f.listings(), never()).updateAvailable(anyLong(), anyLong(), any(), any(), any());
}

@Test void ownerDeletesAvailableListingNotInOrder() {
    var f = fixture(listing("RESELL"));
    when(f.listings().isInOrder(LISTING)).thenReturn(false);
    when(f.listings().deleteAvailableNotOrdered(LISTING, OWNER)).thenReturn(1);
    f.service().deleteResell("owner@example.com", LISTING);
    verify(f.listings()).deleteAvailableNotOrdered(LISTING, OWNER);
}

@Test void listingThatIsInAnOrderCannotBeDeleted() {
    var f = fixture(listing("RESELL"));
    when(f.listings().isInOrder(LISTING)).thenReturn(true);
    assertStatus(HttpStatus.CONFLICT, () -> f.service().deleteResell("owner@example.com", LISTING));
    verify(f.listings(), never()).deleteAvailableNotOrdered(anyLong(), anyLong());
}

@Test void deleteOfListingThatChangedStateIsConflict() {
    var f = fixture(listing("RESELL"));
    when(f.listings().isInOrder(LISTING)).thenReturn(false);
    when(f.listings().deleteAvailableNotOrdered(LISTING, OWNER)).thenReturn(0);
    assertStatus(HttpStatus.CONFLICT, () -> f.service().deleteResell("owner@example.com", LISTING));
}

@Test void otherUserCannotDeleteListing() {
    var f = fixture(listing("RESELL"));
    assertStatus(HttpStatus.FORBIDDEN, () -> f.service().deleteResell("other@example.com", LISTING));
    verify(f.listings(), never()).deleteAvailableNotOrdered(anyLong(), anyLong());
}

@Test void adminCanDeleteAnyListingNotInOrder() {
    var f = fixture(listing("EXCHANGE"));
    when(f.listings().isInOrder(LISTING)).thenReturn(false);
    when(f.listings().deleteAvailableNotOrdered(LISTING, OWNER)).thenReturn(1);
    f.service().deleteResell("admin@example.com", LISTING);
    verify(f.listings()).deleteAvailableNotOrdered(LISTING, OWNER);
}

// ---- MKT-6: a double submit that gets past the status check is a 409, not a raw error ----
@Test void duplicateApplicationRaceOnInsertIsConflict(){
var users=mock(UserRepository.class);var profiles=mock(SellerProfiles.class);
var user=new User("Buyer","buyer@example.com","hash");
when(users.findByEmailIgnoreCase("buyer@example.com")).thenReturn(Optional.of(user));
when(profiles.findById(any())).thenReturn(Optional.empty());
when(profiles.saveAndFlush(any(SellerProfile.class))).thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"));
var service=new MarketService(users,profiles,mock(ResellListings.class),mock(ExchangeOffers.class));
var ex=assertThrows(ResponseStatusException.class,()->service.apply("buyer@example.com","Shop","0123","Dhaka"));
assertEquals(HttpStatus.CONFLICT,ex.getStatusCode());
assertEquals("Application already submitted",ex.getReason());
}
}
