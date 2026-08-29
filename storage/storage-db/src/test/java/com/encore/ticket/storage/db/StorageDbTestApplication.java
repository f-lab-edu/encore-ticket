package com.encore.ticket.storage.db;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication
public class StorageDbTestApplication {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
