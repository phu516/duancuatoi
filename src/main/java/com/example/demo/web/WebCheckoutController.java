package com.example.demo.web;

import com.example.demo.dto.request.*;
import com.example.demo.exception.ApiException;
import com.example.demo.security.CurrentUser;
import com.example.demo.service.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;
import java.util.*;

@Controller
public class WebCheckoutController {
    private final CartService carts; private final CheckoutService checkout; private final OrderService orders; private final UserService users; private final CurrentUser current;
    private record Draft(long owner,OrderCreateRequest request,BigDecimal total) {}
    public WebCheckoutController(CartService carts,CheckoutService checkout,OrderService orders,UserService users,CurrentUser current) {
        this.carts=carts; this.checkout=checkout; this.orders=orders; this.users=users; this.current=current;
    }
    @GetMapping("/checkout") public String checkout(Model model) {
        var user=users.me(); var f=new WebForms.Checkout(); f.setRecipientName(user.fullName()); f.setRecipientPhone(user.phone()); f.setAddress(user.address());
        model.addAttribute("form",f); model.addAttribute("cart",carts.get()); return "checkout";
    }
    @PostMapping("/checkout/preview") public String preview(@Valid @ModelAttribute("form") WebForms.Checkout f,BindingResult errors,
            HttpSession session,HttpServletResponse response,Model model) {
        model.addAttribute("cart",carts.get());
        if(!errors.hasFieldErrors("deliveryMethod") && !"pickup".equals(f.getDeliveryMethod()) && !"pet_taxi".equals(f.getDeliveryMethod()))
            errors.rejectValue("deliveryMethod","invalid","Vui lòng chọn một hình thức nhận hàng hợp lệ");
        if("pet_taxi".equals(f.getDeliveryMethod()) && (f.getAddress()==null || f.getAddress().isBlank()) && !errors.hasFieldErrors("address"))
            errors.rejectValue("address","required","Vui lòng nhập địa chỉ nhận hàng khi chọn vận chuyển thú cưng");
        if(errors.hasErrors()) { response.setStatus(400); return "checkout"; }
        var r=new OrderCreateRequest();
        r.setItems(carts.lines(current.require().id()));
        r.setShipping(new ShippingCreateRequest(f.getRecipientName(),f.getRecipientPhone(),f.getDeliveryMethod(),f.getAddress(),f.getNote()));
        r.setCouponCode(f.getCouponCode()); r.setPaymentMethod("cash");
        try {
            var quote=checkout.preview(r); model.addAttribute("quote",quote);
            if(Boolean.TRUE.equals(quote.get("can_place_order"))) {
                String key="web_"+UUID.randomUUID().toString().replace("-","");
                synchronized(session) {
                    var drafts=drafts(session);
                    while(drafts.size()>=5) drafts.remove(drafts.keySet().iterator().next());
                    drafts.put(key,new Draft(current.require().id(),r,(BigDecimal)quote.get("total_amount")));
                }
                model.addAttribute("checkoutKey",key);
            }
            return "checkout";
        } catch(ApiException e) { response.setStatus(e.getStatus()); model.addAttribute("errorMessage",e.getMessage()); return "checkout"; }
    }
    @SuppressWarnings("unchecked") private Map<String,Draft> drafts(HttpSession session) {
        var value=(Map<String,Draft>)session.getAttribute("checkoutDrafts");
        if(value==null) { value=new LinkedHashMap<>(); session.setAttribute("checkoutDrafts",value); } return value;
    }
    @PostMapping("/checkout/place") public String place(@RequestParam String checkoutKey,HttpSession session,RedirectAttributes flash) {
        Draft draft; synchronized(session) { draft=drafts(session).get(checkoutKey); }
        if(draft==null || draft.owner()!=current.require().id()) {
            flash.addFlashAttribute("errorMessage","Phiên xác nhận đã hết hiệu lực. Vui lòng kiểm tra lại đơn hàng."); return "redirect:/checkout";
        }
        try {
            var result=orders.create(checkoutKey,draft.request(),draft.total());
            flash.addFlashAttribute("successMessage",result.replayed()?"Đơn hàng này đã được ghi nhận trước đó.":"Đặt hàng thành công! Cửa hàng đã nhận được đơn của bạn.");
            return "redirect:/orders/"+result.body().get("id");
        } catch(ApiException e) {
            if(e.getStatus()==401 || e.getStatus()==429) throw e;
            flash.addFlashAttribute("errorMessage",e.getMessage()); return "redirect:/checkout";
        }
    }
}
