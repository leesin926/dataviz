package com.dataviz.user.dto;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserCreateDTO {
    @NotBlank(message = "Username is required")
    private String username;
    @NotBlank(message = "Password is required")
    private String password;
    private String nickname;
    private String email;
    private String phone;
    private String avatar;
    private Long deptId;
}
