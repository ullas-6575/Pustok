package com.example.EPustok.market;
import com.example.EPustok.identity.*;
import java.math.BigDecimal;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service @Transactional public class MarketService {
private final UserRepository users; private final SellerProfiles profiles; private final ResellListings listings; private final ExchangeOffers offers;
public MarketService(UserRepository users,SellerProfiles profiles,ResellListings listings,ExchangeOffers offers){this.users=users;this.profiles=profiles;this.listings=listings;this.offers=offers;}

@PreAuthorize("hasRole('BUYER')") public void apply(String email,String shop,String phone,String address){
User user=users.findByEmailIgnoreCase(email).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));
SellerProfile old=profiles.findById(user.getId()).orElse(null);
if(old!=null&&!old.getStatus().equals("REJECTED")) throw new ResponseStatusException(HttpStatus.CONFLICT,"Application already submitted");
if(old!=null) {profiles.delete(old);profiles.flush();}
try{
profiles.saveAndFlush(new SellerProfile(user.getId(),shop,phone,address));
}catch(DataIntegrityViolationException e){
// Two submits raced past the check above; the primary key on seller_profile lets only one insert succeed.
throw new ResponseStatusException(HttpStatus.CONFLICT,"Application already submitted");
}
}
@PreAuthorize("hasRole('ADMIN')") public void decide(long id,boolean approve){
SellerProfile profile=profiles.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
if(!profile.getStatus().equals("PENDING")) throw new ResponseStatusException(HttpStatus.CONFLICT,"Application already reviewed");
User user=users.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Applicant not found"));
if(!user.getRole().equals("BUYER")) throw new ResponseStatusException(HttpStatus.CONFLICT,"Applicant must be a buyer");
profile.decide(approve);if(approve)user.assignRole(Role.SELLER);
}

/**
 * Owner closes an AVAILABLE listing. Admin may close any listing. Missing listing 404, other user 403.
 * Closing an EXCHANGE listing also rejects its PROPOSED offers in the same transaction, so no offer stays open on a
 * listing that is no longer available. The guarded close runs first: if it affects no rows, nothing else is written.
 */
public void closeResell(String email,long id){
ResellListing listing=loadResell(email,id,true);
boolean exchange="EXCHANGE".equals(listing.getListingType());
if(listings.closeAvailable(id,listing.getUserId())!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"This listing is already closed");
if(exchange) offers.rejectAllProposed(id,ExchangeState.PROPOSED,ExchangeState.REJECTED);
}

/** Owner edits price, condition and notes of an AVAILABLE listing. Only the owner may edit, not admin. */
public void updateResell(String email,long id,String condition,BigDecimal price,String description){
ResellListing listing=loadResell(email,id,false);
boolean resell="RESELL".equals(listing.getListingType());
if(resell&&(price==null||price.signum()<=0)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A resell listing needs a price above zero");
if(listings.updateAvailable(id,listing.getUserId(),condition,resell?price:null,description)!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"This listing is closed and can no longer be edited");
}

/** Owner removes an AVAILABLE listing that is not part of an order. Admin may remove any listing. */
public void deleteResell(String email,long id){
ResellListing listing=loadResell(email,id,true);
if(listings.isInOrder(id)) throw new ResponseStatusException(HttpStatus.CONFLICT,"This listing belongs to an order and cannot be deleted");
if(listings.deleteAvailableNotOrdered(id,listing.getUserId())!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"Only an available listing can be deleted");
}

/**
 * Signed-in user offers their book on an EXCHANGE listing they do not own. Only one PROPOSED offer per offerer per
 * listing; the partial unique index (V11) backs this up against a race. The listing row is locked first, so the
 * AVAILABLE check cannot interleave with an accept or close on the same listing.
 */
public ExchangeOffer makeExchangeOffer(String email,long listingId,String title,String author){
String offeredTitle=title==null?"":title.trim();String offeredAuthor=author==null?"":author.trim();
if(offeredTitle.isEmpty()||offeredAuthor.isEmpty()||offeredTitle.length()>255||offeredAuthor.length()>255) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Offered title and author are required (up to 255 characters each)");
User offerer=currentUser(email);
ResellListing listing=listings.findByIdForUpdate(listingId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Listing not found"));
if(!"EXCHANGE".equals(listing.getListingType())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Offers are only for exchange listings");
if(listing.getUserId().equals(offerer.getId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"You cannot make an offer on your own listing");
if(!"AVAILABLE".equals(listing.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT,"This listing is closed");
if(offers.existsByListingIdAndOffererIdAndStatus(listingId,offerer.getId(),ExchangeState.PROPOSED)) throw new ResponseStatusException(HttpStatus.CONFLICT,"You already have an open offer on this listing");
try{
return offers.saveAndFlush(new ExchangeOffer(listingId,offerer.getId(),offeredTitle,offeredAuthor));
}catch(DataIntegrityViolationException e){
throw new ResponseStatusException(HttpStatus.CONFLICT,"You already have an open offer on this listing");
}
}

/**
 * Listing owner accepts a PROPOSED offer. In this one transaction: close the listing, accept the offer and reject
 * every other PROPOSED offer on the listing. Closing goes first because the listing row serializes concurrent
 * accepts: a second accept waits here, then finds the listing CLOSED and gets 409.
 */
public void acceptExchangeOffer(String email,long offerId){
User actor=currentUser(email);
ExchangeOffer offer=findOffer(offerId);
ResellListing listing=listingOf(offer);
if(!listing.getUserId().equals(actor.getId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only the listing owner can accept an offer");
requireTransition(offer.getStatus(),ExchangeState.ACCEPTED);
long listingId=offer.getListingId();
if(listings.closeAvailable(listingId,listing.getUserId())!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"This listing has already been closed");
if(offers.transition(offerId,ExchangeState.PROPOSED,ExchangeState.ACCEPTED)!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"This offer has already been decided");
offers.rejectOtherProposed(listingId,offerId,ExchangeState.PROPOSED,ExchangeState.REJECTED);
}

/** Listing owner rejects a PROPOSED offer. */
public void rejectExchangeOffer(String email,long offerId){
User actor=currentUser(email);
ExchangeOffer offer=findOffer(offerId);
ResellListing listing=listingOf(offer);
if(!listing.getUserId().equals(actor.getId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only the listing owner can reject an offer");
requireTransition(offer.getStatus(),ExchangeState.REJECTED);
if(offers.transition(offerId,ExchangeState.PROPOSED,ExchangeState.REJECTED)!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"This offer has already been decided");
}

/** Offerer withdraws their own PROPOSED offer. */
public void withdrawExchangeOffer(String email,long offerId){
User actor=currentUser(email);
ExchangeOffer offer=findOffer(offerId);
if(!offer.getOffererId().equals(actor.getId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only the offerer can withdraw an offer");
requireTransition(offer.getStatus(),ExchangeState.WITHDRAWN);
if(offers.transition(offerId,ExchangeState.PROPOSED,ExchangeState.WITHDRAWN)!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"This offer has already been decided");
}

private User currentUser(String email){
return users.findByEmailIgnoreCase(email).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));
}

private ExchangeOffer findOffer(long offerId){
return offers.findById(offerId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Offer not found"));
}

private ResellListing listingOf(ExchangeOffer offer){
return listings.findById(offer.getListingId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Listing not found"));
}

private static void requireTransition(ExchangeState from,ExchangeState to){
if(!from.canTransitionTo(to)) throw new ResponseStatusException(HttpStatus.CONFLICT,"An offer that is "+from.name().toLowerCase()+" cannot be "+to.name().toLowerCase());
}

private ResellListing loadResell(String email,long id,boolean adminAllowed){
User user=users.findByEmailIgnoreCase(email).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));
ResellListing listing=listings.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
boolean owner=listing.getUserId().equals(user.getId());
if(!owner&&!(adminAllowed&&"ADMIN".equals(user.getRole()))) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
return listing;
}
}
