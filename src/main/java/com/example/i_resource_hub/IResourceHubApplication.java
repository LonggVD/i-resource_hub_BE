package com.example.i_resource_hub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class IResourceHubApplication {

	public static void main(String[] args) {
		SpringApplication.run(IResourceHubApplication.class, args);
	}

}
