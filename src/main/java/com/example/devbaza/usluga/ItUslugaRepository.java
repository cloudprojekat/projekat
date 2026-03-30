package com.example.devbaza.usluga;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ItUslugaRepository extends JpaRepository<ItUsluga, Long> {

    // ── Stare metode (zadržane za kompatibilnost) ──
    List<ItUsluga> findByAktivanTrue();
    List<ItUsluga> findByKategorijaAndAktivanTrue(String kategorija);
    List<ItUsluga> findByProgramerIdAndAktivanTrue(Long programerId);

    // ── Paginirana pretraga sa svim filterima ──
    @Query("""
        SELECT u FROM ItUsluga u
        WHERE u.aktivan = true
          AND (:kategorija IS NULL OR :kategorija = 'sve' OR u.kategorija = :kategorija)
          AND (:q IS NULL OR LOWER(u.naziv) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
                          OR LOWER(u.opis)  LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
          AND (:maxCena IS NULL OR u.cenaOd IS NULL OR u.cenaOd <= :maxCena)
          AND (:grad IS NULL OR :grad = 'svi' OR u.grad = :grad)
          AND (:nacinRada IS NULL OR u.nacinRada = :nacinRada)
        """)
    Page<ItUsluga> pretrazi(
            @Param("kategorija") String kategorija,
            @Param("q")          String q,
            @Param("maxCena")    Integer maxCena,
            @Param("grad")       String grad,
            @Param("nacinRada")  String nacinRada,
            Pageable pageable
    );

    // ── Ukupan broj aktivnih (za stats) ──
    long countByAktivanTrue();

    // ── Broj unique programera koji imaju uslugu ──
    @Query("SELECT COUNT(DISTINCT u.programerId) FROM ItUsluga u WHERE u.aktivan = true")
    long countDistinctProgrameri();

    // ── Inkrementiranje pregleda ──
    @Modifying
    @Transactional
    @Query("UPDATE ItUsluga u SET u.pregleda = u.pregleda + 1 WHERE u.id = :id")
    void incrementPregleda(@Param("id") Long id);
}