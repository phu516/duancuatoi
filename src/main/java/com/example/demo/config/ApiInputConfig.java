package com.example.demo.config;
import com.example.demo.exception.ApiException;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.Arrays;
import java.util.Map;

@Configuration
public class ApiInputConfig implements WebMvcConfigurer {
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
                if(handler instanceof HandlerMethod method && !request.getParameterMap().isEmpty()) {
                    boolean acceptsQuery=Arrays.stream(method.getMethodParameters()).anyMatch(p ->
                        p.hasParameterAnnotation(RequestParam.class) && Map.class.isAssignableFrom(p.getParameterType()));
                    if(!acceptsQuery) throw ApiException.invalid("API này không nhận query parameter");
                }
                return true;
            }
        }).addPathPatterns("/api/v1/**");
    }
}

