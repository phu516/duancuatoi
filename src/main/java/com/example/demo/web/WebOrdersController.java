package com.example.demo.web;

import com.example.demo.dto.request.OrderCancellationRequest;
import com.example.demo.dto.request.ShippingUpdateRequest;
import com.example.demo.exception.ApiException;
import com.example.demo.security.CurrentUser;
import com.example.demo.service.OrderService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.*;
import static com.example.demo.web.StorefrontController.*;

@Controller
public class WebOrdersController {
    private final OrderService orders; private final CurrentUser current;
    public WebOrdersController(OrderService orders,CurrentUser current) { this.orders=orders; this.current=current; }
    @GetMapping("/orders") public String list(@RequestParam(defaultValue="0") int page,@RequestParam(required=false) String status,
            @RequestParam(required=false) String paymentStatus,@RequestParam(required=false) String dateFrom,
            @RequestParam(required=false) String dateTo,Model model) {
        var q=query("page",Integer.toString(page),"size","10","status",status,"payment_status",paymentStatus,"date_from",dateFrom,"date_to",dateTo);
        if(current.staff()) q.set("user_id",current.require().id().toString());
        var result=orders.list(q); model.addAttribute("result",result); model.addAttribute("selectedStatus",status);
        model.addAttribute("selectedPaymentStatus",paymentStatus); model.addAttribute("dateFrom",dateFrom); model.addAttribute("dateTo",dateTo);
        q.remove("user_id"); pagination(model,"/orders",q,result.pagination()); return "orders";
    }
    private Map<String,Object> own(long id) {
        var order=orders.detail(id);
        if(((Number)order.get("user_id")).longValue()!=current.require().id()) throw ApiException.missing(); return order;
    }
    @GetMapping("/orders/{id}") public String detail(@PathVariable long id,Model model) {
        model.addAttribute("order",own(id)); model.addAttribute("events",orders.events(id,query("size","100")).data()); return "order";
    }
    @PostMapping("/orders/{id}/cancel") public String cancel(@PathVariable long id,@RequestParam String reason,RedirectAttributes flash) {
        own(id);
        try { orders.cancel(id,new OrderCancellationRequest(reason)); flash.addFlashAttribute("successMessage","Đơn đã được hủy."); }
        catch(ApiException e) { if(e.getStatus()==401) throw e; flash.addFlashAttribute("errorMessage",e.getMessage()); }
        return "redirect:/orders/"+id;
    }
    @PostMapping("/orders/{id}/shipping") public String shipping(@PathVariable long id,@RequestParam String recipientName,
            @RequestParam String recipientPhone,@RequestParam(required=false) String address,
            @RequestParam(required=false) String note,RedirectAttributes flash) {
        own(id);
        try { var r=new ShippingUpdateRequest(); r.setRecipientName(recipientName); r.setRecipientPhone(recipientPhone);
            r.setAddress(address); r.setNote(note); orders.updateShipping(id,r);
            flash.addFlashAttribute("successMessage","Đã cập nhật giao nhận."); }
        catch(ApiException e) { if(e.getStatus()==401) throw e; flash.addFlashAttribute("errorMessage",e.getMessage()); }
        return "redirect:/orders/"+id;
    }
}
