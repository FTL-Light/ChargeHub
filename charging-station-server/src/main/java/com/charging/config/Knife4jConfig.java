package com.charging.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j 接口文档配置
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("新能源充电站管理平台 API 文档")
                        .description("新能源充电站管理平台后端接口文档")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("充电站管理平台")
                                .email("admin@charging.com")));
    }
}
