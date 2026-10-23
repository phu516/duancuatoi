package com.example.demo.web;

import com.example.demo.dto.response.PageResponse;
import com.example.demo.exception.ApiException;
import com.example.demo.service.*;
import com.example.demo.security.CurrentUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.*;

@Controller
public class StorefrontController {
    private final ProductService products; private final PetService pets; private final ReviewService reviews; private final StorefrontData data; private final CurrentUser current;
    public StorefrontController(ProductService products,PetService pets,ReviewService reviews,StorefrontData data,CurrentUser current) {
        this.products=products; this.pets=pets; this.reviews=reviews; this.data=data; this.current=current;
    }
    static MultiValueMap<String,String> query(String... entries) {
        var map=new LinkedMultiValueMap<String,String>();
        for(int i=0;i<entries.length;i+=2) if(entries[i+1]!=null && !entries[i+1].isBlank()) map.set(entries[i],entries[i+1].trim());
        return map;
    }
    static void pagination(Model model,String path,MultiValueMap<String,String> query,PageResponse.Pagination page) {
        var previous=new LinkedMultiValueMap<>(query); previous.set("page",Integer.toString(Math.max(0,page.page()-1)));
        var next=new LinkedMultiValueMap<>(query); next.set("page",Integer.toString(page.page()+1));
        model.addAttribute("previousUrl",UriComponentsBuilder.fromPath(path).queryParams(previous).build().encode().toUriString());
        model.addAttribute("nextUrl",UriComponentsBuilder.fromPath(path).queryParams(next).build().encode().toUriString());
        model.addAttribute("pagination",page);
    }
    @GetMapping("/") public String home(Model model) {
        model.addAttribute("pets",products.list(query("product_type","pet","size","4")).data());
        model.addAttribute("essentials",products.list(query("product_type","food","size","4")).data());
        model.addAttribute("testimonials",data.testimonials()); return "home";
    }
    @GetMapping("/products") public String products(@RequestParam Map<String,String> params,Model model) {
        var q=new LinkedMultiValueMap<String,String>();
        for(String key:List.of("keyword","category_id","product_type","min_price","max_price","sort","page")) {
            String value=params.get(key); if(value!=null && !value.isBlank()) q.set(key,value.trim());
        }
        q.set("size","12"); var page=products.list(q);
        model.addAttribute("result",page); model.addAttribute("filters",q.toSingleValueMap());
        pagination(model,"/products",q,page.pagination()); return "products";
    }
    @GetMapping("/pets") public String pets(@RequestParam Map<String,String> params,Model model) {
        var q=new LinkedMultiValueMap<String,String>();
        for(String key:List.of("status","breed","supplier_id","page")) {
            String value=params.get(key); if(value!=null && !value.isBlank()) q.set(key,value.trim());
        }
        q.set("size","12"); var page=pets.list(q);
        model.addAttribute("result",page); model.addAttribute("filters",q.toSingleValueMap());
        pagination(model,"/pets",q,page.pagination()); return "pets";
    }
    @GetMapping("/products/{id}") public String product(@PathVariable long id,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="newest") String sort,Model model) {
        var product=products.detail(id); if(!product.isActive()) throw ApiException.missing();
        model.addAttribute("product",product);
        var reviewPage=reviews.list(id,query("size","5","page",Integer.toString(page),"sort",sort));
        model.addAttribute("reviews",reviewPage.data()); model.addAttribute("reviewSort",sort);
        pagination(model,"/products/"+id,query("sort",sort),reviewPage.pagination());
        if(current.principalOrNull()!=null) {
            model.addAttribute("myReviewId",reviews.mine(id));
            model.addAttribute("myReview",reviews.mineDetail(id));
            model.addAttribute("canReview",reviews.canReview());
        }
        model.addAttribute("health",product.pet()==null?List.of():pets.healthList(product.pet().id(),query("size","100")).data());
        return "product";
    }
}
