package com.example.devbaza.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtFilter jwtFilter;

    @Value("${app.cors.allowed-origin:http://localhost,http://localhost:5500,http://127.0.0.1:5500,http://127.0.0.1}")
    private String allowedOrigin;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(ct -> {})
                        .xssProtection(xss -> {})
                        .referrerPolicy(ref ->
                                ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        // Content-Security-Policy — sprečava XSS i data injection napade
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; " +
                                        "script-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com https://fonts.googleapis.com; " +
                                        "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://cdnjs.cloudflare.com; " +
                                        "font-src 'self' https://fonts.gstatic.com https://cdnjs.cloudflare.com; " +
                                        "img-src 'self' data: https:; " +
                                        "connect-src 'self' https://projekat-production.up.railway.app; " +
                                        "frame-ancestors 'none';"
                        ))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/javno/**").permitAll()
                        // GET programeri zahtijeva prijavu — neregistrovani ne vide nista
                        .requestMatchers(HttpMethod.GET,  "/api/programeri").authenticated()
                        .requestMatchers(HttpMethod.GET,  "/api/programeri/{id}").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/programeri/{id}/klik").permitAll()
                        // GET usluge zahtijeva prijavu — neregistrovani ne vide podatke
                        .requestMatchers(HttpMethod.GET,  "/api/usluge/stats").permitAll()
                        .requestMatchers(HttpMethod.GET,  "/api/usluge/**").authenticated()
                        .requestMatchers(HttpMethod.GET,  "/api/statistika/**").permitAll()
                        .requestMatchers(HttpMethod.GET,  "/api/spajanja/broj").permitAll()
                        .requestMatchers(HttpMethod.GET,  "/api/spajanja/poslednja").permitAll()
                        .requestMatchers(HttpMethod.POST,   "/api/programeri").authenticated()
                        .requestMatchers(HttpMethod.PUT,    "/api/programeri/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/programeri/**").authenticated()
                        .requestMatchers("/api/sacuvani/**").authenticated()
                        .requestMatchers(HttpMethod.POST,   "/api/usluge").authenticated()
                        .requestMatchers(HttpMethod.PUT,    "/api/usluge/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/usluge/**").authenticated()
                        .requestMatchers("/api/ai/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/spajanja/**").authenticated()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/korisnici/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        List<String> origins = Arrays.stream(allowedOrigin.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        config.setAllowedOriginPatterns(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"
        ));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}