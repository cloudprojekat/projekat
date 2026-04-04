package com.example.devbaza.usluga;

import com.example.devbaza.programer.ProgramerRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/usluge")
public class ItUslugaController {

    @Autowired private ItUslugaRepository uslugaRepo;
    @Autowired private ProgramerRepository programerRepo;

    // ── Pomocna metoda: da li korisnik ima pristup kontakt podacima ──
    // Samo firme i admini vide kontakt email
    private boolean imaPuniPristup(HttpServletRequest request) {
        String tip = (String) request.getAttribute("korisnikTip");
        return "firma".equals(tip) || "admin".equals(tip);
    }

    // ────────────────────────────────────────────────────────────────
    //  GET /api/usluge
    // ────────────────────────────────────────────────────────────────
    @GetMapping
    public ResponseEntity<Map<String, Object>> getSve(
            @RequestParam(defaultValue = "0")    int     page,
            @RequestParam(defaultValue = "20")   int     size,
            @RequestParam(defaultValue = "sve")  String  kategorija,
            @RequestParam(required = false)      String  q,
            @RequestParam(required = false)      Integer maxCena,
            @RequestParam(defaultValue = "svi")  String  grad,
            @RequestParam(required = false)      String  nacinRada,
            @RequestParam(defaultValue = "novo") String  sortBy,
            HttpServletRequest request) {

        size = Math.min(size, 50);

        String katParam   = (kategorija.isBlank() || kategorija.equals("sve")) ? null : kategorija;
        String qParam     = (q == null || q.isBlank()) ? null : q.trim();
        String gradParam  = (grad.isBlank() || grad.equals("svi")) ? null : grad;
        String nacinParam = (nacinRada == null || nacinRada.isBlank()) ? null : nacinRada;

        Sort sort = switch (sortBy) {
            case "cena_asc"  -> Sort.by(Sort.Direction.ASC,  "cenaOd");
            case "cena_desc" -> Sort.by(Sort.Direction.DESC, "cenaOd");
            default          -> Sort.by(Sort.Direction.DESC, "kreirano");
        };

        Pageable pageable = PageRequest.of(page, size, sort);

        Page<ItUsluga> stranica = uslugaRepo.pretrazi(
                katParam, qParam, maxCena, gradParam, nacinParam, pageable);

        boolean puniPristup = imaPuniPristup(request);

        // U listi usluga kontaktEmail nikad ne prikazujemo — to je samo na detail stranici
        List<ItUsluga> sadrzaj = stranica.getContent();
        if (!puniPristup) {
            sadrzaj.forEach(u -> u.setKontaktEmail(null));
        }

        Map<String, Object> odgovor = new LinkedHashMap<>();
        odgovor.put("sadrzaj",         sadrzaj);
        odgovor.put("ukupnoElemenata", stranica.getTotalElements());
        odgovor.put("ukupnoStrana",    stranica.getTotalPages());
        odgovor.put("trenutnaStrana",  stranica.getNumber());
        odgovor.put("velicinaStrane",  stranica.getSize());
        odgovor.put("poslednja",       stranica.isLast());

        return ResponseEntity.ok(odgovor);
    }

    // ────────────────────────────────────────────────────────────────
    //  GET /api/usluge/stats
    // ────────────────────────────────────────────────────────────────
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getStats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("ukupnoUsluga",    uslugaRepo.countByAktivanTrue());
        stats.put("ukupnoStucnjaka", uslugaRepo.countDistinctProgrameri());
        return ResponseEntity.ok(stats);
    }

    // ────────────────────────────────────────────────────────────────
    //  GET /api/usluge/{id}
    //  Pregled se ne povećava ako je vlasnik usluge taj koji gleda.
    //  kontaktEmail je vidljiv samo firmama i adminima.
    // ────────────────────────────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<?> getJedan(@PathVariable Long id, HttpServletRequest request) {
        return uslugaRepo.findById(id)
                .filter(ItUsluga::getAktivan)
                .map(u -> {
                    Long tokenKorisnikId = (Long) request.getAttribute("korisnikId");
                    String tokenTip = (String) request.getAttribute("korisnikTip");

                    boolean jeVlasnik = tokenKorisnikId != null
                            && tokenKorisnikId.equals(u.getProgramerId());

                    boolean puniPristup = "firma".equals(tokenTip)
                            || "admin".equals(tokenTip)
                            || jeVlasnik;

                    // Povećavamo preglede samo ako nije vlasnik
                    if (!jeVlasnik) {
                        uslugaRepo.incrementPregleda(id);
                    }

                    // Sakrijemo kontakt email ako nema pristup
                    if (!puniPristup) {
                        u.setKontaktEmail(null);
                    }

                    return ResponseEntity.ok(u);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ────────────────────────────────────────────────────────────────
    //  GET /api/usluge/programer/{programerId}
    // ────────────────────────────────────────────────────────────────
    @GetMapping("/programer/{programerId}")
    public ResponseEntity<List<ItUsluga>> zaProgamera(@PathVariable Long programerId,
                                                      HttpServletRequest request) {
        List<ItUsluga> usluge = uslugaRepo.findByProgramerIdAndAktivanTrue(programerId);

        boolean puniPristup = imaPuniPristup(request);
        Long tokenId = (Long) request.getAttribute("korisnikId");
        boolean jeVlasnik = tokenId != null && tokenId.equals(programerId);

        if (!puniPristup && !jeVlasnik) {
            usluge.forEach(u -> u.setKontaktEmail(null));
        }

        return ResponseEntity.ok(usluge);
    }

    // ────────────────────────────────────────────────────────────────
    //  POST /api/usluge
    // ────────────────────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<?> kreiraj(@Valid @RequestBody ItUsluga usluga,
                                     HttpServletRequest request) {
        Long korisnikId = (Long) request.getAttribute("korisnikId");
        if (korisnikId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        // Validacija URL-ova
        String portGreska = validirajPortfolioUrl(usluga.getPortfolioLink());
        if (portGreska != null)
            return ResponseEntity.badRequest().body(Map.of("greska", portGreska));

        usluga.setProgramerId(korisnikId);
        programerRepo.findByKorisnikIdAndAktivanTrue(korisnikId).ifPresent(p ->
                usluga.setProgramerIme(p.getIme())
        );

        usluga.setAktivan(true);
        return ResponseEntity.status(HttpStatus.CREATED).body(uslugaRepo.save(usluga));
    }

    // ────────────────────────────────────────────────────────────────
    //  PUT /api/usluge/{id}
    // ────────────────────────────────────────────────────────────────
    @PutMapping("/{id}")
    public ResponseEntity<?> izmeni(@PathVariable Long id,
                                    @RequestBody ItUsluga izmene,
                                    HttpServletRequest request) {
        Long korisnikId = (Long) request.getAttribute("korisnikId");
        String tip = (String) request.getAttribute("korisnikTip");

        Optional<ItUsluga> opt = uslugaRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        ItUsluga u = opt.get();
        boolean jeVlasnik = u.getProgramerId() != null && u.getProgramerId().equals(korisnikId);
        if (!jeVlasnik && !"admin".equals(tip)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("greska", "Nema dozvole."));
        }

        // Validacija portfolio URL-a
        if (izmene.getPortfolioLink() != null) {
            String portGreska = validirajPortfolioUrl(izmene.getPortfolioLink());
            if (portGreska != null)
                return ResponseEntity.badRequest().body(Map.of("greska", portGreska));
        }

        if (izmene.getNaziv() != null)         u.setNaziv(izmene.getNaziv());
        if (izmene.getOpis() != null)          u.setOpis(izmene.getOpis());
        if (izmene.getKategorija() != null)    u.setKategorija(izmene.getKategorija());
        if (izmene.getCenaOd() != null)        u.setCenaOd(izmene.getCenaOd());
        if (izmene.getRokIsporuke() != null)   u.setRokIsporuke(izmene.getRokIsporuke());
        if (izmene.getNacinRada() != null)     u.setNacinRada(izmene.getNacinRada());
        if (izmene.getGrad() != null)          u.setGrad(izmene.getGrad());
        if (izmene.getTehnologije() != null)   u.setTehnologije(izmene.getTehnologije());
        if (izmene.getKontaktEmail() != null)  u.setKontaktEmail(izmene.getKontaktEmail());
        if (izmene.getPortfolioLink() != null) u.setPortfolioLink(izmene.getPortfolioLink());

        return ResponseEntity.ok(uslugaRepo.save(u));
    }

    // ────────────────────────────────────────────────────────────────
    //  DELETE /api/usluge/{id}
    // ────────────────────────────────────────────────────────────────
    @DeleteMapping("/{id}")
    public ResponseEntity<?> obrisi(@PathVariable Long id,
                                    HttpServletRequest request) {
        Long korisnikId = (Long) request.getAttribute("korisnikId");
        String tip = (String) request.getAttribute("korisnikTip");

        Optional<ItUsluga> opt = uslugaRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        ItUsluga u = opt.get();
        boolean jeVlasnik = u.getProgramerId() != null && u.getProgramerId().equals(korisnikId);
        if (!jeVlasnik && !"admin".equals(tip)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("greska", "Nema dozvole."));
        }

        u.setAktivan(false);
        uslugaRepo.save(u);
        return ResponseEntity.ok(Map.of("poruka", "Usluga uklonjena."));
    }

    /**
     * Validacija portfolio/CV linka za IT usluge.
     * Mora biti https://, max 500 karaktera.
     */
    private String validirajUrl(String url, String naziv) {
        if (url == null || url.isBlank()) return null;
        url = url.trim();
        if (url.length() > 500)
            return naziv + " link je predugačak (max 500 karaktera).";
        if (!url.startsWith("https://"))
            return naziv + " link mora počinjati sa https://.";
        String lower = url.toLowerCase();
        if (lower.contains("javascript:") || lower.contains("data:"))
            return naziv + " link nije ispravan.";
        return null;
    }

    private String validirajPortfolioUrl(String url) {
        return validirajUrl(url, "Portfolio");
    }
}