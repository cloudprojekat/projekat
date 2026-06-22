package com.example.devbaza.admin;

import com.example.devbaza.korisnik.Korisnik;
import com.example.devbaza.korisnik.KorisnikDTO;
import com.example.devbaza.korisnik.KorisnikRepository;
import com.example.devbaza.programer.Programer;
import com.example.devbaza.programer.ProgramerRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/admin")
// Nema @CrossOrigin(origins = "*") — kontroliše SecurityConfig
public class AdminController {

    @Autowired
    private KorisnikRepository korisnikRepo;

    @Autowired
    private ProgramerRepository programerRepo;

    // ─────────────────────────────────────────────────────────────────────────
    // 1. UPRAVLJANJE NALOZIMA
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * GET /api/admin/korisnici — svi korisnici kao DTO (bez lozinke)
     */
    @GetMapping("/korisnici")
    public ResponseEntity<List<KorisnikDTO>> getSviKorisnici() {
        List<KorisnikDTO> lista = korisnikRepo.findAll()
                .stream()
                .map(KorisnikDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(lista);
    }

    /**
     * PUT /api/admin/korisnici/{id}/status — aktivacija/deaktivacija
     * Soft disable umesto brisanja — preservuje podatke
     */
    @PutMapping("/korisnici/{id}/status")
    public ResponseEntity<?> promeniStatusKorisnika(@PathVariable Long id,
                                                    @RequestBody Map<String, Boolean> body,
                                                    HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("korisnikId");
        if (id.equals(adminId)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("greska", "Ne možete menjati status sopstvenog naloga."));
        }

        Boolean aktivan = body.get("aktivan");
        if (aktivan == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("greska", "Polje 'aktivan' je obavezno."));
        }

        return korisnikRepo.findById(id)
                .map(k -> {
                    k.setAktivan(aktivan);
                    korisnikRepo.save(k);
                    return ResponseEntity.ok(Map.of(
                            "poruka", aktivan ? "Nalog aktiviran." : "Nalog deaktiviran.",
                            "korisnik", new KorisnikDTO(k)
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * DELETE /api/admin/korisnici/{id} — trajno brisanje naloga
     */
    @DeleteMapping("/korisnici/{id}")
    public ResponseEntity<?> obrisiKorisnika(@PathVariable Long id,
                                             HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("korisnikId");
        if (id.equals(adminId)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("greska", "Ne možete obrisati sopstveni nalog."));
        }

        if (!korisnikRepo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        korisnikRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("poruka", "Korisnički nalog uspešno obrisan."));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. UPRAVLJANJE PROGRAMERIMA
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * GET /api/admin/programeri — svi programeri (aktivni i neaktivni)
     */
    @GetMapping("/programeri")
    public ResponseEntity<List<Programer>> getSviProgrameri() {
        return ResponseEntity.ok(programerRepo.findAll());
    }

    /**
     * PUT /api/admin/programeri/{id} — izmena profila od strane admina
     */
    @PutMapping("/programeri/{id}")
    public ResponseEntity<?> adminIzmenaProgramera(@PathVariable Long id,
                                                   @RequestBody Programer izmene) {
        return programerRepo.findById(id).map(p -> {
            if (izmene.getIme()         != null) p.setIme(izmene.getIme());
            if (izmene.getGrad()        != null) p.setGrad(izmene.getGrad());
            if (izmene.getNivo()        != null) p.setNivo(izmene.getNivo());
            if (izmene.getPlata()       != null) p.setPlata(izmene.getPlata());
            if (izmene.getTehnologije() != null) p.setTehnologije(izmene.getTehnologije());
            if (izmene.getOpis()        != null) p.setOpis(izmene.getOpis());
            if (izmene.getGithub()      != null) p.setGithub(izmene.getGithub());
            if (izmene.getNacinRada()   != null) p.setNacinRada(izmene.getNacinRada());
            if (izmene.getPozicija()    != null) p.setPozicija(izmene.getPozicija());
            if (izmene.getEngleski()    != null) p.setEngleski(izmene.getEngleski());
            if (izmene.getDostupnost()  != null) p.setDostupnost(izmene.getDostupnost());
            if (izmene.getAngazovanje() != null) p.setAngazovanje(izmene.getAngazovanje());
            if (izmene.getAktivan()     != null) p.setAktivan(izmene.getAktivan());

            programerRepo.save(p);
            return ResponseEntity.ok(Map.of("poruka", "Profil programera ažuriran."));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * DELETE /api/admin/programeri/{id} — soft delete profila
     */
    @DeleteMapping("/programeri/{id}")
    public ResponseEntity<?> adminBrisanjeProgramera(@PathVariable Long id) {
        return programerRepo.findById(id).map(p -> {
            p.setAktivan(false);
            programerRepo.save(p);
            return ResponseEntity.ok(Map.of("poruka", "Profil programera deaktiviran."));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. UPRAVLJANJE FIRMAMA
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * GET /api/admin/firme — sve firme kao DTO (bez lozinke)
     */
    @GetMapping("/firme")
    public ResponseEntity<List<KorisnikDTO>> getSveFirme() {
        List<KorisnikDTO> firme = korisnikRepo.findByTip("firma")
                .stream()
                .map(KorisnikDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(firme);
    }

    /**
     * DELETE /api/admin/firme/{id} — brisanje firme
     */
    @DeleteMapping("/firme/{id}")
    public ResponseEntity<?> obrisiFirmu(@PathVariable Long id,
                                         HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("korisnikId");
        if (id.equals(adminId)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("greska", "Ne možete obrisati sopstveni nalog."));
        }

        if (!korisnikRepo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        korisnikRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("poruka", "Nalog firme obrisan."));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. STATISTIKA ZA ADMIN PANEL
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/stats")
    public ResponseEntity<?> getAdminStats() {
        long ukupnoKorisnika  = korisnikRepo.count();
        long ukupnoProgramera = korisnikRepo.countByTip("programer");
        long ukupnoFirmi      = korisnikRepo.countByTip("firma");
        long ukupnoProfila    = programerRepo.count();

        return ResponseEntity.ok(Map.of(
                "ukupnoKorisnika",  ukupnoKorisnika,
                "ukupnoProgramera", ukupnoProgramera,
                "ukupnoFirmi",      ukupnoFirmi,
                "ukupnoProfila",    ukupnoProfila
        ));
    }
}