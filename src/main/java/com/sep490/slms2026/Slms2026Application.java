package com.sep490.slms2026;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

@SpringBootApplication
@EntityScan(basePackages = "com.sep490.slms2026.entity")
public class Slms2026Application {
	public static void main(String[] args) {
		SpringApplication.run(Slms2026Application.class, args);
	}

}
