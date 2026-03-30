package com.example.devbaza.spajanje;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/spajanja")
public class SpajanjeController {

    @Autowired
    private SpajanjeRepository spajanjeRepo;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd.MM. HH:mm");

    @GetMapping("/broj")
    public ResponseEntity<?> getBroj() {
        long nedeljno = spajanjeRepo.countOd(LocalDateTime.now().minusWeeks(1));
        long mesecno  = spajanjeRepo.countOd(LocalDateTime.now().minusMonths(1));

        Double prosekDev   = spajanjeRepo.prosekDanaDeveloper();
        Double prosekFirma = spajanjeRepo.prosekDanaFirma();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("ukupno",     spajanjeRepo.count());
        res.put("nedeljno",   nedeljno);
        res.put("mesecno",    mesecno);
        res.put("prosekDanaDeveloper", prosekDev   != null ? Math.round(prosekDev)   : null);
        res.put("prosekDanaFirma",     prosekFirma != null ? Math.round(prosekFirma) : null);
        return ResponseEntity.ok(res);
    }

    // GET /api/spajanja/poslednja — javno
    @GetMapping("/poslednja")
    public ResponseEntity<?> getPoslednja() {
        List<Spajanje> lista = spajanjeRepo.nadjiPoslednja(PageRequest.of(0, 20));
        List<Map<String, Object>> rezultat = lista.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tip",      s.getTip());
            m.put("pozicija", s.getPozicija());
            m.put("vreme",    s.getVremenaDana());
            m.put("datum",    s.getDatum() != null ? s.getDatum().format(FMT) : "");
            return m;
        }).toList();
        return ResponseEntity.ok(rezultat);
    }

    @PostMapping("/dodaj")
    public ResponseEntity<?> dodaj(@RequestBody Map<String, Object> body,
                                   HttpServletRequest request) {
        Long korisnikId    = (Long) request.getAttribute("korisnikId");
        String korisnikTip = (String) request.getAttribute("korisnikTip");

        if (korisnikId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("greska", "Morate biti ulogovani."));
        }

        // Validacija pozicije
        String pozicija = body.getOrDefault("pozicija", "").toString().trim();
        if (pozicija.isBlank() || pozicija.length() > 100) {
            return ResponseEntity.badRequest()
                    .body(Map.of("greska", "Pozicija je obavezna (max 100 karaktera)."));
        }
        pozicija = pozicija.replaceAll("<[^>]*>", "");

        // Validacija dana
        Integer dana = null;
        try {
            Object danaObj = body.get("vreme");
            if (danaObj != null && !danaObj.toString().isBlank()) {
                dana = Integer.parseInt(danaObj.toString());
                if (dana < 1 || dana > 365) dana = null;
            }
        } catch (NumberFormatException ignored) {}

        String tip = korisnikTip != null ? korisnikTip : "nepoznato";

        Spajanje s = new Spajanje();
        s.setKorisnikId(korisnikId);
        s.setTip(tip);
        s.setPozicija(pozicija);
        s.setVremenaDana(dana);
        spajanjeRepo.save(s);

        Double prosekDev   = spajanjeRepo.prosekDanaDeveloper();
        Double prosekFirma = spajanjeRepo.prosekDanaFirma();

        return ResponseEntity.ok(Map.of(
                "success",             true,
                "ukupno",              spajanjeRepo.count(),
                "prosekDanaDeveloper", prosekDev   != null ? Math.round(prosekDev)   : 0,
                "prosekDanaFirma",     prosekFirma != null ? Math.round(prosekFirma) : 0
        ));
    }
}