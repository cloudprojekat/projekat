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
            @RequestParam(defaultValue = "novo") String  sortBy) {

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

        Map<String, Object> odgovor = new LinkedHashMap<>();
        odgovor.put("sadrzaj",         stranica.getContent());
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
    //  Vlasnik se identifikuje po korisnikId iz JWT tokena koji se
    //  poredi sa programerId na ItUsluga entitetu.
    // ────────────────────────────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<?> getJedan(@PathVariable Long id, HttpServletRequest request) {
        return uslugaRepo.findById(id)
                .filter(ItUsluga::getAktivan)
                .map(u -> {
                    Long tokenKorisnikId = (Long) request.getAttribute("korisnikId");
                    boolean jeVlasnik = tokenKorisnikId != null
                            && tokenKorisnikId.equals(u.getProgramerId());

                    // Povećavamo preglede samo ako nije vlasnik
                    if (!jeVlasnik) {
                        uslugaRepo.incrementPregleda(id);
                    }

                    return ResponseEntity.ok(u);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ────────────────────────────────────────────────────────────────
    //  GET /api/usluge/programer/{programerId}
    // ────────────────────────────────────────────────────────────────
    @GetMapping("/programer/{programerId}")
    public ResponseEntity<List<ItUsluga>> zaProgamera(@PathVariable Long programerId) {
        return ResponseEntity.ok(uslugaRepo.findByProgramerIdAndAktivanTrue(programerId));
    }

    // ────────────────────────────────────────────────────────────────
    //  POST /api/usluge
    // ────────────────────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<?> kreiraj(@Valid @RequestBody ItUsluga usluga,
                                     HttpServletRequest request) {
        Long korisnikId = (Long) request.getAttribute("korisnikId");
        if (korisnikId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

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
}