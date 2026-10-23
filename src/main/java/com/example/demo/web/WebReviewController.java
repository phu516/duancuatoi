package com.example.demo.web;

import com.example.demo.dto.request.*;
import com.example.demo.exception.ApiException;
import com.example.demo.service.ReviewService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class WebReviewController {
    private final ReviewService reviews;
    public WebReviewController(ReviewService reviews) { this.reviews=reviews; }
    @PostMapping("/products/{product}/reviews") public String create(@PathVariable long product,@RequestParam int rating,
            @RequestParam(required=false) String comment,RedirectAttributes flash) {
        try { reviews.create(product,new ReviewCreateRequest(rating,comment)); flash.addFlashAttribute("successMessage","Đã đăng đánh giá."); }
        catch(ApiException e) { if(e.getStatus()==401) throw e; flash.addFlashAttribute("errorMessage",e.getMessage()); }
        return "redirect:/products/"+product+"#reviews";
    }
    @PostMapping("/products/{product}/reviews/{review}/update") public String update(@PathVariable long product,
            @PathVariable long review,@RequestParam int rating,@RequestParam(required=false) String comment,RedirectAttributes flash) {
        try { var r=new ReviewUpdateRequest(); r.setRating(rating); r.setComment(comment); reviews.update(product,review,r);
            flash.addFlashAttribute("successMessage","Đã sửa đánh giá."); }
        catch(ApiException e) { if(e.getStatus()==401) throw e; flash.addFlashAttribute("errorMessage",e.getMessage()); }
        return "redirect:/products/"+product+"#reviews";
    }
    @PostMapping("/products/{product}/reviews/{review}/delete") public String delete(@PathVariable long product,
            @PathVariable long review,RedirectAttributes flash) {
        try { reviews.delete(product,review); flash.addFlashAttribute("successMessage","Đã xóa đánh giá."); }
        catch(ApiException e) { if(e.getStatus()==401) throw e; flash.addFlashAttribute("errorMessage",e.getMessage()); }
        return "redirect:/products/"+product+"#reviews";
    }
}
