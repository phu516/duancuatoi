package com.example.demo.config;
import com.example.demo.exception.ApiException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import java.io.*;
import java.lang.reflect.Type;

/** Bound reads even when a request uses chunked transfer without Content-Length. */
@ControllerAdvice
public class JsonBodyLimitAdvice extends RequestBodyAdviceAdapter {
    private static final int LIMIT=64*1024;
    @Override public boolean supports(MethodParameter parameter,Type type,Class<? extends HttpMessageConverter<?>> converter) { return true; }
    @Override public HttpInputMessage beforeBodyRead(HttpInputMessage input,MethodParameter parameter,Type type,
        Class<? extends HttpMessageConverter<?>> converter) throws IOException {
        if(input.getHeaders().getContentLength()>LIMIT) throw tooLarge();
        byte[] body=input.getBody().readNBytes(LIMIT+1);
        if(body.length>LIMIT) throw tooLarge();
        return new HttpInputMessage() {
            public InputStream getBody() { return new ByteArrayInputStream(body); }
            public HttpHeaders getHeaders() { return input.getHeaders(); }
        };
    }
    private ApiException tooLarge() { return new ApiException(413,"PAYLOAD_TOO_LARGE","Request tối đa 64 KiB"); }
}

