package com.capstone.deepterview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class DeepterviewApplication {
	public static void main(String[] args) {
		SpringApplication.run(DeepterviewApplication.class, args);
	}

}
