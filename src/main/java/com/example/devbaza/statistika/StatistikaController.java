package com.example.devbaza.statistika;

import com.example.devbaza.programer.ProgramerRepository;
import com.example.devbaza.sacuvani.SacuvaniKandidatRepository;
import com.example.devbaza.spajanje.SpajanjeRepository;
import com.example.devbaza.usluga.ItUslugaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/statistika")
public class StatistikaController {

    @Autowired private ProgramerRepository programerRepo;
    @Autowired private SpajanjeRepository spajanjeRepo;
    @Autowired private ItUslugaRepository uslugaRepo;
    @Autowired private SacuvaniKandidatRepository sacuvaniRepo;

    /**
     * GET /api/statistika — javna statistika platforme
     */
    @GetMapping
    public ResponseEntity<?> getPlatforma() {
        // findByAktivanTrue(PageRequest.of(0, 1)).getTotalElements() je ok — samo COUNT query
        long ukupnoDevs = programerRepo.findByAktivanTrue(PageRequest.of(0, 1)).getTotalElements();

        long ukupnoSpajanja   = spajanjeRepo.count();
        long nedeljnoSpajanja = spajanjeRepo.countOd(LocalDateTime.now().minusWeeks(1));
        long mesecnoSpajanja  = spajanjeRepo.countOd(LocalDateTime.now().minusMonths(1));

        // ISPRAVKA: countByAktivanTrue() umesto findByAktivanTrue().size()
        // Stara verzija učitavala je SVE usluge u memoriju samo da bi ih prebrojala!
        long ukupnoUsluga = uslugaRepo.countByAktivanTrue();

        Map<String, Object> stat = new LinkedHashMap<>();
        stat.put("ukupnoDevs",        ukupnoDevs);
        stat.put("ukupnoSpajanja",    ukupnoSpajanja);
        stat.put("nedeljnoSpajanja",  nedeljnoSpajanja);
        stat.put("mesecnoSpajanja",   mesecnoSpajanja);
        stat.put("ukupnoUsluga",      ukupnoUsluga);

        return ResponseEntity.ok(stat);
    }

    /**
     * GET /api/statistika/developer/{id} — statistika za jednog developera (javno)
     */
    @GetMapping("/developer/{id}")
    public ResponseEntity<?> getDeveloper(@PathVariable Long id) {
        return programerRepo.findById(id)
                .filter(p -> Boolean.TRUE.equals(p.getAktivan()))
                .map(p -> {
                    Long sacuvanja = sacuvaniRepo.countByProgramerId(id);

                    Map<String, Object> stat = new LinkedHashMap<>();
                    stat.put("id",           p.getId());
                    stat.put("ime",          p.getIme());
                    stat.put("grad",         p.getGrad());
                    stat.put("nivo",         p.getNivo());
                    stat.put("iskustvo",     p.getIskustvo());
                    stat.put("tehnologije",  p.getTehnologije());
                    stat.put("brojPregleda", p.getBrojPregleda());
                    stat.put("brojSacuvanja", sacuvanja);
                    stat.put("kreiranDatum", p.getKreiranDatum());
                    // Ne vraćamo platu, GitHub, CV, opis — to su lični podaci
                    // koji su vidljivi na profilu, ali ne moraju biti u statistici

                    return ResponseEntity.ok(stat);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}