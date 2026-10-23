package com.example.demo.dto.request;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
/** Setters reject explicit null; omitted properties stay unset for PATCH. */
public class ProfileUpdateRequest {
    @Size(min=1,max=100) private String fullName;
    @Size(min=1,max=30) private String phone;
    @Size(min=1,max=255) private String address;
    @JsonSetter(nulls=Nulls.FAIL) public void setFullName(String value) { fullName=value; }
    @JsonSetter(nulls=Nulls.FAIL) public void setPhone(String value) { phone=value; }
    @JsonSetter(nulls=Nulls.FAIL) public void setAddress(String value) { address=value; }
    public String fullName() { return fullName; }
    public String phone() { return phone; }
    public String address() { return address; }
}
