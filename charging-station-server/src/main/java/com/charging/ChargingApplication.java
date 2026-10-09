package com.charging;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 充电站管理平台启动类
 */
@SpringBootApplication
@MapperScan("com.charging.mapper")
public class ChargingApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChargingApplication.class, args);
        System.out.println("====================================");
        System.out.println("充电站管理平台后端服务启动成功!");
        System.out.println("接口文档地址: http://localhost:8080/api/doc.html");
        System.out.println("====================================");
    }
}
