package com.spark.app.web.controller;

import com.spark.app.web.model.User;
import com.spark.app.web.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证控制器 - 登录、注册、登出、状态查询。
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String SESSION_USER_KEY = "currentUser";

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody Map<String, String> body, HttpSession session) {
        String username = body.get("username");
        String password = body.get("password");
        User user = userService.authenticate(username, password);
        if (user == null) {
            return ResponseEntity.status(401).body(errorMap("用户名或密码错误"));
        }
        session.setAttribute(SESSION_USER_KEY, toPublicMap(user));
        return ResponseEntity.ok(toPublicMap(user));
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @RequestBody Map<String, String> body, HttpSession session) {
        String username = body.get("username");
        String password = body.get("password");
        String displayName = body.get("displayName");

        if (username == null || username.trim().length() < 3) {
            return ResponseEntity.badRequest().body(errorMap("用户名至少3个字符"));
        }
        if (password == null || password.length() < 6) {
            return ResponseEntity.badRequest().body(errorMap("密码至少6个字符"));
        }

        User user = userService.register(username, password, displayName);
        if (user == null) {
            return ResponseEntity.badRequest().body(errorMap("用户名已存在"));
        }
        session.setAttribute(SESSION_USER_KEY, toPublicMap(user));
        return ResponseEntity.ok(toPublicMap(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpSession session) {
        session.invalidate();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(HttpSession session) {
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) session.getAttribute(SESSION_USER_KEY);
        if (user == null) {
            return ResponseEntity.status(401).body(errorMap("未登录"));
        }
        return ResponseEntity.ok(user);
    }

    private Map<String, Object> toPublicMap(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("displayName", user.getDisplayName());
        return map;
    }

    private Map<String, Object> errorMap(String message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("error", message);
        return map;
    }
}
