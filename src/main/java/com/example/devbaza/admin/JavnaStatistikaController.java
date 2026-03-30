package com.example.devbaza.admin;

import com.example.devbaza.korisnik.KorisnikRepository;
import com.example.devbaza.programer.Programer;
import com.example.devbaza.programer.ProgramerRepository;
import com.example.devbaza.spajanje.SpajanjeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/javno")
public class JavnaStatistikaController {

    @Autowired
    private KorisnikRepository korisnikRepo;

    @Autowired
    private ProgramerRepository programerRepo;

    @Autowired
    private SpajanjeRepository spajanjeRepo;

    /**
     * GET /api/javno/stats
     * Samo agregirani brojevi — ne vraća lične podatke.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getJavneStats() {
        return ResponseEntity.ok(Map.of(
                "brojProgramera", programerRepo.countByAktivanTrue(),
                "brojFirmi",      korisnikRepo.countByTip("firma")
        ));
    }

    /**
     * GET /api/javno/programeri/pregled
     * Ograničena lista za grafikone — samo javna polja, bez kontakt podataka.
     * Maksimum 100 profila, sortirano po datumu.
     */
    @GetMapping("/programeri/pregled")
    public ResponseEntity<?> getProgrameriZaGrafikone() {
        List<Programer> profili = programerRepo.findByAktivanTrue(
                PageRequest.of(0, 100, Sort.by("kreiranDatum").descending())
        ).getContent();

        List<Map<String, Object>> javniPodaci = profili.stream()
                .map(p -> Map.<String, Object>of(
                        "id",          p.getId(),
                        "nivo",        p.getNivo()        != null ? p.getNivo()        : "",
                        "grad",        p.getGrad()        != null ? p.getGrad()        : "",
                        "iskustvo",    p.getIskustvo()    != null ? p.getIskustvo()    : 0,
                        "tehnologije", p.getTehnologije() != null ? p.getTehnologije() : List.of(),
                        "nacinRada",   p.getNacinRada()   != null ? p.getNacinRada()   : "",
                        "pozicija",    p.getPozicija()    != null ? p.getPozicija()    : ""
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(javniPodaci);
    }

    /**
     * GET /api/javno/tehnologije/top
     * Top 10 tehnologija — jedan SQL GROUP BY upit umesto 500 profila u memoriji.
     */
    @GetMapping("/tehnologije/top")
    public ResponseEntity<?> getTopTehnologije() {
        List<Object[]> rows = programerRepo.topTehnologijeNative(10);

        List<Map<String, Object>> top10 = rows.stream()
                .map(row -> Map.<String, Object>of(
                        "tehnologija", row[0].toString(),
                        "broj",        ((Number) row[1]).longValue()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(top10);
    }

    /**
     * GET /api/javno/aktivnost
     * Aktivnost platforme — novi profili danas, novi korisnici sedmice,
     * spajanja danas i ukupno pregleda profila.
     * Svi podaci dolaze iz postojećih tabela — nema novih zavisnosti.
     */
    @GetMapping("/aktivnost")
    public ResponseEntity<?> getAktivnost() {
        LocalDateTime pocetakDana    = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime sedmicaDana    = LocalDateTime.now().minusDays(7);

        long noviProfiliDanas     = programerRepo.countNovihOd(pocetakDana);
        long noviKorisniciSedmica = korisnikRepo.countRegistrovanihOd(sedmicaDana);
        long spajanjaDanas        = spajanjeRepo.countOd(pocetakDana);
        Long ukupnoPregleda       = programerRepo.ukupnoPregleda();

        return ResponseEntity.ok(Map.of(
                "noviProfiliDanas",     noviProfiliDanas,
                "noviKorisniciSedmica", noviKorisniciSedmica,
                "spajanjaDanas",        spajanjaDanas,
                "ukupnoPregleda",       ukupnoPregleda != null ? ukupnoPregleda : 0L
        ));
    }
}