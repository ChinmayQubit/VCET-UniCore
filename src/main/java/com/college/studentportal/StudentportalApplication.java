package com.college.studentportal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StudentportalApplication {

	public static void main(String[] args) {
		SpringApplication.run(StudentportalApplication.class, args);
	}

}
