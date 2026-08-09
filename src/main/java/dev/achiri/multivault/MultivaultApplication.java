package dev.achiri.multivault;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class MultivaultApplication {

    public static void main(String[] args) {
        SpringApplication.run(MultivaultApplication.class, args);
    }

}
