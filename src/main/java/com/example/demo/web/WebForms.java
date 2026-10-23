package com.example.demo.web;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

public final class WebForms {
    private WebForms() {}
    @Getter @Setter
    public static class Profile {
        @NotBlank @Size(max=100) private String fullName;
        @Size(max=30) private String phone;
        @Size(max=255) private String address;
    }
    @Getter @Setter
    public static class Password {
        @NotBlank private String oldPassword;
        @NotBlank @Size(min=8,max=72) private String newPassword;
        @NotBlank private String passwordConfirmation;
    }
    @Getter @Setter
    public static class Login {
        @NotBlank(message="Vui lòng nhập email") @Email(message="Email không hợp lệ") @Size(max=100) private String email;
        @NotBlank(message="Vui lòng nhập mật khẩu") @Size(max=1024) private String password;
    }
    @Getter @Setter
    public static class Registration {
        @NotBlank(message="Vui lòng nhập họ tên") @Size(max=100) private String fullName;
        @NotBlank(message="Vui lòng nhập email") @Email(message="Email không hợp lệ") @Size(max=100) private String email;
        @NotBlank(message="Vui lòng nhập số điện thoại") @Pattern(regexp="(0|\\+84)[0-9]{9,10}",message="Số điện thoại không hợp lệ") private String phone;
        @NotBlank(message="Vui lòng nhập địa chỉ") @Size(max=255) private String address;
        @NotBlank @Size(min=8,max=72,message="Mật khẩu cần ít nhất 8 ký tự") private String password;
        @NotBlank(message="Vui lòng xác nhận mật khẩu") private String passwordConfirmation;
    }
    @Getter @Setter
    public static class Checkout {
        @NotBlank(message="Vui lòng nhập họ tên người nhận") @Size(max=100,message="Họ tên người nhận không được quá 100 ký tự") private String recipientName;
        @NotBlank(message="Vui lòng nhập số điện thoại") @Pattern(regexp="(?:\\s*|(?:0|\\+84)[0-9]{9,10})",message="Số điện thoại không hợp lệ") private String recipientPhone;
        @Size(max=255,message="Địa chỉ nhận hàng không được quá 255 ký tự") private String address;
        @Size(max=255) private String note;
        @NotBlank(message="Vui lòng chọn hình thức nhận hàng") private String deliveryMethod="pickup";
        @Size(max=50) private String couponCode;
    }
}
