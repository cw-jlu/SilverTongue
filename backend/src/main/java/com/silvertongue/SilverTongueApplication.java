package com.silvertongue;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SilverTongue (灵舌) - Spring Boot 业务服务入口
 * <p>
 * 负责用户管理、社区交互、练习会话元数据及 gRPC 客户端调用 AI Agent 服务。
 */
@MapperScan("com.silvertongue.user.mapper")
@SpringBootApplication
public class SilverTongueApplication {

    public static void main(String[] args) {
        SpringApplication.run(SilverTongueApplication.class, args);
    }
}
