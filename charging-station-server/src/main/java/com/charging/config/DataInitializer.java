package com.charging.config;

import com.charging.entity.Admin;
import com.charging.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 数据初始化器
 * 启动时检查并创建默认管理员账号: admin / admin123
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final AdminService adminService;

    @Override
    public void run(String... args) {
        // 检查是否已存在 admin 账号
        long count = adminService.lambdaQuery()
                .eq(Admin::getUsername, "admin")
                .count();
        if (count == 0) {
            BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
            Admin admin = new Admin();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setRealName("超级管理员");
            admin.setRole(1);
            admin.setStatus(1);
            adminService.save(admin);
            log.info("默认管理员账号创建成功: admin / admin123");
        } else {
            log.info("管理员账号已存在，跳过初始化");
        }
    }
}
