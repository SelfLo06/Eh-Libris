package com.libris.eh_libris;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.libris.eh_libris.dao") // 核心：让程序启动时扫描到你的 Mapper 接口
public class EhLibrisApplication {

    public static void main(String[] args) {
        SpringApplication.run(EhLibrisApplication.class, args);
    }

}