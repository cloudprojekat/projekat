package com.example.devbaza.korisnik;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Kontroler za upravljanje korisnicima.
 * Svi endpointi zahtevaju ADMIN rolu (definisano u SecurityConfig).
 *
 * VAŽNO: Nikad ne vraćamo Korisnik entitet direktno — koristimo KorisnikDTO
 * da sprečimo curenje BCrypt hash-a lozinke.
 *
 * ENDPOINTI:
 * GET    /api/korisnici        → Svi korisnici (samo admin)
 * GET    /api/korisnici/{id}   → Jedan korisnik (samo admin)
 * PUT    /api/korisnici/{id}/status → Aktivacija/deaktivacija (samo admin)
 * DELETE /api/korisnici/{id}   → Brisanje korisnika (samo admin)
 */
@RestController
@RequestMapping("/api/korisnici")
// Nema @CrossOrigin(origins = "*") — CORS se kontroliše centralno u SecurityConfig
public class KorisnikController {

    @Autowired
    private KorisnikRepository korisnikRepository;

    /**
     * GET /api/korisnici — svi korisnici kao DTO (bez lozinke)
     * Zaštićeno: samo ADMIN (SecurityConfig)
     */
    @GetMapping
    public ResponseEntity<List<KorisnikDTO>> getAll() {
        List<KorisnikDTO> korisnici = korisnikRepository.findAll()
                .stream()
                .map(KorisnikDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(korisnici);
    }

    /**
     * GET /api/korisnici/{id} — jedan korisnik kao DTO
     * Zaštićeno: samo ADMIN (SecurityConfig)
     */
    @GetMapping("/{id}")
    public ResponseEntity<KorisnikDTO> getById(@PathVariable Long id) {
        return korisnikRepository.findById(id)
                .map(k -> ResponseEntity.ok(new KorisnikDTO(k)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * PUT /api/korisnici/{id}/status — aktivacija ili deaktivacija naloga
     * Zaštićeno: samo ADMIN (SecurityConfig)
     *
     * Body: { "aktivan": true/false }
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<?> promeniStatus(@PathVariable Long id,
                                           @RequestBody Map<String, Boolean> body,
                                           HttpServletRequest request) {
        // Provera da admin ne može da deaktivira sam sebe
        Long adminId = (Long) request.getAttribute("korisnikId");
        if (id.equals(adminId)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("greska", "Ne možete deaktivirati sopstveni nalog."));
        }

        Boolean aktivan = body.get("aktivan");
        if (aktivan == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("greska", "Polje 'aktivan' je obavezno."));
        }

        return korisnikRepository.findById(id)
                .map(k -> {
                    k.setAktivan(aktivan);
                    korisnikRepository.save(k);
                    String poruka = aktivan ? "Nalog aktiviran." : "Nalog deaktiviran.";
                    return ResponseEntity.ok(Map.of("poruka", poruka, "korisnik", new KorisnikDTO(k)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * DELETE /api/korisnici/{id} — trajno brisanje korisnika
     * Zaštićeno: samo ADMIN (SecurityConfig)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> obrisi(@PathVariable Long id,
                                    HttpServletRequest request) {
        // Provera da admin ne briše sam sebe
        Long adminId = (Long) request.getAttribute("korisnikId");
        if (id.equals(adminId)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("greska", "Ne možete obrisati sopstveni nalog."));
        }

        if (!korisnikRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        korisnikRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("poruka", "Korisnik trajno obrisan."));
    }
}