package com.example.adminsystem.util;

import lombok.Data;

@Data
public class Result<T> {
    private Integer code;
    private String msg;
    private T data;

    public static <T> Result<T> success(){
        Result<T> r = new Result<>();
        r.setCode(200);
        r.setMsg("成功");
        return r;
    }

    public static <T> Result<T> success(T data){
        Result<T> r = new Result<>();
        r.setCode(200);
        r.setMsg("成功");
        r.setData(data);
        return r;
    }

    public static <T> Result<T> success(String msg){
        Result<T> r = new Result<>();
        r.setCode(200);
        r.setMsg(msg);
        return r;
    }

    public static <T> Result<T> fail(Integer code,String msg){
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMsg(msg);
        return r;
    }
}
