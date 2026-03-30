package com.example.devbaza.security;

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

        // Uvek čistimo security context na početku requesta
        // Sprečava potencijalno "context leak" između zahteva
        SecurityContextHolder.clearContext();

        String authHeader = request.getHeader("Authorization");
        String token      = jwtUtil.izvuciIzHeadera(authHeader);

        if (token != null) {
            // Provera da li je token istekao — daje bolju poruku grešku
            if (jwtUtil.jeIstekao(token)) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"greska\":\"Token je istekao. Prijavite se ponovo.\"}");
                return;
            }

            if (jwtUtil.jeValidan(token)) {
                Long   korisnikId    = jwtUtil.getId(token);
                String korisnikTip   = jwtUtil.getTip(token);
                String korisnikEmail = jwtUtil.getEmail(token);

                // Validacija da tip nije null — odbrana od malformiranog tokena
                if (korisnikId != null && korisnikTip != null && korisnikEmail != null) {

                    // Postavljamo atribute na request — dostupni u kontrolerima
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

                    // Postavljamo autentifikaciju u Spring Security context
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
            // Ako token nije validan (a nije ni istekao) — samo nastavljamo bez autentifikacije
            // Spring Security će blokirati zaštićene endpoint-e automatski
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Preskaćemo filter za OPTIONS zahteve (CORS preflight)
     * Ovo ubrzava CORS preflight jer ne treba JWT provera
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }
}