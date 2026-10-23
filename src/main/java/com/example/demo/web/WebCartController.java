package com.example.demo.web;

import com.example.demo.exception.ApiException;
import com.example.demo.service.CartService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class WebCartController {
    private final CartService carts;
    public WebCartController(CartService carts) { this.carts=carts; }
    @GetMapping("/cart") public String cart(Model model) { model.addAttribute("cart",carts.get()); return "cart"; }
    @PostMapping("/cart/add") public String add(@RequestParam long productId,@RequestParam(defaultValue="1") int quantity,RedirectAttributes flash) {
        try { carts.put(productId,quantity,true); flash.addFlashAttribute("successMessage","Đã thêm sản phẩm vào giỏ hàng."); }
        catch(ApiException e) { if(e.getStatus()==401) throw e; flash.addFlashAttribute("errorMessage",e.getMessage()); }
        return "redirect:/cart";
    }
    @PostMapping("/cart/{id}/quantity") public String quantity(@PathVariable long id,@RequestParam int quantity,RedirectAttributes flash) {
        try { carts.put(id,quantity,false); flash.addFlashAttribute("successMessage","Đã cập nhật số lượng."); }
        catch(ApiException e) { if(e.getStatus()==401) throw e; flash.addFlashAttribute("errorMessage",e.getMessage()); }
        return "redirect:/cart";
    }
    @PostMapping("/cart/{id}/remove") public String remove(@PathVariable long id,RedirectAttributes flash) {
        carts.delete(id); flash.addFlashAttribute("successMessage","Đã xóa sản phẩm khỏi giỏ."); return "redirect:/cart";
    }
    @PostMapping("/cart/clear") public String clear(RedirectAttributes flash) {
        carts.delete(null); flash.addFlashAttribute("successMessage","Đã xóa toàn bộ giỏ hàng."); return "redirect:/cart";
    }
}
