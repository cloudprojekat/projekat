package com.example.devbaza.security;

import com.example.devbaza.korisnik.Korisnik;
import com.example.devbaza.korisnik.KorisnikRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Custom implementacija UserDetailsService
 *
 * Spring Security zahteva ovu klasu da bi znao kako da
 * učita korisnika iz baze. Mi je implementiramo ali
 * NE koristimo Spring Security autentifikaciju direktno -
 * već radimo ručnu proveru u AuthController.
 *
 * Ova klasa je ovde da Spring Security ne bi bacao greške
 * pri pokretanju aplikacije.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private KorisnikRepository korisnikRepository;

    /**
     * Učitava korisnika po emailu za Spring Security
     *
     * MAPIRANJE PRIVILEGIJA:
     * - "admin"     → ROLE_ADMIN
     * - "firma"     → ROLE_FIRMA
     * - "programer" → ROLE_PROGRAMER
     *
     * @param email - Email korisnika
     * @return UserDetails objekat za Spring Security
     * @throws UsernameNotFoundException ako korisnik ne postoji
     */
    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        Korisnik korisnik = korisnikRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Korisnik sa emailom " + email + " nije pronađen!"
                ));

        // Mapiramo tip korisnika u Spring Security rolu
        String rola = "ROLE_" + korisnik.getTip().toUpperCase();

        return new User(
                korisnik.getEmail(),
                korisnik.getLozinka(),
                List.of(new SimpleGrantedAuthority(rola))
        );
    }
}