package com.example.EPustok.order;
import com.example.EPustok.cart.CartService;
import com.example.EPustok.payment.PaymentGateway;
import com.example.EPustok.payment.PaymentGatewayFactory;
import com.example.EPustok.payment.PaymentRecord;
import com.example.EPustok.payment.PaymentService;
import com.example.EPustok.payment.PaymentStatus;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller @RequestMapping("/buyer") public class CheckoutController {
private final CartService cart; private final CheckoutService checkout; private final PaymentService payments; private final PaymentGatewayFactory gateways;
public CheckoutController(CartService cart,CheckoutService checkout,PaymentService payments,PaymentGatewayFactory gateways){this.cart=cart;this.checkout=checkout;this.payments=payments;this.gateways=gateways;}
public static class Form {
@NotBlank @Size(max=120) public String name;
@NotBlank @Pattern(regexp="[+0-9() -]{7,30}",message="Enter a valid phone number") public String phone;
@NotBlank @Size(max=500) public String address;
@NotNull public UUID requestId=UUID.randomUUID();
/** Payment method key: COD, BKASH, NAGAD or MOCK. Missing or blank means COD. Validated in place(), not by annotation. */
public String gateway="COD";
/** Optional test card number for the MOCK gateway. Type DECLINE to simulate a failed payment. */
public String testCard;
public String getName(){return name;}public void setName(String v){name=v;}
public String getPhone(){return phone;}public void setPhone(String v){phone=v;}
public String getAddress(){return address;}public void setAddress(String v){address=v;}
public UUID getRequestId(){return requestId;}public void setRequestId(UUID v){requestId=v;}
public String getGateway(){return gateway;}public void setGateway(String v){gateway=v;}
public String getTestCard(){return testCard;}public void setTestCard(String v){testCard=v;}
}
private void summary(Authentication a,Model m){var items=cart.items(a.getName());m.addAttribute("items",items);m.addAttribute("total",items.stream().map(CartService.Line::getSubtotal).reduce(BigDecimal.ZERO,BigDecimal::add));m.addAttribute("mockPaymentsEnabled",gateways.isMockEnabled());}
@GetMapping("/checkout") public String page(Authentication a,Model m){m.addAttribute("form",new Form());summary(a,m);return "checkout";}
/**
 * Places the order first, then charges it in the same request. Nothing is charged if the order was not created.
 * expectedTotal is the total the buyer saw; CheckoutService rejects the order if the cart total changed.
 */
@PostMapping("/checkout") public String place(Authentication a,@Valid @ModelAttribute("form") Form form,BindingResult errors,@RequestParam(required=false) BigDecimal expectedTotal,Model m,RedirectAttributes flash){
if(form.gateway==null||form.gateway.isBlank()) form.gateway="COD";
if(!errors.hasErrors()){
PaymentGateway gateway;
try{gateway=gateways.select(form.gateway);}catch(IllegalArgumentException|ResponseStatusException e){m.addAttribute("message","Choose a valid payment method.");summary(a,m);return "checkout";}
try{
CheckoutService.PlaceResult placed=checkout.place(a.getName(),form.name,form.phone,form.address,form.requestId,expectedTotal);
if(placed.replayed()){
// The same checkout was submitted again. The order already exists, so it is not charged again.
flash.addFlashAttribute("message","This order was already placed.");
}else{
String message=chargeNewOrder(a.getName(),placed.orderId(),gateway,form.testCard);
if(message!=null) flash.addFlashAttribute("message",message);
}
return "redirect:/buyer/orders/"+placed.orderId();
}catch(ResponseStatusException e){m.addAttribute("message",e.getReason());}
}
summary(a,m);return "checkout";}

/**
 * Charges the order this checkout just created with a fresh server-generated key. A replayed checkout (same
 * requestId) already has a payment row, so it is not charged again. Returns the message for the buyer, or null.
 */
private String chargeNewOrder(String email,UUID orderId,PaymentGateway gateway,String testCard){
if(payments.payment(email,orderId).isPresent()) return null;
String card=testCard==null||testCard.isBlank()?null:testCard.trim();
try{
return messageFor(gateway.name(),payments.pay(email,orderId,gateway.name(),UUID.randomUUID().toString(),card).status());
}catch(ResponseStatusException e){
// A concurrent replay of this checkout paid first. The confirmation page shows that payment.
return null;
}}

private static String messageFor(String gateway,PaymentStatus status){
return switch(status){
case COMPLETED -> "Payment completed. Your order is now processing.";
case FAILED -> "Payment failed. You can retry below.";
case PENDING -> "COD".equals(gateway) ? "Order placed. You will pay cash on delivery." : "Payment is pending confirmation. Your order stays placed until it is confirmed.";
};}

static String paymentLabel(PaymentRecord p){
if(p==null) return "Payment: not recorded yet";
if("COD".equals(p.gateway())) return "Payment: Cash on delivery";
String method=switch(p.gateway()){case "MOCK"->"Test card";case "BKASH"->"bKash (sandbox)";case "NAGAD"->"Nagad (sandbox)";default->p.gateway();};
String state=switch(p.status()){case PENDING->"pending confirmation";case COMPLETED->"completed";case FAILED->"failed";};
return "Payment: "+method+", "+state;}

/**
 * Buyer-facing status line under the heading. Null when no payment row exists: the pay form then explains
 * what to do, and the admin view has no payment row at all.
 */
static String paymentNotice(PaymentRecord p){
if(p==null) return null;
if("COD".equals(p.gateway())) return p.status()==PaymentStatus.PENDING
        ? "Payment is due on delivery. Follow your order status below."
        : "Follow your order status below.";
return switch(p.status()){
case PENDING -> "Payment is awaiting confirmation. Follow your order status below.";
case COMPLETED -> "Payment received. Follow your order status below.";
case FAILED -> "Payment was not completed.";
};}

@GetMapping("/orders/{id}") public String confirmation(Authentication a,@PathVariable UUID id,Model m){PaymentRecord payment=payments.payment(a.getName(),id).orElse(null);m.addAttribute("order",checkout.order(a.getName(),id));m.addAttribute("items",checkout.items(a.getName(),id));m.addAttribute("payment",payment);m.addAttribute("paymentLabel",paymentLabel(payment));m.addAttribute("paymentNotice",paymentNotice(payment));m.addAttribute("payKey",UUID.randomUUID().toString());m.addAttribute("mockPaymentsEnabled",gateways.isMockEnabled());return "order-confirmation";}
}
