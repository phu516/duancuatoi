package com.example.demo.exception;
import com.example.demo.dto.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.ConcurrencyFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log=LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private Object error(int status,String code,String message,HttpServletRequest req) {
        if(!req.getRequestURI().startsWith(req.getContextPath()+"/api/")) {
            var view=new org.springframework.web.servlet.ModelAndView("error",java.util.Map.of("status",status,"message",message));
            view.setStatus(HttpStatus.valueOf(status)); return view;
        }
        return ResponseEntity.status(status).body(ApiErrorResponse.of(code,message,(String)req.getAttribute("trace_id")));
    }
    @ExceptionHandler(ApiException.class)
    Object api(ApiException e,HttpServletRequest r,jakarta.servlet.http.HttpServletResponse response) {
        if(e instanceof RateLimitException limited) response.setHeader("Retry-After",Long.toString(limited.getRetryAfterSeconds()));
        return error(e.getStatus(),e.getCode(),e.getMessage(),r);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    Object validation(MethodArgumentNotValidException e,HttpServletRequest r) {
        var fields=e.getBindingResult().getFieldErrors().stream().map(f ->
            new ApiErrorResponse.FieldError(f.getField().replaceAll("([a-z])([A-Z])","$1_$2").toLowerCase(java.util.Locale.ROOT),f.getDefaultMessage())).toList();
        return ResponseEntity.badRequest().body(new ApiErrorResponse(new ApiErrorResponse.ErrorDetail(
            "VALIDATION_ERROR","Dữ liệu không hợp lệ",fields,(String)r.getAttribute("trace_id"))));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    Object json(HttpMessageNotReadableException e,HttpServletRequest r) { return error(400,"INVALID_JSON","JSON sai kiểu, thiếu body hoặc có trường không được hỗ trợ",r); }
    @ExceptionHandler({MethodArgumentTypeMismatchException.class,ConstraintViolationException.class})
    Object invalid(Exception e,HttpServletRequest r) { return error(400,"VALIDATION_ERROR","Tham số không hợp lệ",r); }
    @ExceptionHandler(AccessDeniedException.class)
    Object denied(Exception e,HttpServletRequest r) { return error(403,"FORBIDDEN","Không đủ quyền",r); }
    @ExceptionHandler(NoResourceFoundException.class)
    Object missing(Exception e,HttpServletRequest r) { return error(404,"NOT_FOUND","Không tìm thấy API",r); }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    Object method(HttpRequestMethodNotSupportedException e,HttpServletRequest r) {
        if(!r.getRequestURI().startsWith(r.getContextPath()+"/api/")) return error(405,"METHOD_NOT_ALLOWED","Thao tác không được hỗ trợ",r);
        return ResponseEntity.status(405).headers(e.getHeaders()).body(ApiErrorResponse.of("METHOD_NOT_ALLOWED","Method không được hỗ trợ",(String)r.getAttribute("trace_id")));
    }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    Object media(Exception e,HttpServletRequest r) { return error(415,"UNSUPPORTED_MEDIA_TYPE","Cần Content-Type: application/json",r); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    Object integrity(Exception e,HttpServletRequest r) { return error(409,"RESOURCE_IN_USE","Dữ liệu trùng hoặc còn liên kết",r); }
    @ExceptionHandler(ConcurrencyFailureException.class)
    Object conflict(Exception e,HttpServletRequest r) { return error(409,"CONCURRENT_UPDATE","Dữ liệu đang được cập nhật; hãy thử lại",r); }
    @ExceptionHandler(Exception.class)
    Object unexpected(Exception e,HttpServletRequest r) {
        log.error("Unhandled API error trace_id={}",r.getAttribute("trace_id"),e);
        return error(500,"INTERNAL_ERROR","Lỗi hệ thống",r);
    }
}
