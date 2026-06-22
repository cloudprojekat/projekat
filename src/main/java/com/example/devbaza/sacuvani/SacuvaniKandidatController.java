package com.example.devbaza.sacuvani;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/sacuvani")
public class SacuvaniKandidatController {

    @Autowired
    private com.example.devbaza.sacuvani.SacuvaniKandidatRepository sacuvaniRepo;

    /**
     * POST /api/sacuvani/dodaj
     * Firma može sačuvati samo za SEBE — ID se čita iz JWT tokena!
     */
    @PostMapping("/dodaj")
    public ResponseEntity<?> dodaj(@RequestBody Map<String, Long> body,
                                   HttpServletRequest request) {
        Long tokenId  = (Long) request.getAttribute("korisnikId");
        String tip    = (String) request.getAttribute("korisnikTip");
        Long programerId = body.get("programerId");

        if (tokenId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        // Samo firme mogu čuvati kandidate
        if (!"firma".equals(tip) && !"admin".equals(tip)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("greska", "Samo firme mogu čuvati kandidate."));
        }

        if (programerId == null) {
            return ResponseEntity.badRequest().body(Map.of("greska", "programerId je obavezan."));
        }

        // firmaId dolazi iz JWT tokena — ne iz body-ja!
        Long firmaId = tokenId;

        if (sacuvaniRepo.findByFirmaIdAndProgramerId(firmaId, programerId).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("greska", "Kandidat je već sačuvan."));
        }

        sacuvaniRepo.save(new SacuvaniKandidat(firmaId, programerId));
        return ResponseEntity.ok(Map.of("success", true, "poruka", "Kandidat sačuvan."));
    }

    /**
     * POST /api/sacuvani/ukloni
     */
    @PostMapping("/ukloni")
    @Transactional
    public ResponseEntity<?> ukloni(@RequestBody Map<String, Long> body,
                                    HttpServletRequest request) {
        Long tokenId     = (Long) request.getAttribute("korisnikId");
        Long programerId = body.get("programerId");

        if (tokenId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        sacuvaniRepo.deleteByFirmaIdAndProgramerId(tokenId, programerId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * GET /api/sacuvani/firma/{id}
     * Firma može videti samo SVOJE sačuvane — proverava se JWT
     */
    @GetMapping("/firma/{id}")
    public ResponseEntity<?> zaSvojuFirmu(@PathVariable Long id,
                                          HttpServletRequest request) {
        Long tokenId = (Long) request.getAttribute("korisnikId");
        String tip   = (String) request.getAttribute("korisnikTip");

        if (tokenId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        // Provera — firma može videti samo svoje
        if (!"admin".equals(tip) && !tokenId.equals(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("greska", "Nemate pristup ovim podacima."));
        }

        return ResponseEntity.ok(sacuvaniRepo.findByFirmaId(id));
    }

    /**
     * GET /api/sacuvani/check?programerId=5
     * Proverava da li je firma sačuvala programera
     */
    @GetMapping("/check")
    public ResponseEntity<?> check(@RequestParam Long programerId,
                                   HttpServletRequest request) {
        Long firmaId = (Long) request.getAttribute("korisnikId");
        if (firmaId == null) return ResponseEntity.ok(Map.of("jeSacuvan", false));

        boolean jeSacuvan = sacuvaniRepo
                .findByFirmaIdAndProgramerId(firmaId, programerId).isPresent();
        return ResponseEntity.ok(Map.of("jeSacuvan", jeSacuvan));
    }

    /**
     * GET /api/sacuvani/programer/{id}/broj
     * Javno — koliko firmi je sačuvalo programera (za statistiku)
     */
    @GetMapping("/programer/{id}/broj")
    public ResponseEntity<?> brojSacuvanja(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of(
                "programerId", id,
                "brojFirmi", sacuvaniRepo.countByProgramerId(id)
        ));
    }
}
