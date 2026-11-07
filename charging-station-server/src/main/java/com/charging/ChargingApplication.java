package com.charging;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 充电站管理平台启动类
 */
@SpringBootApplication
@MapperScan("com.charging.mapper")
// 订单超时兜底补偿任务依赖定时任务：延迟消息可能因 MQ 宕机、消息丢失而失效，
// 扫描数据库的兜底任务才是最后一道防线
@EnableScheduling
public class ChargingApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChargingApplication.class, args);
        System.out.println("====================================");
        System.out.println("充电站管理平台后端服务启动成功!");
        System.out.println("接口文档地址: http://localhost:8080/api/doc.html");
        System.out.println("Github:FTL-Light");
        System.out.println("====================================");
    }
}
