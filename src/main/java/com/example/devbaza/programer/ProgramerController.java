package com.example.devbaza.programer;

import com.example.devbaza.security.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/programeri")
public class ProgramerController {

    @Autowired private ProgramerRepository programerRepo;
    @Autowired private RateLimiterService rateLimiter;

    private static final Map<String, Integer> ENG_RANK = Map.of(
            "a2", 1, "b1", 2, "b2", 3, "c1", 4, "c2", 5
    );

    private static final int MAX_PROFILA_ZA_FILTER = 1000;

    // ── Pomocna metoda: da li korisnik ima pristup privatnim podacima ──
    // Samo firme i admini mogu videti platu, CV, GitHub, email
    private boolean imaPuniPristup(HttpServletRequest request) {
        String tip = (String) request.getAttribute("korisnikTip");
        return "firma".equals(tip) || "admin".equals(tip);
    }

    @GetMapping
    public ResponseEntity<?> getSvi(
            @RequestParam(defaultValue = "0")    int page,
            @RequestParam(defaultValue = "20")   int size,
            @RequestParam(required = false)      String q,
            @RequestParam(required = false)      String nivo,
            @RequestParam(required = false)      String grad,
            @RequestParam(required = false)      Integer minIskustvo,
            @RequestParam(required = false)      Integer maxIskustvo,
            @RequestParam(required = false)      Integer minPlata,
            @RequestParam(required = false)      Integer maxPlata,
            @RequestParam(required = false)      String nacinRada,
            @RequestParam(required = false)      String tech,
            @RequestParam(required = false)      Boolean imaCv,
            @RequestParam(required = false)      Boolean imaGithub,
            @RequestParam(required = false)      String pozicija,
            @RequestParam(required = false)      String engleski,
            @RequestParam(required = false)      String dostupnost,
            @RequestParam(required = false)      String angazovanje,
            @RequestParam(defaultValue = "kreiranDatum") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            HttpServletRequest request) {

        if (!rateLimiter.dozvoljenaAkcija(getIp(request))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("greska", "Previse zahteva. Sacekaj minut."));
        }

        // Provera da li korisnik ima pravo da vidi privatne podatke
        boolean puniPristup = imaPuniPristup(request);

        size = Math.min(size, 50);

        String qParam           = blank(q)          ? null : q.trim();
        String nivoParam        = blank(nivo)        ? null : nivo.trim();
        String gradParam        = blank(grad)        ? null : grad.trim();
        String nacinRadaParam   = blank(nacinRada)   ? null : nacinRada.trim();
        String techParam        = blank(tech)        ? null : tech.trim();
        String pozicijaParam    = blank(pozicija)    ? null : pozicija.trim();
        String engleskiParam    = blank(engleski)    ? null : engleski.trim();
        String dostupnostParam  = blank(dostupnost)  ? null : dostupnost.trim();
        String angazovanjeParam = blank(angazovanje) ? null : angazovanje.trim();

        boolean imaFiltera = qParam != null || nivoParam != null || gradParam != null
                || minIskustvo != null || maxIskustvo != null
                || minPlata != null    || maxPlata != null
                || nacinRadaParam != null || techParam != null
                || Boolean.TRUE.equals(imaCv) || Boolean.TRUE.equals(imaGithub)
                || pozicijaParam != null || engleskiParam != null
                || dostupnostParam != null || angazovanjeParam != null;

        if (!imaFiltera) {
            List<String> dozvoljenaSortPolja = List.of(
                    "kreiranDatum", "iskustvo", "plata", "brojPregleda", "ime");
            if (!dozvoljenaSortPolja.contains(sortBy)) sortBy = "kreiranDatum";
            Sort sort = "asc".equalsIgnoreCase(sortDir)
                    ? Sort.by(sortBy).ascending()
                    : Sort.by(sortBy).descending();
            Page<Programer> stranica = programerRepo.findByAktivanTrue(
                    PageRequest.of(page, size, sort));
            List<Map<String, Object>> sadrzaj = stranica.getContent().stream()
                    .map(p -> buildDto(p, null, null, puniPristup))
                    .collect(Collectors.toList());
            return ResponseEntity.ok(buildResponse(sadrzaj, page,
                    stranica.getTotalPages(), stranica.getTotalElements(),
                    size, stranica.isLast()));
        }

        final List<String> trazene = techParam != null
                ? Arrays.stream(techParam.split(","))
                .map(String::trim).filter(s -> !s.isBlank())
                .map(String::toLowerCase).collect(Collectors.toList())
                : List.of();

        final String qF   = qParam,  nivoF = nivoParam, gradF = gradParam;
        final String nacF = nacinRadaParam, pozF = pozicijaParam;
        final String engF = engleskiParam,  dosF = dostupnostParam, angF = angazovanjeParam;
        final Integer minIskF = minIskustvo, maxIskF = maxIskustvo;
        final Integer minPF   = minPlata,    maxPF   = maxPlata;
        final Boolean cvF = imaCv, ghF = imaGithub;

        List<Programer> sviProfili = programerRepo.pretraziSoft(
                qParam,
                PageRequest.of(0, MAX_PROFILA_ZA_FILTER,
                        Sort.by("kreiranDatum").descending())
        ).getContent();

        List<Map<String, Object>> sviScored = sviProfili.stream()
                .map(p -> {
                    MatchResult mr = izracunajScore(p,
                            qF, trazene, nivoF, gradF, nacF,
                            minIskF, maxIskF, minPF, maxPF,
                            cvF, ghF, pozF, engF, dosF, angF);
                    return buildDto(p, mr.score, mr.poklapanja, puniPristup);
                })
                .sorted(Comparator.comparingInt(
                                (Map<String, Object> d) -> (int) d.getOrDefault("matchScore", 0))
                        .reversed())
                .collect(Collectors.toList());

        int ukupno       = sviScored.size();
        int from         = page * size;
        int to           = Math.min(from + size, ukupno);
        int ukupnoStrana = (int) Math.ceil((double) ukupno / size);

        if (from >= ukupno) {
            from = 0; to = Math.min(size, ukupno);
        }

        List<Map<String, Object>> stranica = sviScored.subList(from, to);
        boolean poslednja = (page + 1) >= ukupnoStrana;

        return ResponseEntity.ok(buildResponse(stranica, page,
                ukupnoStrana, (long) ukupno, size, poslednja));
    }

    // ══════════════════════════════════════════════════════════════════
    //  MATCH SCORE
    // ══════════════════════════════════════════════════════════════════

    private static class MatchResult {
        int score; String poklapanja;
        MatchResult(int s, String p) { this.score = s; this.poklapanja = p; }
    }

    private MatchResult izracunajScore(
            Programer p, String q, List<String> trazene,
            String nivo, String grad, String nacinRada,
            Integer minIsk, Integer maxIsk,
            Integer minPlata, Integer maxPlata,
            Boolean imaCv, Boolean imaGithub,
            String pozicija, String engleski,
            String dostupnost, String angazovanje) {

        int score = 0, maxScore = 0, ispunjeno = 0, ukupnoFiltera = 0;

        List<String> pTechLower = p.getTehnologije() != null
                ? p.getTehnologije().stream().map(String::toLowerCase).collect(Collectors.toList())
                : List.of();

        if (!trazene.isEmpty()) {
            maxScore += 25; ukupnoFiltera++;
            long match = trazene.stream().filter(t -> pTechLower.stream()
                    .anyMatch(pt -> pt.contains(t) || t.contains(pt))).count();
            int ts = (int)((double) match / trazene.size() * 25);
            score += ts; if (ts > 0) ispunjeno++;
        }
        if (nivo != null) {
            maxScore += 15; ukupnoFiltera++;
            String pN = p.getNivo() != null ? p.getNivo().toLowerCase() : "";
            String tN = nivo.toLowerCase();
            if (pN.equals(tN) || (tN.equals("mid") && pN.equals("medior"))
                    || (tN.equals("medior") && pN.equals("mid")))
            { score += 15; ispunjeno++; }
        }
        if (grad != null) {
            maxScore += 10; ukupnoFiltera++;
            if (p.getGrad() != null && p.getGrad().toLowerCase().contains(grad.toLowerCase()))
            { score += 10; ispunjeno++; }
        }
        if (nacinRada != null) {
            maxScore += 10; ukupnoFiltera++;
            if (p.getNacinRada() != null && p.getNacinRada().toLowerCase().contains(nacinRada.toLowerCase()))
            { score += 10; ispunjeno++; }
        }
        if (pozicija != null) {
            maxScore += 10; ukupnoFiltera++;
            if (p.getPozicija() != null
                    && (p.getPozicija().toLowerCase().contains(pozicija.toLowerCase())
                    || pozicija.toLowerCase().contains(p.getPozicija().toLowerCase())))
            { score += 10; ispunjeno++; }
        }
        if (engleski != null) {
            maxScore += 8; ukupnoFiltera++;
            Integer tR = ENG_RANK.get(engleski.toLowerCase());
            Integer pR = p.getEngleski() != null ? ENG_RANK.get(p.getEngleski().toLowerCase()) : null;
            if (tR != null && pR != null && pR >= tR) { score += 8; ispunjeno++; }
        }
        if (dostupnost != null) {
            maxScore += 8; ukupnoFiltera++;
            Map<String, Integer> dr = Map.of("odmah",1,"15dana",2,"mesec",3,"2meseca",4);
            Integer tR = dr.get(dostupnost.toLowerCase());
            Integer pR = p.getDostupnost() != null ? dr.get(p.getDostupnost().toLowerCase()) : null;
            if (tR != null && pR != null && pR <= tR) { score += 8; ispunjeno++; }
        }
        if (angazovanje != null) {
            maxScore += 8; ukupnoFiltera++;
            if (p.getAngazovanje() != null && p.getAngazovanje().equalsIgnoreCase(angazovanje))
            { score += 8; ispunjeno++; }
        }
        if (minIsk != null && minIsk > 0) {
            maxScore += 6; ukupnoFiltera++;
            int pIsk = p.getIskustvo() != null ? p.getIskustvo() : 0;
            if (pIsk >= minIsk) { score += 6; ispunjeno++; }
        }
        if (maxIsk != null && maxIsk < 50) {
            maxScore += 4; ukupnoFiltera++;
            int pIsk = p.getIskustvo() != null ? p.getIskustvo() : 0;
            if (pIsk <= maxIsk) { score += 4; ispunjeno++; }
        }
        if (minPlata != null && minPlata > 0) {
            maxScore += 6; ukupnoFiltera++;
            if (p.getPlata() != null && p.getPlata() >= minPlata) { score += 6; ispunjeno++; }
        }
        if (maxPlata != null && maxPlata < 999999) {
            maxScore += 6; ukupnoFiltera++;
            if (p.getPlata() == null || p.getPlata() <= maxPlata) { score += 6; ispunjeno++; }
        }
        if (Boolean.TRUE.equals(imaCv)) {
            maxScore += 3; ukupnoFiltera++;
            if (p.getCv() != null && !p.getCv().isBlank()) { score += 3; ispunjeno++; }
        }
        if (Boolean.TRUE.equals(imaGithub)) {
            maxScore += 3; ukupnoFiltera++;
            if (p.getGithub() != null && !p.getGithub().isBlank()) { score += 3; ispunjeno++; }
        }
        if (q != null) {
            maxScore += 5; ukupnoFiltera++;
            String ql = q.toLowerCase();
            boolean hit = (p.getIme()  != null && p.getIme().toLowerCase().contains(ql))
                    || (p.getOpis() != null && p.getOpis().toLowerCase().contains(ql))
                    || (p.getGrad() != null && p.getGrad().toLowerCase().contains(ql))
                    || pTechLower.stream().anyMatch(t -> t.contains(ql) || ql.contains(t));
            if (hit) { score += 5; ispunjeno++; }
        }

        int finalScore = maxScore > 0
                ? Math.min(100, (int) Math.round((double) score / maxScore * 100)) : 0;
        return new MatchResult(finalScore, ispunjeno + "/" + ukupnoFiltera);
    }

    // ══════════════════════════════════════════════════════════════════
    //  OSTALI ENDPOINTI
    // ══════════════════════════════════════════════════════════════════

    @GetMapping("/{id}")
    public ResponseEntity<?> getJedan(@PathVariable Long id, HttpServletRequest request) {
        if (!rateLimiter.dozvoljenaAkcija(getIp(request)))
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();

        boolean puniPristup = imaPuniPristup(request);

        return programerRepo.findById(id).filter(Programer::getAktivan)
                .map(p -> ResponseEntity.ok(buildDto(p, null, null, puniPristup)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/vlasnik/{korisnikId}")
    public ResponseEntity<?> getVlasnikov(@PathVariable Long korisnikId, HttpServletRequest request) {
        Long tokenId = (Long) request.getAttribute("korisnikId");
        if (tokenId == null || !tokenId.equals(korisnikId))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("greska", "Nemate pristup."));
        // Vlasnik uvek vidi sve svoje podatke
        return programerRepo.findByKorisnikIdAndAktivanTrue(korisnikId)
                .map(p -> ResponseEntity.ok(buildDto(p, null, null, true)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> kreiraj(@Valid @RequestBody Programer programer, HttpServletRequest request) {
        Long korisnikId = (Long) request.getAttribute("korisnikId");
        String tip      = (String) request.getAttribute("korisnikTip");
        if (korisnikId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        boolean jeAdmin = "admin".equalsIgnoreCase(tip);
        if (!jeAdmin && programerRepo.findByKorisnikIdAndAktivanTrue(korisnikId).isPresent())
            return ResponseEntity.badRequest().body(Map.of("greska", "Vec imate kreiran profil."));
        // Validacija URL-ova — sprečava maliciozne linkove i viruse
        String githubGreska = validirajGithubUrl(programer.getGithub());
        if (githubGreska != null)
            return ResponseEntity.badRequest().body(Map.of("greska", githubGreska));

        String cvGreska = validirajCvUrl(programer.getCv());
        if (cvGreska != null)
            return ResponseEntity.badRequest().body(Map.of("greska", cvGreska));

        programer.setKorisnikId(korisnikId);
        programer.setAktivan(true);
        programer.setBrojPregleda(0);
        if (programer.getIskustvo() == null) programer.setIskustvo(0);
        if (programer.getIme()  != null) programer.setIme(sanitize(programer.getIme()));
        if (programer.getOpis() != null) programer.setOpis(sanitize(programer.getOpis()));

        // Sanitizacija enum polja — nepoznate vrijednosti se ignorišu, HTML se uklanja
        programer.setNivo(sanitizeEnum(programer.getNivo(), "Junior", "Medior", "Senior"));
        programer.setNacinRada(sanitizeEnum(programer.getNacinRada(),
                "Remote", "Hibridno", "Kancelarija",
                "Remote, Hibridno", "Hibridno, Kancelarija", "Remote, Hibridno, Kancelarija"));
        programer.setEngleski(sanitizeEnum(programer.getEngleski(), "A2", "B1", "B2", "C1", "C2"));
        programer.setDostupnost(sanitizeEnum(programer.getDostupnost(), "odmah", "15dana", "mesec", "2meseca"));
        programer.setAngazovanje(sanitizeEnum(programer.getAngazovanje(), "Fulltime", "Parttime", "Freelance", "Praksa"));

        // Sanitizacija slobodnih tekstualnih polja
        if (programer.getGrad() != null) programer.setGrad(sanitize(programer.getGrad()));
        if (programer.getPozicija() != null) programer.setPozicija(sanitize(programer.getPozicija()));
        if (programer.getEdukacija() != null) programer.setEdukacija(sanitize(programer.getEdukacija()));
        // Sanitizacija svake tehnologije pojedinačno
        if (programer.getTehnologije() != null) {
            programer.setTehnologije(
                    programer.getTehnologije().stream()
                            .filter(t -> t != null && !t.isBlank())
                            .map(t -> sanitize(t))
                            .filter(t -> t != null && !t.isBlank() && t.length() <= 50)
                            .distinct()
                            .limit(30)
                            .collect(java.util.stream.Collectors.toList())
            );
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(programerRepo.save(programer));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> izmeni(@PathVariable Long id, @Valid @RequestBody Programer izmene,
                                    HttpServletRequest request) {
        Long korisnikId = (Long) request.getAttribute("korisnikId");
        String tip      = (String) request.getAttribute("korisnikTip");
        Optional<Programer> opt = programerRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Programer p = opt.get();
        if (!p.getKorisnikId().equals(korisnikId) && !"admin".equalsIgnoreCase(tip))
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("greska", "Nemate dozvolu za izmenu ovog profila."));
        // Validacija URL-ova — sprečava maliciozne linkove i viruse
        if (izmene.getGithub() != null) {
            String gr = validirajGithubUrl(izmene.getGithub());
            if (gr != null) return ResponseEntity.badRequest().body(Map.of("greska", gr));
        }
        if (izmene.getCv() != null) {
            String cr = validirajCvUrl(izmene.getCv());
            if (cr != null) return ResponseEntity.badRequest().body(Map.of("greska", cr));
        }

        if (izmene.getIme()         != null) p.setIme(sanitize(izmene.getIme()));
        if (izmene.getGrad()        != null) p.setGrad(izmene.getGrad());
        if (izmene.getIskustvo()    != null) p.setIskustvo(izmene.getIskustvo());
        if (izmene.getEdukacija()   != null) p.setEdukacija(izmene.getEdukacija());
        if (izmene.getPlata()       != null) p.setPlata(izmene.getPlata());
        if (izmene.getGithub()      != null) p.setGithub(izmene.getGithub());
        if (izmene.getCv()          != null) p.setCv(izmene.getCv());
        if (izmene.getOpis()        != null) p.setOpis(sanitize(izmene.getOpis()));
        if (izmene.getGrad()        != null) p.setGrad(sanitize(izmene.getGrad()));
        if (izmene.getPozicija()    != null) p.setPozicija(sanitize(izmene.getPozicija()));
        if (izmene.getEdukacija()   != null) p.setEdukacija(sanitize(izmene.getEdukacija()));
        if (izmene.getTehnologije() != null) {
            p.setTehnologije(
                    izmene.getTehnologije().stream()
                            .filter(t -> t != null && !t.isBlank())
                            .map(t -> sanitize(t))
                            .filter(t -> t != null && !t.isBlank() && t.length() <= 50)
                            .distinct()
                            .limit(30)
                            .collect(java.util.stream.Collectors.toList())
            );
        }

        // Enum polja — sanitizacija i whitelist provjera
        if (izmene.getNivo()        != null) p.setNivo(sanitizeEnum(izmene.getNivo(),
                "Junior", "Medior", "Senior"));
        if (izmene.getNacinRada()   != null) p.setNacinRada(sanitizeEnum(izmene.getNacinRada(),
                "Remote", "Hibridno", "Kancelarija",
                "Remote, Hibridno", "Hibridno, Kancelarija", "Remote, Hibridno, Kancelarija"));
        if (izmene.getEngleski()    != null) p.setEngleski(sanitizeEnum(izmene.getEngleski(),
                "A2", "B1", "B2", "C1", "C2"));
        if (izmene.getDostupnost()  != null) p.setDostupnost(sanitizeEnum(izmene.getDostupnost(),
                "odmah", "15dana", "mesec", "2meseca"));
        if (izmene.getAngazovanje() != null) p.setAngazovanje(sanitizeEnum(izmene.getAngazovanje(),
                "Fulltime", "Parttime", "Freelance", "Praksa"));

        return ResponseEntity.ok(programerRepo.save(p));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> obrisi(@PathVariable Long id, HttpServletRequest request) {
        Long korisnikId = (Long) request.getAttribute("korisnikId");
        String tip      = (String) request.getAttribute("korisnikTip");
        Optional<Programer> opt = programerRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Programer p = opt.get();
        if (!p.getKorisnikId().equals(korisnikId) && !"admin".equalsIgnoreCase(tip))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("greska", "Nemate dozvolu."));
        p.setAktivan(false);
        programerRepo.save(p);
        return ResponseEntity.ok(Map.of("poruka", "Profil uspesno obrisan."));
    }

    @PostMapping("/{id}/klik")
    public ResponseEntity<?> klik(@PathVariable Long id, HttpServletRequest request) {
        if (!rateLimiter.dozvoljenaAkcija(getIp(request)))
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();

        Long tokenKorisnikId = (Long) request.getAttribute("korisnikId");
        if (tokenKorisnikId != null) {
            boolean jeVlasnik = programerRepo.findById(id)
                    .map(p -> tokenKorisnikId.equals(p.getKorisnikId()))
                    .orElse(false);
            if (jeVlasnik) {
                return ResponseEntity.ok(Map.of("ok", true, "preskoceno", true));
            }
        }

        programerRepo.incrementPregleda(id);
        return ResponseEntity.ok(Map.of("ok", true, "preskoceno", false));
    }

    @GetMapping("/{id}/statistika")
    public ResponseEntity<?> statistika(@PathVariable Long id) {
        return programerRepo.findById(id).filter(Programer::getAktivan)
                .map(p -> {
                    Long sacuvanja = programerRepo.countSacuvanja(id);
                    Map<String, Object> stat = new LinkedHashMap<>();
                    stat.put("id",            p.getId());
                    stat.put("ime",           p.getIme());
                    stat.put("grad",          p.getGrad());
                    stat.put("nivo",          p.getNivo());
                    stat.put("iskustvo",      p.getIskustvo());
                    stat.put("tehnologije",   p.getTehnologije());
                    stat.put("brojPregleda",  p.getBrojPregleda());
                    stat.put("brojSacuvanja", sacuvanja);
                    stat.put("kreiranDatum",  p.getKreiranDatum());
                    stat.put("pozicija",      p.getPozicija());
                    // NAMERNO ne vraćamo: plata, CV, GitHub, opis, email
                    return ResponseEntity.ok(stat);
                }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/slicni")
    public ResponseEntity<?> slicni(@PathVariable Long id,
                                    @RequestParam(defaultValue = "4") int limit) {
        return programerRepo.findById(id)
                .filter(Programer::getAktivan)
                .map(p -> {
                    List<String> techs = p.getTehnologije();
                    if (techs == null || techs.isEmpty()) {
                        return ResponseEntity.ok(List.of());
                    }
                    int ukupno = techs.size();
                    int limit4 = Math.min(limit, 8);
                    int[] pragovi = {
                            Math.max(1, (int) Math.ceil(ukupno * 0.7)),
                            Math.max(1, (int) Math.ceil(ukupno * 0.5)),
                            1
                    };
                    List<Object[]> rows = List.of();
                    for (int prag : pragovi) {
                        rows = programerRepo.nadjiSlicne(id, techs, ukupno, prag, limit4);
                        if (!rows.isEmpty()) break;
                    }
                    List<Map<String, Object>> rezultat = rows.stream().map(r -> {
                        Map<String, Object> dto = new LinkedHashMap<>();
                        dto.put("id",           r[0]);
                        dto.put("ime",          r[1]);
                        dto.put("nivo",         r[2]);
                        dto.put("grad",         r[3]);
                        dto.put("pozicija",     r[4]);
                        dto.put("nacinRada",    r[5]);
                        dto.put("iskustvo",     r[6]);
                        dto.put("brojPregleda", r[7]);
                        dto.put("zajednicke",   r[8]);
                        double proc = r[9] instanceof Number ? ((Number) r[9]).doubleValue() : 0;
                        dto.put("procenat",     (int) Math.round(proc));
                        // NAMERNO ne vraćamo: plata, cv, github — slicni prikaz nema potrebu za tim
                        return dto;
                    }).collect(Collectors.toList());
                    return ResponseEntity.ok(rezultat);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ══════════════════════════════════════════════════════════════════
    //  DTO BUILDER — centralno mesto za kontrolu vidljivosti podataka
    //
    //  puniPristup = true  → firma ili admin → vide platu, CV, GitHub, opis
    //  puniPristup = false → neregistrovani/programer → vide samo javne podatke
    // ══════════════════════════════════════════════════════════════════
    private Map<String, Object> buildDto(Programer p, Integer matchScore,
                                         String poklapanja, boolean puniPristup) {
        Map<String, Object> dto = new LinkedHashMap<>();

        // ── Uvek javni podaci ──
        dto.put("id",           p.getId());
        dto.put("korisnikId",   p.getKorisnikId());
        dto.put("ime",          p.getIme());
        dto.put("grad",         p.getGrad());
        dto.put("iskustvo",     p.getIskustvo());
        dto.put("nivo",         p.getNivo());
        dto.put("nacinRada",    p.getNacinRada());
        dto.put("tehnologije",  p.getTehnologije());
        dto.put("kreiranDatum", p.getKreiranDatum());
        dto.put("brojPregleda", p.getBrojPregleda());
        dto.put("pozicija",     p.getPozicija());
        dto.put("engleski",     p.getEngleski());
        dto.put("dostupnost",   p.getDostupnost());
        dto.put("angazovanje",  p.getAngazovanje());
        dto.put("edukacija",    p.getEdukacija());

        // ── Privatni podaci — samo za firme i admina ──
        if (puniPristup) {
            dto.put("plata",  p.getPlata());
            dto.put("cv",     p.getCv());
            dto.put("github", p.getGithub());
            dto.put("opis",   p.getOpis());
        } else {
            // Eksplicitno null — frontend zna da treba da prikaže "zaključano"
            dto.put("plata",  null);
            dto.put("cv",     null);
            dto.put("github", null);
            dto.put("opis",   null);
        }

        if (matchScore != null) {
            dto.put("matchScore",  matchScore);
            dto.put("poklapanja",  poklapanja);
        }
        return dto;
    }

    private Map<String, Object> buildResponse(List<Map<String, Object>> sadrzaj, int page,
                                              int ukupnoStrana, long ukupnoElemenata,
                                              int size, boolean poslednja) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("sadrzaj",          sadrzaj);
        r.put("trenutnaStrana",   page);
        r.put("ukupnoStrana",     ukupnoStrana);
        r.put("ukupnoElemenata",  ukupnoElemenata);
        r.put("velicinaStrane",   size);
        r.put("poslednja",        poslednja);
        return r;
    }

    private boolean blank(String s) { return s == null || s.isBlank(); }

    private String getIp(HttpServletRequest req) {
        String fwd = req.getHeader("X-Forwarded-For");
        return fwd != null ? fwd.split(",")[0].trim() : req.getRemoteAddr();
    }

    private String sanitize(String input) {
        if (input == null) return null;
        return input.replaceAll("<[^>]*>", "").trim();
    }

    /**
     * Validacija URL-a za GitHub i CV linkove.
     *
     * Pravila:
     * - Mora počinjati sa https:// (ne http://, ne javascript:, ne data:)
     * - Max 500 karaktera
     * - GitHub mora sadržati github.com
     * - CV može biti Google Drive, Dropbox, OneDrive, ili direktan PDF link
     *
     * Vraća poruku greške ako nije validan, null ako je ok ili prazan.
     */
    private String validirajGithubUrl(String url) {
        if (url == null || url.isBlank()) return null; // prazno je ok
        url = url.trim();
        if (url.length() > 500)
            return "GitHub URL je predugačak (max 500 karaktera).";
        if (!url.startsWith("https://"))
            return "GitHub link mora počinjati sa https://.";
        if (!url.toLowerCase().contains("github.com"))
            return "GitHub link mora sadržati github.com.";
        // Blokira javascript: i data: protocol injections koje bi preživjele https provjeru
        if (url.toLowerCase().contains("javascript:") || url.toLowerCase().contains("data:"))
            return "GitHub link nije ispravan.";
        return null;
    }

    private String validirajCvUrl(String url) {
        if (url == null || url.isBlank()) return null; // prazno je ok
        url = url.trim();
        if (url.length() > 500)
            return "CV URL je predugačak (max 500 karaktera).";
        if (!url.startsWith("https://"))
            return "CV link mora počinjati sa https://.";
        if (url.toLowerCase().contains("javascript:") || url.toLowerCase().contains("data:"))
            return "CV link nije ispravan.";
        // Dozvoljeni domeni za CV: Google Drive, Dropbox, OneDrive, i direktni PDF linkovi
        String lower = url.toLowerCase();
        boolean dozvoljenDomen =
                lower.contains("drive.google.com") ||
                        lower.contains("docs.google.com") ||
                        lower.contains("dropbox.com") ||
                        lower.contains("onedrive.live.com") ||
                        lower.contains("1drv.ms") ||
                        lower.contains("sharepoint.com") ||
                        lower.contains("notion.so") ||
                        lower.contains("github.com") ||
                        lower.contains("linkedin.com") ||
                        lower.endsWith(".pdf");
        if (!dozvoljenDomen)
            return "CV link mora biti Google Drive, Dropbox, OneDrive, ili direktan PDF link (https://.../cv.pdf).";
        return null;
    }

    /**
     * Sanitizuje enum polja — uklanja HTML tagove i ograničava na poznate vrijednosti.
     * Ako vrijednost nije u listi dozvoljenih, vraća null (polje se ignoriše).
     */
    private String sanitizeEnum(String input, String... dozvoljene) {
        if (input == null) return null;
        String clean = input.replaceAll("<[^>]*>", "").trim();
        for (String d : dozvoljene) {
            if (d.equalsIgnoreCase(clean)) return d; // vraća kanonski oblik
        }
        return null; // nepoznata vrijednost — ignorišemo
    }
}