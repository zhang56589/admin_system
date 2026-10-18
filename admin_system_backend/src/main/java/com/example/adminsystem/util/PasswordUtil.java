package com.example.adminsystem.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordUtil {
    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /**
     * 加密：明文密码→BCrypt密文
     */
    public static String encrypt(String rawPassword){
        return encoder.encode(rawPassword);
    }

    /**
     * 校验：明文和数据库密文比对
     */
    public static boolean matches(String rawPassword,String encodedPassword){
        return encoder.matches(rawPassword,encodedPassword);
    }
}
