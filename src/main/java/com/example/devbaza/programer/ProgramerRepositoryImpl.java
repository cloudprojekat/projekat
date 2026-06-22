package com.example.devbaza.programer;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class ProgramerRepositoryImpl implements ProgramerRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    private static final Map<String, String> SORT_FIELD_MAP = Map.of(
            "kreiranDatum", "kreiranDatum",
            "iskustvo",     "iskustvo",
            "plata",        "plata",
            "brojPregleda", "brojPregleda",
            "ime",          "ime"
    );

    // Rang engleskog za "minimum nivo" filtriranje
    private static final Map<String, Integer> ENG_RANK = Map.of(
            "a2", 1, "b1", 2, "b2", 3, "c1", 4, "c2", 5
    );

    @Override
    public Page<Programer> filtriraj(
            String nivo, String grad,
            Integer minIskustvo, Integer maxIskustvo,
            Integer minPlata, Integer maxPlata,
            String nacinRada, String tech, String q,
            Boolean imaCv, Boolean imaGithub,
            String pozicija, String engleski,
            String dostupnost, String angazovanje,
            Pageable pageable) {

        CriteriaBuilder cb = em.getCriteriaBuilder();

        // --- COUNT query ---
        CriteriaQuery<Long> countQ = cb.createQuery(Long.class);
        Root<Programer> cr = countQ.from(Programer.class);
        countQ.select(cb.countDistinct(cr));
        countQ.where(buildPredicates(cb, cr, countQ,
                nivo, grad, minIskustvo, maxIskustvo, minPlata, maxPlata,
                nacinRada, tech, q, imaCv, imaGithub,
                pozicija, engleski, dostupnost, angazovanje));
        Long total = em.createQuery(countQ).getSingleResult();

        if (total == 0) return new PageImpl<>(List.of(), pageable, 0);

        // --- DATA query ---
        CriteriaQuery<Programer> dataQ = cb.createQuery(Programer.class);
        Root<Programer> root = dataQ.from(Programer.class);
        dataQ.select(root).distinct(true);
        dataQ.where(buildPredicates(cb, root, dataQ,
                nivo, grad, minIskustvo, maxIskustvo, minPlata, maxPlata,
                nacinRada, tech, q, imaCv, imaGithub,
                pozicija, engleski, dostupnost, angazovanje));

        List<Order> orders = new ArrayList<>();
        for (Sort.Order so : pageable.getSort()) {
            String fieldName = SORT_FIELD_MAP.getOrDefault(so.getProperty(), "kreiranDatum");
            try {
                orders.add(so.isAscending()
                        ? cb.asc(root.get(fieldName))
                        : cb.desc(root.get(fieldName)));
            } catch (IllegalArgumentException e) {
                orders.add(cb.desc(root.get("kreiranDatum")));
            }
        }
        if (orders.isEmpty()) orders.add(cb.desc(root.get("kreiranDatum")));
        dataQ.orderBy(orders);

        List<Programer> rezultati = em.createQuery(dataQ)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        return new PageImpl<>(rezultati, pageable, total);
    }

    private Predicate[] buildPredicates(
            CriteriaBuilder cb, Root<Programer> root, CriteriaQuery<?> query,
            String nivo, String grad,
            Integer minIskustvo, Integer maxIskustvo,
            Integer minPlata, Integer maxPlata,
            String nacinRada, String tech, String q,
            Boolean imaCv, Boolean imaGithub,
            String pozicija, String engleski,
            String dostupnost, String angazovanje) {

        List<Predicate> ps = new ArrayList<>();

        ps.add(cb.isTrue(root.get("aktivan")));

        // ── Nivo
        if (nivo != null && !nivo.isBlank()) {
            String nivoLower = nivo.toLowerCase().trim();
            switch (nivoLower) {
                case "mid" -> ps.add(cb.or(
                        cb.equal(cb.lower(root.get("nivo")), "mid"),
                        cb.equal(cb.lower(root.get("nivo")), "medior")));
                case "junior" -> ps.add(cb.equal(cb.lower(root.get("nivo")), "junior"));
                case "senior" -> ps.add(cb.equal(cb.lower(root.get("nivo")), "senior"));
                default       -> ps.add(cb.equal(cb.lower(root.get("nivo")), nivoLower));
            }
        }

        // ── Grad
        if (grad != null && !grad.isBlank()) {
            ps.add(cb.like(cb.lower(root.get("grad")),
                    "%" + grad.toLowerCase().trim() + "%"));
        }

        // ── Iskustvo
        if (minIskustvo != null) {
            ps.add(cb.or(
                    cb.isNull(root.get("iskustvo")),
                    cb.greaterThanOrEqualTo(root.get("iskustvo"), minIskustvo)));
        }
        if (maxIskustvo != null) {
            ps.add(cb.or(
                    cb.isNull(root.get("iskustvo")),
                    cb.lessThanOrEqualTo(root.get("iskustvo"), maxIskustvo)));
        }

        // ── Plata
        if (minPlata != null) {
            ps.add(cb.or(
                    cb.isNull(root.get("plata")),
                    cb.greaterThanOrEqualTo(root.get("plata"), minPlata)));
        }
        if (maxPlata != null) {
            ps.add(cb.or(
                    cb.isNull(root.get("plata")),
                    cb.lessThanOrEqualTo(root.get("plata"), maxPlata)));
        }

        // ── Način rada (OR logika: Remote,Hibridno → jedan od ta dva)
        if (nacinRada != null && !nacinRada.isBlank()) {
            String[] modes = nacinRada.split(",");
            List<Predicate> modePreds = new ArrayList<>();
            for (String mode : modes) {
                String m = mode.trim();
                if (!m.isEmpty()) {
                    modePreds.add(cb.like(cb.lower(root.get("nacinRada")),
                            "%" + m.toLowerCase() + "%"));
                }
            }
            if (!modePreds.isEmpty())
                ps.add(cb.or(modePreds.toArray(new Predicate[0])));
        }

        // ── Tehnologije (OR logika — bar jedna)
        if (tech != null && !tech.isBlank()) {
            String[] techList = tech.split(",");
            List<Predicate> techPreds = new ArrayList<>();
            for (String t : techList) {
                String trimmed = t.trim();
                if (!trimmed.isEmpty()) {
                    techPreds.add(cb.exists(techSubquery(cb, root, query, trimmed)));
                }
            }
            if (!techPreds.isEmpty()) {
                ps.add(cb.or(techPreds.toArray(new Predicate[0])));
            }
        }

        // ── Keyword search
        if (q != null && !q.isBlank()) {
            String ql = "%" + q.toLowerCase().trim() + "%";
            ps.add(cb.or(
                    cb.like(cb.lower(root.get("ime")),  ql),
                    cb.like(cb.lower(root.get("opis")), ql),
                    cb.like(cb.lower(root.get("grad")), ql),
                    cb.exists(techSubquery(cb, root, query, q))));
        }

        // ── Ima CV / GitHub
        if (Boolean.TRUE.equals(imaCv)) {
            ps.add(cb.and(
                    cb.isNotNull(root.get("cv")),
                    cb.notEqual(cb.trim(root.<String>get("cv")), "")));
        }
        if (Boolean.TRUE.equals(imaGithub)) {
            ps.add(cb.and(
                    cb.isNotNull(root.get("github")),
                    cb.notEqual(cb.trim(root.<String>get("github")), "")));
        }

        // ── Pozicija (OR logika — comma-separated)
        if (pozicija != null && !pozicija.isBlank()) {
            String[] pozList = pozicija.split(",");
            List<Predicate> pozPreds = new ArrayList<>();
            for (String poz : pozList) {
                String trimmed = poz.trim();
                if (!trimmed.isEmpty()) {
                    pozPreds.add(cb.like(cb.lower(root.get("pozicija")),
                            "%" + trimmed.toLowerCase() + "%"));
                }
            }
            if (!pozPreds.isEmpty())
                ps.add(cb.or(pozPreds.toArray(new Predicate[0])));
        }

        // ── Engleski (minimum nivo — A2 tražen → vraća A2, B1, B2, C1, C2)
        if (engleski != null && !engleski.isBlank()) {
            Integer trazeniRang = ENG_RANK.get(engleski.toLowerCase().trim());
            if (trazeniRang != null) {
                List<Predicate> engPreds = new ArrayList<>();
                for (Map.Entry<String, Integer> entry : ENG_RANK.entrySet()) {
                    if (entry.getValue() >= trazeniRang) {
                        engPreds.add(cb.equal(
                                cb.lower(root.get("engleski")),
                                entry.getKey()));
                    }
                }
                if (!engPreds.isEmpty())
                    ps.add(cb.or(engPreds.toArray(new Predicate[0])));
            }
        }

        // ── Dostupnost (kumulativna logika — "mesec" vraća odmah+15dana+mesec)
        if (dostupnost != null && !dostupnost.isBlank()) {
            Map<String, Integer> dosRank = Map.of(
                    "odmah", 1, "15dana", 2, "mesec", 3, "2meseca", 4
            );
            Integer trazeniRang = dosRank.get(dostupnost.toLowerCase().trim());
            if (trazeniRang != null) {
                List<Predicate> dosPreds = new ArrayList<>();
                for (Map.Entry<String, Integer> entry : dosRank.entrySet()) {
                    if (entry.getValue() <= trazeniRang) {
                        dosPreds.add(cb.equal(
                                cb.lower(root.get("dostupnost")),
                                entry.getKey()));
                    }
                }
                if (!dosPreds.isEmpty())
                    ps.add(cb.or(dosPreds.toArray(new Predicate[0])));
            }
        }

        // ── Angažovanje
        if (angazovanje != null && !angazovanje.isBlank()) {
            ps.add(cb.like(cb.lower(root.get("angazovanje")),
                    "%" + angazovanje.toLowerCase().trim() + "%"));
        }

        return ps.toArray(new Predicate[0]);
    }

    private Subquery<Integer> techSubquery(
            CriteriaBuilder cb, Root<Programer> parentRoot,
            CriteriaQuery<?> parentQuery, String searchTerm) {

        Subquery<Integer> sub = parentQuery.subquery(Integer.class);
        Root<Programer> subRoot = sub.correlate(parentRoot);
        Join<Programer, String> techJoin = subRoot.join("tehnologije", JoinType.INNER);
        sub.select(cb.literal(1));
        sub.where(cb.like(
                cb.lower(techJoin.as(String.class)),
                "%" + searchTerm.toLowerCase().trim() + "%"));
        return sub;
    }

    // ══════════════════════════════════════════════════════════════════
    //  pretraziSoft — vraća SVE aktivne profile za filtrirani prikaz.
    //  Ako postoji keyword q, filtrira samo po njemu (OR logika).
    //  Sve ostalo (nivo, grad, plata...) radi score u ProgramerController.
    //  Rezultat: korisnik vidi sve profile, bolji match = viši score.
    // ══════════════════════════════════════════════════════════════════
    @Override
    public Page<Programer> pretraziSoft(String q, Pageable pageable) {

        CriteriaBuilder cb = em.getCriteriaBuilder();

        // COUNT query
        CriteriaQuery<Long> countQ = cb.createQuery(Long.class);
        Root<Programer> cr = countQ.from(Programer.class);
        countQ.select(cb.countDistinct(cr));
        countQ.where(buildSoftPredicates(cb, cr, countQ, q));
        Long total = em.createQuery(countQ).getSingleResult();

        if (total == 0) return new PageImpl<>(List.of(), pageable, 0);

        // DATA query
        CriteriaQuery<Programer> dataQ = cb.createQuery(Programer.class);
        Root<Programer> root = dataQ.from(Programer.class);
        dataQ.select(root).distinct(true);
        dataQ.where(buildSoftPredicates(cb, root, dataQ, q));

        // Sortiranje
        List<Order> orders = new ArrayList<>();
        for (Sort.Order so : pageable.getSort()) {
            String field = SORT_FIELD_MAP.getOrDefault(so.getProperty(), "kreiranDatum");
            try {
                orders.add(so.isAscending()
                        ? cb.asc(root.get(field))
                        : cb.desc(root.get(field)));
            } catch (IllegalArgumentException e) {
                orders.add(cb.desc(root.get("kreiranDatum")));
            }
        }
        if (orders.isEmpty()) orders.add(cb.desc(root.get("kreiranDatum")));
        dataQ.orderBy(orders);

        List<Programer> rezultati = em.createQuery(dataQ)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        return new PageImpl<>(rezultati, pageable, total);
    }

    private Predicate[] buildSoftPredicates(
            CriteriaBuilder cb, Root<Programer> root,
            CriteriaQuery<?> query, String q) {

        List<Predicate> ps = new ArrayList<>();

        // Jedini obavezni filter — samo aktivni profili
        ps.add(cb.isTrue(root.get("aktivan")));

        // Keyword q je jedini hard filter — OR po svim relevantnim poljima
        if (q != null && !q.isBlank()) {
            String ql = "%" + q.toLowerCase().trim() + "%";
            ps.add(cb.or(
                    cb.like(cb.lower(root.get("ime")),  ql),
                    cb.like(cb.lower(root.get("opis")), ql),
                    cb.like(cb.lower(root.get("grad")), ql),
                    cb.exists(techSubquery(cb, root, query, q.trim()))
            ));
        }

        return ps.toArray(new Predicate[0]);
    }
}