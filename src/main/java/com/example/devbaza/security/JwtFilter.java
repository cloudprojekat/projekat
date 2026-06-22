package com.example.devbaza.security;

import com.example.devbaza.korisnik.AuthController;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        SecurityContextHolder.clearContext();

        // 1. Pokušaj izvući token iz HttpOnly cookie-a (primarni način)
        String token = AuthController.izvuciTokenIzCookieja(request);

        // 2. Fallback na Authorization header (za API klijente koji ne koriste browser)
        if (token == null) {
            token = jwtUtil.izvuciIzHeadera(request.getHeader("Authorization"));
        }

        if (token != null) {
            if (jwtUtil.jeIstekao(token)) {
                // Obriši cookie ako je istekao
                response.addHeader("Set-Cookie",
                        "jwt_token=; Max-Age=0; Path=/; HttpOnly; Secure; SameSite=Strict");
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"greska\":\"Token je istekao. Prijavite se ponovo.\"}");
                return;
            }

            if (jwtUtil.jeValidan(token)) {
                Long   korisnikId    = jwtUtil.getId(token);
                String korisnikTip   = jwtUtil.getTip(token);
                String korisnikEmail = jwtUtil.getEmail(token);

                if (korisnikId != null && korisnikTip != null && korisnikEmail != null) {
                    request.setAttribute("korisnikId",    korisnikId);
                    request.setAttribute("korisnikTip",   korisnikTip);
                    request.setAttribute("korisnikEmail", korisnikEmail);

                    String rola = "ROLE_" + korisnikTip.toUpperCase();

                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(
                                    korisnikEmail,
                                    null,
                                    List.of(new SimpleGrantedAuthority(rola))
                            );

                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }
}