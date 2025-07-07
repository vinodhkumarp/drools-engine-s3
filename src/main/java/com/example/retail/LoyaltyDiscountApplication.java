package com.example.retail;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LoyaltyDiscountApplication {
  public static void main(String[] args) {
    SpringApplication.run(LoyaltyDiscountApplication.class, args);
  }
}
