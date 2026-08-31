package com.swiftpay.gateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI swiftPayGatewayOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("SwiftPay Transaction Gateway API")
                .description("REST API for initiating P2P payments")
                .version("1.0.0"));
    }
}
