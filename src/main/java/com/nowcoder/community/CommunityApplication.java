package com.nowcoder.community;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CommunityApplication {

	//SpringBoot通过main方法启动SpringApplication类的静态方法run()来启动项目
	public static void main(String[] args) {
		SpringApplication.run(CommunityApplication.class, args);
	}

}
