package com.davidwang456.excel.controller;

import com.davidwang456.excel.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import com.davidwang456.excel.service.AuditLogService;

@Controller
public class PageController {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private AuditLogService auditLogService;

    @GetMapping("/")
    public String root() {
        return "redirect:/home";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/home")
    public String index(HttpSession session) {
        if (session.getAttribute("user") == null) {
            return "redirect:/login";
        }
        return "layout";
    }

    @PostMapping("/login")
    @ResponseBody
    public Map<String, Object> doLogin(@RequestBody Map<String, String> loginForm, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        String username = loginForm.get("username");
        String password = loginForm.get("password");

        if (userService.validateUser(username, password)) {
            session.setAttribute("user", username);
            result.put("success", true);
            auditLogService.logAudit(AuditLogService.ACTION_LOGIN, username);
        } else {
            result.put("success", false);
            result.put("message", "用户名或密码错误");
        }
        return result;
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        String username = (String) session.getAttribute("user");
        session.invalidate();
        if (username != null) {
            auditLogService.logAudit(AuditLogService.ACTION_LOGOUT, username);
        }
        return "redirect:/login";
    }
}