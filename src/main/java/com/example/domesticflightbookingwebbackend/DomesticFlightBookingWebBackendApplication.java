package com.example.domesticflightbookingwebbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DomesticFlightBookingWebBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(DomesticFlightBookingWebBackendApplication.class, args);
    }

}
