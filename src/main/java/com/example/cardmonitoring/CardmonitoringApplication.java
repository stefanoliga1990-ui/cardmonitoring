package com.example.cardmonitoring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CardmonitoringApplication {

	public static void main(String[] args) {
		SpringApplication.run(CardmonitoringApplication.class, args);
	}

}
