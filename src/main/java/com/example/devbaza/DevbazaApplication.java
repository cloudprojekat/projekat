package com.example.devbaza;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.devbaza.security.RateLimiterService;

@SpringBootApplication
@EnableScheduling
public class DevbazaApplication {

    private static final Logger log = LoggerFactory.getLogger(DevbazaApplication.class);

    @Autowired
    private RateLimiterService rateLimiterService;

    public static void main(String[] args) {
        SpringApplication.run(DevbazaApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * Čišćenje rate limiter memorije svakih sat vremena.
     * Briše SAMO stare buckete (neaktivne 2+ sata) — ne SVE.
     * Loguje broj praćenih IP-ova za monitoring.
     */
    @Scheduled(fixedRate = 3_600_000)
    public void ocistiRateLimiter() {
        int pre = rateLimiterService.getBrojPracenihIpova();
        rateLimiterService.ocistiStareBuckete();
        int posle = rateLimiterService.getBrojPracenihIpova();
        log.info("RateLimiter cleanup: {} → {} aktivnih IP-ova", pre, posle);
    }
}