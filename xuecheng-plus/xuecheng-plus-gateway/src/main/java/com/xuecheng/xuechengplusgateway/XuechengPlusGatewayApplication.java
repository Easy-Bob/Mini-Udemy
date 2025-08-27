package com.xuecheng.xuechengplusgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class XuechengPlusGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(XuechengPlusGatewayApplication.class, args);
    }

}
