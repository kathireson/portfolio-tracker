package com.homelab.portfolio.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // *** DEVELOPMENT CHANGE ***: Allow all origins for local testing.
        // *** PRODUCTION CHANGE ***: Change to "https://your-duckdns-ip" only.
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:8080", "http://localhost:8080") // <-- Change this for production
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
