package com.example.adminsystem.interceptor;

import com.example.adminsystem.annotation.RequireAdmin;
import com.example.adminsystem.annotation.RequireRole;
import com.example.adminsystem.enums.UserRoleEnum;
import com.example.adminsystem.util.JwtUtil;
import com.example.adminsystem.util.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

@Component
public class TokenInterceptor implements HandlerInterceptor {

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        System.out.println("进入TokenInterceptor，uri=" + request.getRequestURI() + " method=" + request.getMethod());

        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }

        //登录注册放行
        if(request.getRequestURI().contains("/api/login") || request.getRequestURI().contains("/api/register")){
            System.out.println("放行登录/注册接口");
            return true;
        }

        String token = request.getHeader("token");
        System.out.println("获取token=" + token);

        if(token == null || token.isBlank()){
            writeFail(response, 401, "未登录，请携带token");
            return false;
        }

        try {
            Claims claims = jwtUtil.parseToken(token);
            Object userIdObj = claims.get("userId");
            Object roleObj = claims.get("role");
            // username 用于操作日志记录操作人
            Object usernameObj = claims.get("username");

            if(userIdObj == null || roleObj == null){
                System.out.println("token内userId/role为空");
                writeFail(response, 401, "token无效");
                return false;
            }
            //存入 Long / Integer / String，不存字符串化的原类型
            request.setAttribute("userId", Long.valueOf(userIdObj.toString()));
            request.setAttribute("role", Integer.valueOf(roleObj.toString()));
            if(usernameObj != null){
                request.setAttribute("username", usernameObj.toString());
            }
        }catch (Exception e){
            System.out.println("token解析异常");
            e.printStackTrace();
            writeFail(response, 401, "token无效或已过期");
            return false;
        }

        return checkAdmin(request, response, handler);
    }

    private boolean checkAdmin(HttpServletRequest request,
                               HttpServletResponse response,
                               Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        String denyMessage = resolveAdminDenyMessage(handlerMethod);
        if (denyMessage == null) {
            return true;
        }
        Object roleObj = request.getAttribute("role");
        if (UserRoleEnum.ADMIN.getCode().equals(roleObj)) {
            return true;
        }
        writeFail(response, 403, denyMessage);
        return false;
    }

    private String resolveAdminDenyMessage(HandlerMethod handlerMethod) {
        RequireAdmin requireAdmin = handlerMethod.getMethodAnnotation(RequireAdmin.class);
        if (requireAdmin == null) {
            requireAdmin = handlerMethod.getBeanType().getAnnotation(RequireAdmin.class);
        }
        if (requireAdmin != null) {
            return requireAdmin.message();
        }
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        return requireRole == null ? null : requireRole.message();
    }

    private void writeFail(HttpServletResponse response, int code, String msg) throws Exception {
        response.setStatus(code);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Result.fail(code, msg));
    }
}
