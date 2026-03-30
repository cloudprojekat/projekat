package com.example.devbaza.korisnik;

import com.example.devbaza.security.JwtUtil;
import com.example.devbaza.security.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired private KorisnikRepository korisnikRepo;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private RateLimiterService rateLimiter;

    // ─── DTO klase ───────────────────────────────────────────────────────────

    public static class LoginRequest {
        @NotBlank(message = "Email je obavezan")
        @Email(message = "Email nije ispravan")
        public String email;

        @NotBlank(message = "Lozinka je obavezna")
        public String lozinka;
    }

    public static class RegisterRequest {
        @NotBlank(message = "Ime je obavezno")
        @Size(min = 2, max = 100, message = "Ime mora biti između 2 i 100 karaktera")
        // Dozvoljava samo slova, razmake i srpska slova — sprečava HTML/script injection u imenu
        @Pattern(regexp = "^[\\p{L} .'-]+$", message = "Ime sadrži nedozvoljene karaktere")
        public String ime;

        @NotBlank(message = "Email je obavezan")
        @Email(message = "Email nije ispravan")
        @Size(max = 150, message = "Email je predugačak")
        public String email;

        @NotBlank(message = "Lozinka je obavezna")
        @Size(min = 8, max = 100, message = "Lozinka mora imati između 8 i 100 karaktera")
        public String lozinka;

        @NotBlank(message = "Tip je obavezan")
        public String tip;
    }

    // ─── LOGIN ───────────────────────────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req,
                                   HttpServletRequest request) {
        String ip = getClientIp(request);
        if (!rateLimiter.dozvoljenaAuthAkcija(ip)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(greska("Previše pokušaja. Sačekaj minut."));
        }

        String email = sanitizeEmail(req.email);
        Optional<Korisnik> opt = korisnikRepo.findByEmail(email);

        // Namerno ista poruka greške za pogrešan email I pogrešnu lozinku
        // Sprečava "user enumeration" napad — napadač ne može da zna da li email postoji
        if (opt.isEmpty() || !passwordEncoder.matches(req.lozinka, opt.get().getLozinka())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(greska("Pogrešan email ili lozinka."));
        }

        Korisnik k = opt.get();

        if (!k.getAktivan()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(greska("Nalog je deaktiviran. Kontaktirajte podršku."));
        }

        String token = jwtUtil.generisiToken(k.getId(), k.getEmail(), k.getTip(), k.getIme());

        return ResponseEntity.ok(buildAuthResponse(token, k));
    }

    // ─── REGISTRACIJA ────────────────────────────────────────────────────────

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req,
                                      HttpServletRequest request) {
        String ip = getClientIp(request);
        if (!rateLimiter.dozvoljenaAuthAkcija(ip)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(greska("Previše pokušaja. Sačekaj minut."));
        }

        // Samo programer i firma — admin se ne može registrovati javno
        if (!req.tip.equals("programer") && !req.tip.equals("firma")) {
            return ResponseEntity.badRequest()
                    .body(greska("Tip mora biti 'programer' ili 'firma'."));
        }

        String email = sanitizeEmail(req.email);

        if (korisnikRepo.existsByEmail(email)) {
            // Namerno vaga poruka — ne otkriva da email postoji
            // U produkciji razmisli o: uvek vraćaj 201 i pošalji email potvrdu
            return ResponseEntity.badRequest()
                    .body(greska("Registracija nije uspela. Proverite podatke."));
        }

        // Lozinka validacija — proverava kompleksnost
        String lozinkaGreska = validirajLozinku(req.lozinka);
        if (lozinkaGreska != null) {
            return ResponseEntity.badRequest().body(greska(lozinkaGreska));
        }

        Korisnik novi = new Korisnik();
        novi.setIme(sanitizeName(req.ime));
        novi.setEmail(email);
        novi.setLozinka(passwordEncoder.encode(req.lozinka));
        novi.setTip(req.tip);
        novi.setAktivan(true);

        Korisnik sacuvan = korisnikRepo.save(novi);

        String token = jwtUtil.generisiToken(
                sacuvan.getId(), sacuvan.getEmail(), sacuvan.getTip(), sacuvan.getIme());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(buildAuthResponse(token, sacuvan));
    }

    // ─── VERIFY TOKEN ─────────────────────────────────────────────────────────

    @GetMapping("/verify")
    public ResponseEntity<?> verify(@RequestHeader("Authorization") String authHeader) {
        String token = jwtUtil.izvuciIzHeadera(authHeader);
        if (token == null || !jwtUtil.jeValidan(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(greska("Token nije validan ili je istekao."));
        }

        Map<String, Object> info = new HashMap<>();
        info.put("id",  jwtUtil.getId(token));
        info.put("ime", jwtUtil.getIme(token));
        info.put("tip", jwtUtil.getTip(token));
        // Ne vraćamo token nazad — klijent ga već ima

        return ResponseEntity.ok(info);
    }

    // ─── HELPER metode ────────────────────────────────────────────────────────

    private Map<String, Object> buildAuthResponse(String token, Korisnik k) {
        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("id",    k.getId());
        response.put("ime",   k.getIme());
        response.put("tip",   k.getTip());
        // NIKAD ne vraćati lozinku ili email ovde osim ako nije neophodno
        return response;
    }

    private Map<String, String> greska(String poruka) {
        Map<String, String> m = new HashMap<>();
        m.put("greska", poruka);
        return m;
    }

    /**
     * Normalizuje email — lowercase i trim
     * Sprečava "duplicate email" bug: "Test@mail.com" i "test@mail.com"
     */
    private String sanitizeEmail(String email) {
        if (email == null) return "";
        return email.toLowerCase().trim();
    }

    /**
     * Sanitizuje ime — uklanja višestruke razmake i trima
     */
    private String sanitizeName(String ime) {
        if (ime == null) return "";
        return ime.trim().replaceAll("\\s+", " ");
    }

    /**
     * Validacija kompleksnosti lozinke
     * Vraća poruku greške ili null ako je ok
     */
    private String validirajLozinku(String lozinka) {
        if (lozinka == null || lozinka.length() < 8) {
            return "Lozinka mora imati minimum 8 karaktera.";
        }
        // Mora imati bar jedno slovo
        if (!lozinka.matches(".*[a-zA-Z].*")) {
            return "Lozinka mora sadržati bar jedno slovo.";
        }
        // Mora imati bar jedan broj
        if (!lozinka.matches(".*[0-9].*")) {
            return "Lozinka mora sadržati bar jedan broj.";
        }
        return null;
    }

    /**
     * Izvlači pravi IP — uzima u obzir reverse proxy (Nginx, Cloudflare)
     * VAŽNO: X-Forwarded-For može biti spoofovan — u produkciji konfiguriši
     * trusted proxies u Nginx/Cloudflare pa ovo radi ispravno
     */
    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}