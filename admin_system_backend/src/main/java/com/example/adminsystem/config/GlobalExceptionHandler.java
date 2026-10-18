package com.example.adminsystem.config;

import com.example.adminsystem.util.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Set;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 捕获 @Valid + @RequestBody JSON请求体校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<?> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = (fieldError != null) ? fieldError.getDefaultMessage() : "参数校验失败";
        log.warn("JSON请求体参数校验异常：{}", msg);
        return Result.fail(400, msg);
    }

    /**
     * 捕获 @Valid form‑data表单校验异常
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<?> handleBindException(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = (fieldError != null) ? fieldError.getDefaultMessage() : "参数校验失败";
        log.warn("参数校验异常：{}", msg);
        return Result.fail(400, msg);
    }

    /**
     * 捕获 @RequestParam简单参数校验
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<?> handleConstraintViolation(ConstraintViolationException e) {
        Set<ConstraintViolation<?>> violations = e.getConstraintViolations();
        String msg = violations.iterator().next().getMessage();
        log.warn("参数校验异常：{}", msg);
        return Result.fail(400, msg);
    }

    /**
     * 捕获404接口不存在
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<?> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("访问不存在接口：{}", e.getMessage());
        return Result.fail(404, "接口不存在");
    }

    /**
     * 兜底捕获全部未知异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<?> handleAllException(Exception e) {
        log.error("系统发生未知异常", e);
        return Result.fail(500, "服务器内部错误，请联系管理员");
    }
}
