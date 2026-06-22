package com.example.devbaza.programer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProgramerRepository extends JpaRepository<Programer, Long>,
        ProgramerRepositoryCustom {

    Page<Programer> findByAktivanTrue(Pageable pageable);

    Optional<Programer> findByKorisnikIdAndAktivanTrue(Long korisnikId);

    @Modifying
    @Transactional
    @Query("UPDATE Programer p SET p.brojPregleda = p.brojPregleda + 1 WHERE p.id = :id")
    void incrementPregleda(@Param("id") Long id);

    @Query("SELECT COUNT(s) FROM SacuvaniKandidat s WHERE s.programerId = :id")
    Long countSacuvanja(@Param("id") Long id);

    Long countByAktivanTrue();

    @Query(value = """
        SELECT t.tehnologija, COUNT(*) as broj
        FROM programer_tehnologije t
        JOIN programeri p ON p.id = t.programer_id
        WHERE p.aktivan = true
          AND t.tehnologija IS NOT NULL
          AND TRIM(t.tehnologija) != ''
        GROUP BY t.tehnologija
        ORDER BY broj DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> topTehnologijeNative(@Param("limit") int limit);

    // ── SLIČNI PROFILI ──
    // Jedan native SQL upit — bez učitavanja u memoriju.
    // Vraća profile koji dele >= minPoklapanja tehnologija sa datim programerom.
    // HAVING COUNT(*) >= :minPoklapanja filtrira na nivou baze — Java ne dira ostatak.
    @Query(value = """
        SELECT p.id, p.ime, p.nivo, p.grad, p.pozicija,
               p.nacin_rada, p.iskustvo, p.broj_pregleda,
               COUNT(*) AS zajednicke,
               CAST(COUNT(*) AS float) / :ukupno * 100 AS procenat
        FROM programeri p
        JOIN programer_tehnologije pt ON pt.programer_id = p.id
        WHERE pt.tehnologija IN (:tehnologije)
          AND p.aktivan = true
          AND p.id != :id
        GROUP BY p.id, p.ime, p.nivo, p.grad, p.pozicija,
                 p.nacin_rada, p.iskustvo, p.broj_pregleda
        HAVING COUNT(*) >= :minPoklapanja
        ORDER BY zajednicke DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> nadjiSlicne(
            @Param("id")            Long id,
            @Param("tehnologije")   List<String> tehnologije,
            @Param("ukupno")        int ukupno,
            @Param("minPoklapanja") int minPoklapanja,
            @Param("limit")         int limit
    );
    @Query("SELECT COUNT(p) FROM Programer p WHERE p.aktivan = true AND p.kreiranDatum >= :od")
    Long countNovihOd(@Param("od") LocalDateTime od);

    @Query("SELECT SUM(p.brojPregleda) FROM Programer p WHERE p.aktivan = true")
    Long ukupnoPregleda();
}