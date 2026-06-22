package com.example.devbaza.Ai;

import com.example.devbaza.programer.Programer;
import com.example.devbaza.programer.ProgramerRepository;
import com.example.devbaza.security.RateLimiterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-latest:generateContent?key=";

    // Bez default vrednosti — mora biti u env varijabli ili ostaje prazan string
    @Value("${gemini.api.key:}")
    private String geminiKey;

    @Autowired private RestTemplate restTemplate;
    @Autowired private RateLimiterService rateLimiter;
    @Autowired private ProgramerRepository programerRepo;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Maksimalna dužina query-ja — sprečava prompt injection i preskupe Gemini pozive
    private static final int MAX_QUERY_LENGTH = 500;

    @PostMapping("/search")
    public ResponseEntity<?> aiSearch(@RequestBody Map<String, String> body,
                                      HttpServletRequest request) {

        String ip = getIp(request);
        if (!rateLimiter.dozvoljenaAkcija(ip)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("greska", "Previše zahteva. Sačekaj minut."));
        }

        String query = body.get("query");
        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("greska", "Query je prazan."));
        }

        // Ograničava dužinu — sprečava preskupe Gemini API pozive i prompt injection
        query = query.trim();
        if (query.length() > MAX_QUERY_LENGTH) {
            query = query.substring(0, MAX_QUERY_LENGTH);
        }

        // Uklanja HTML tagove iz query-ja
        query = query.replaceAll("<[^>]*>", "").trim();

        if (query.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("greska", "Query je prazan nakon sanitizacije."));
        }

        // ── Pokušaj parsiranja kroz Gemini, inače prazna mapa
        Map<String, Object> kriterijumi = new HashMap<>();
        boolean geminiDostupan = geminiKey != null
                && !geminiKey.isBlank()
                && !geminiKey.startsWith("POSTAVI")
                && !geminiKey.startsWith("PROMENI");

        if (geminiDostupan) {
            try {
                kriterijumi = parseQueryGemini(query);
            } catch (Exception e) {
                // Gemini nedostupan ili greška — nastavljamo bez AI parsiranja
                // Ne logujemo poruku greške jer može sadržati API key u stack traceu
            }
        }

        // ── Izvuci filtere iz AI odgovora
        String grad        = getString(kriterijumi, "grad");
        String nivo        = getString(kriterijumi, "nivo");
        String nacinRada   = getString(kriterijumi, "nacinRada");
        String pozicija    = getString(kriterijumi, "pozicija");
        String engleski    = getString(kriterijumi, "engleski");
        String dostupnost  = getString(kriterijumi, "dostupnost");
        String angazovanje = getString(kriterijumi, "angazovanje");
        Integer maxPlata   = getInteger(kriterijumi, "maxPlata");
        Integer minPlata   = getInteger(kriterijumi, "minPlata");
        List<String> techLista = getTechList(kriterijumi);
        String techParam   = techLista.isEmpty() ? null : String.join(",", techLista);

        String qParam = extrakoujKljucneReci(query, nivo, grad, techLista, pozicija);

        // Limit na 50 kandidata iz DB — razumno za AI scoring
        Pageable pageable = PageRequest.of(0, 50);
        List<Programer> kandidati = List.of();

        // ── Pokušaj 1: puni filteri
        kandidati = pokusajPretragu(nivo, grad, minPlata, maxPlata,
                nacinRada, techParam, qParam,
                pozicija, engleski, dostupnost, angazovanje, pageable);

        // ── Pokušaj 2: samo keyword ako nema rezultata
        if (kandidati.isEmpty() && qParam != null) {
            kandidati = pokusajPretragu(null, null, null, null,
                    null, null, qParam,
                    null, null, null, null, pageable);
        }

        // ── Pokušaj 3: samo tehnologije
        if (kandidati.isEmpty() && techParam != null) {
            kandidati = pokusajPretragu(null, null, null, null,
                    null, techParam, null,
                    null, null, null, null, pageable);
        }

        // ── Pokušaj 4: samo nivo
        if (kandidati.isEmpty() && nivo != null) {
            kandidati = pokusajPretragu(nivo, null, null, null,
                    null, null, null,
                    null, null, null, null, pageable);
        }

        if (kandidati.isEmpty()) return ResponseEntity.ok(List.of());

        final List<String> ft = techLista;
        final String fn = nivo, fg = grad, fr = nacinRada, fp = pozicija;
        final String ql = query.toLowerCase();

        List<AIKandidatDTO> rezultati = kandidati.stream()
                .map(p -> napraviDTO(p,
                        izracunajProcenat(p, ql, ft, fn, fg, fr, fp),
                        generisiObrazlozenje(p, ft, fn, fg, fp)))
                .filter(dto -> dto.getProcenat() >= 20)
                .sorted(Comparator.comparingInt(AIKandidatDTO::getProcenat).reversed())
                .limit(20)
                .collect(Collectors.toList());

        return ResponseEntity.ok(rezultati);
    }

    private List<Programer> pokusajPretragu(
            String nivo, String grad, Integer minPlata, Integer maxPlata,
            String nacinRada, String tech, String q,
            String pozicija, String engleski, String dostupnost, String angazovanje,
            Pageable pageable) {
        try {
            return programerRepo.filtriraj(
                    nivo, grad, null, null, minPlata, maxPlata,
                    nacinRada, tech, q,
                    null, null,
                    pozicija, engleski, dostupnost, angazovanje,
                    pageable
            ).getContent();
        } catch (Exception e) {
            return List.of();
        }
    }

    private AIKandidatDTO napraviDTO(Programer p, int procenat, String obrazlozenje) {
        AIKandidatDTO dto = new AIKandidatDTO();
        dto.setId(p.getId());
        dto.setProcenat(procenat);
        dto.setObrazlozenje(obrazlozenje);
        dto.setIme(p.getIme());
        dto.setGrad(p.getGrad());
        dto.setNivo(p.getNivo());
        dto.setIskustvo(p.getIskustvo());
        dto.setPlata(p.getPlata());
        dto.setNacinRada(p.getNacinRada());
        dto.setTehnologije(p.getTehnologije());
        dto.setKorisnikId(p.getKorisnikId());
        dto.setOpis(p.getOpis());
        dto.setGithub(p.getGithub());
        dto.setPozicija(p.getPozicija());
        dto.setEngleski(p.getEngleski());
        dto.setDostupnost(p.getDostupnost());
        dto.setAngazovanje(p.getAngazovanje());
        return dto;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseQueryGemini(String query) throws Exception {
        // Query je već sanitizovan i ograničen na 500 karaktera pre ovog poziva
        String prompt = """
            Analiziraj ovu pretragu za IT developer platformu i ekstrahuj filtere.
            Pretraga: "%s"
            
            Vrati SAMO validan JSON, bez objasnjenja, bez markdown backtick-a:
            {
              "grad": string ili null,
              "nivo": "junior" ili "mid" ili "senior" ili null,
              "tehnologije": [lista stringova] ili [],
              "nacinRada": "Remote" ili "Hibridno" ili "Kancelarija" ili null,
              "pozicija": string (npr "Frontend Developer", "Backend Developer") ili null,
              "engleski": "A2" ili "B1" ili "B2" ili "C1" ili "C2" ili null,
              "dostupnost": "odmah" ili "15dana" ili "mesec" ili "2meseca" ili null,
              "angazovanje": "Fulltime" ili "Parttime" ili "Freelance" ili "Praksa" ili null,
              "minPlata": broj ili null,
              "maxPlata": broj ili null
            }
            """.formatted(query);

        Map<String, Object> reqBody = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", prompt))
                ))
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> resp = restTemplate.exchange(
                GEMINI_URL + geminiKey,
                HttpMethod.POST,
                new HttpEntity<>(reqBody, headers),
                Map.class
        );

        if (resp.getBody() == null) return new HashMap<>();

        List<Map<String, Object>> candidates =
                (List<Map<String, Object>>) resp.getBody().get("candidates");
        if (candidates == null || candidates.isEmpty()) return new HashMap<>();

        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        if (content == null) return new HashMap<>();

        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        if (parts == null || parts.isEmpty()) return new HashMap<>();

        String tekst = (String) parts.get(0).get("text");
        if (tekst == null || tekst.isBlank()) return new HashMap<>();

        tekst = tekst.replaceAll("```json|```", "").trim();

        return objectMapper.readValue(tekst, Map.class);
    }

    private int izracunajProcenat(Programer p, String queryLower,
                                  List<String> techLista, String nivo,
                                  String grad, String nacinRada, String pozicija) {
        int score = 0, maxScore = 0;

        List<String> pTechLower = p.getTehnologije() != null
                ? p.getTehnologije().stream().map(String::toLowerCase).toList()
                : List.of();

        if (!techLista.isEmpty()) {
            maxScore += 40;
            long match = techLista.stream()
                    .filter(t -> pTechLower.stream()
                            .anyMatch(pt -> pt.contains(t.toLowerCase())
                                    || t.toLowerCase().contains(pt)))
                    .count();
            score += (int) ((double) match / techLista.size() * 40);
        }

        if (nivo != null) {
            maxScore += 20;
            String pNivo = p.getNivo() != null ? p.getNivo().toLowerCase() : "";
            String tNivo = nivo.toLowerCase();
            boolean ok = pNivo.equals(tNivo)
                    || (tNivo.equals("mid") && pNivo.equals("medior"))
                    || (tNivo.equals("medior") && pNivo.equals("mid"));
            if (ok) score += 20;
        }

        if (grad != null) {
            maxScore += 15;
            if (p.getGrad() != null
                    && p.getGrad().toLowerCase().contains(grad.toLowerCase()))
                score += 15;
        }

        if (pozicija != null) {
            maxScore += 15;
            if (p.getPozicija() != null
                    && (p.getPozicija().toLowerCase().contains(pozicija.toLowerCase())
                    ||  pozicija.toLowerCase().contains(p.getPozicija().toLowerCase())))
                score += 15;
        }

        if (nacinRada != null) {
            maxScore += 10;
            if (p.getNacinRada() != null
                    && p.getNacinRada().toLowerCase().contains(nacinRada.toLowerCase()))
                score += 10;
        }

        maxScore += 10;
        long kwMatch = Arrays.stream(queryLower.split("\\s+"))
                .filter(r -> r.length() > 3)
                .filter(r -> (p.getIme()  != null && p.getIme().toLowerCase().contains(r))
                        ||   (p.getOpis() != null && p.getOpis().toLowerCase().contains(r)))
                .count();
        if (kwMatch > 0) score += Math.min(10, (int) (kwMatch * 3));

        if (maxScore == 0) return 50;
        return Math.min(100, (int) ((double) score / maxScore * 100));
    }

    private String generisiObrazlozenje(Programer p, List<String> techLista,
                                        String nivo, String grad, String pozicija) {
        List<String> razlozi = new ArrayList<>();
        List<String> pTech = p.getTehnologije() != null ? p.getTehnologije() : List.of();

        List<String> poklapaju = techLista.stream()
                .filter(t -> pTech.stream()
                        .anyMatch(pt -> pt.toLowerCase().contains(t.toLowerCase())))
                .toList();

        if (!poklapaju.isEmpty())
            razlozi.add("Poznaje: " + String.join(", ", poklapaju));

        if (nivo != null && p.getNivo() != null) {
            String pNivo = p.getNivo().toLowerCase();
            boolean ok = pNivo.equals(nivo.toLowerCase())
                    || (nivo.equalsIgnoreCase("mid") && pNivo.equals("medior"));
            if (ok) razlozi.add(p.getNivo() + " nivo ✓");
        }

        if (grad != null && p.getGrad() != null
                && p.getGrad().toLowerCase().contains(grad.toLowerCase()))
            razlozi.add("Iz " + p.getGrad());

        if (pozicija != null && p.getPozicija() != null
                && (p.getPozicija().toLowerCase().contains(pozicija.toLowerCase())
                ||  pozicija.toLowerCase().contains(p.getPozicija().toLowerCase())))
            razlozi.add(p.getPozicija());

        if (p.getEngleski() != null && !p.getEngleski().isBlank())
            razlozi.add("Engleski: " + p.getEngleski());

        if (p.getDostupnost() != null) {
            String d = switch (p.getDostupnost().toLowerCase()) {
                case "odmah"   -> "Odmah dostupan";
                case "15dana"  -> "Do 15 dana";
                case "mesec"   -> "Do mesec dana";
                case "2meseca" -> "Do 2 meseca";
                default        -> p.getDostupnost();
            };
            razlozi.add(d);
        }

        return razlozi.isEmpty() ? "Odgovara kriterijumima" : String.join(" · ", razlozi);
    }

    private String extrakoujKljucneReci(String query, String nivo, String grad,
                                        List<String> tech, String pozicija) {
        if (query == null || query.isBlank()) return null;

        Set<String> ignorisi = new HashSet<>(Arrays.asList(
                "koji", "se", "zove", "trazim", "trebam", "hocu", "zelim",
                "developer", "programer", "developera", "programera", "dev",
                "iz", "za", "sa", "u", "i", "ili", "je", "su", "ima",
                "treba", "moze", "radi", "radno", "mesto", "the", "and",
                "with", "for", "from", "that", "have", "need", "want",
                "junior", "senior", "mid", "medior", "remote", "hibridno",
                "kancelarija", "office", "hybrid", "odmah", "dostupan",
                "fulltime", "freelance", "parttime", "praksa"
        ));

        if (nivo     != null) ignorisi.add(nivo.toLowerCase());
        if (grad     != null) ignorisi.add(grad.toLowerCase());
        if (pozicija != null) Arrays.stream(pozicija.toLowerCase().split("\\s+"))
                .forEach(ignorisi::add);
        tech.forEach(t -> ignorisi.add(t.toLowerCase()));

        String kljucne = Arrays.stream(query.toLowerCase().split("\\s+"))
                .filter(r -> r.length() > 2)
                .filter(r -> !ignorisi.contains(r))
                .collect(Collectors.joining(" "));

        return kljucne.isBlank() ? null : kljucne;
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null || "null".equalsIgnoreCase(val.toString())) return null;
        String s = val.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private Integer getInteger(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;
        try {
            int parsed = Integer.parseInt(val.toString());
            // Sanitizujemo platu da bude u razumnom opsegu
            return Math.max(0, Math.min(100_000, parsed));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> getTechList(Map<String, Object> map) {
        Object val = map.get("tehnologije");
        if (val instanceof List) {
            return ((List<?>) val).stream()
                    .map(Object::toString)
                    .filter(s -> !s.isBlank() && s.length() <= 50)
                    .limit(15) // Max 15 tehnologija — sprečava preskupe upite
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private String getIp(HttpServletRequest req) {
        String fwd = req.getHeader("X-Forwarded-For");
        return fwd != null ? fwd.split(",")[0].trim() : req.getRemoteAddr();
    }
}