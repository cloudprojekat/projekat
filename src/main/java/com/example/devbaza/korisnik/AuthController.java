package com.example.devbaza.korisnik;

import com.example.devbaza.security.JwtUtil;
import com.example.devbaza.security.RateLimiterService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
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

    @Value("${jwt.expiration.days:7}")
    private int expirationDays;

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
                                   HttpServletRequest request,
                                   HttpServletResponse response) {
        String ip = getClientIp(request);
        if (!rateLimiter.dozvoljenaAuthAkcija(ip)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(greska("Previše pokušaja. Sačekaj minut."));
        }

        String email = sanitizeEmail(req.email);
        Optional<Korisnik> opt = korisnikRepo.findByEmail(email);

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

        // Postavi token u HttpOnly cookie — JavaScript ne može pročitati
        postaviJwtCookie(response, token);

        // Vraćamo samo javne podatke — token NE ide u response body
        return ResponseEntity.ok(buildAuthResponse(k));
    }

    // ─── REGISTRACIJA ────────────────────────────────────────────────────────

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req,
                                      HttpServletRequest request,
                                      HttpServletResponse response) {
        String ip = getClientIp(request);
        if (!rateLimiter.dozvoljenaAuthAkcija(ip)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(greska("Previše pokušaja. Sačekaj minut."));
        }

        if (!req.tip.equals("programer") && !req.tip.equals("firma")) {
            return ResponseEntity.badRequest()
                    .body(greska("Tip mora biti 'programer' ili 'firma'."));
        }

        String email = sanitizeEmail(req.email);

        if (korisnikRepo.existsByEmail(email)) {
            return ResponseEntity.badRequest()
                    .body(greska("Registracija nije uspela. Proverite podatke."));
        }

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

        // Postavi token u HttpOnly cookie
        postaviJwtCookie(response, token);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(buildAuthResponse(sacuvan));
    }

    // ─── LOGOUT ──────────────────────────────────────────────────────────────

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        // Obriši cookie postavljanjem max-age na 0
        Cookie cookie = new Cookie("jwt_token", "");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return ResponseEntity.ok(Map.of("poruka", "Odjavljen."));
    }

    // ─── VERIFY TOKEN ────────────────────────────────────────────────────────

    @GetMapping("/verify")
    public ResponseEntity<?> verify(HttpServletRequest request) {
        // Čita token iz cookie-a ili Authorization headera (za kompatibilnost)
        String token = izvuciTokenIzCookieja(request);
        if (token == null) {
            token = jwtUtil.izvuciIzHeadera(request.getHeader("Authorization"));
        }

        if (token == null || !jwtUtil.jeValidan(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(greska("Token nije validan ili je istekao."));
        }

        Map<String, Object> info = new HashMap<>();
        info.put("id",  jwtUtil.getId(token));
        info.put("ime", jwtUtil.getIme(token));
        info.put("tip", jwtUtil.getTip(token));

        return ResponseEntity.ok(info);
    }

    // ─── HELPER metode ────────────────────────────────────────────────────────

    /**
     * Postavlja JWT token kao HttpOnly, Secure, SameSite=Strict cookie.
     * HttpOnly = JavaScript ne može pročitati token (zaštita od XSS).
     * Secure   = šalje se samo preko HTTPS.
     * SameSite = zaštita od CSRF napada.
     */
    private void postaviJwtCookie(HttpServletResponse response, String token) {
        int maxAge = expirationDays * 24 * 60 * 60;

        // Spring Cookie klasa ne podržava SameSite direktno, koristimo header
        String cookieValue = String.format(
                "jwt_token=%s; Max-Age=%d; Path=/; HttpOnly; Secure; SameSite=Strict",
                token, maxAge
        );
        response.addHeader("Set-Cookie", cookieValue);
    }

    /**
     * Izvlači JWT token iz cookie-a.
     */
    public static String izvuciTokenIzCookieja(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> "jwt_token".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    /**
     * Gradi auth response BEZ tokena — token je u HttpOnly cookie-u.
     * JavaScript ne može pročitati cookie, ali browser ga automatski šalje.
     */
    private Map<String, Object> buildAuthResponse(Korisnik k) {
        Map<String, Object> response = new HashMap<>();
        // Token se NE vraća u body — nalazi se u HttpOnly cookie-u
        response.put("id",  k.getId());
        response.put("ime", k.getIme());
        response.put("tip", k.getTip());
        return response;
    }

    private Map<String, String> greska(String poruka) {
        Map<String, String> m = new HashMap<>();
        m.put("greska", poruka);
        return m;
    }

    private String sanitizeEmail(String email) {
        if (email == null) return "";
        return email.toLowerCase().trim();
    }

    private String sanitizeName(String ime) {
        if (ime == null) return "";
        return ime.trim().replaceAll("\\s+", " ");
    }

    private String validirajLozinku(String lozinka) {
        if (lozinka == null || lozinka.length() < 8) {
            return "Lozinka mora imati minimum 8 karaktera.";
        }
        if (!lozinka.matches(".*[a-zA-Z].*")) {
            return "Lozinka mora sadržati bar jedno slovo.";
        }
        if (!lozinka.matches(".*[0-9].*")) {
            return "Lozinka mora sadržati bar jedan broj.";
        }
        return null;
    }

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