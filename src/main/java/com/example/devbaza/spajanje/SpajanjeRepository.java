package com.example.devbaza.spajanje;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SpajanjeRepository extends JpaRepository<Spajanje, Long> {

    long count();

    @Query("SELECT COUNT(s) > 0 FROM Spajanje s WHERE s.korisnikId = :id AND s.datum > :od")
    boolean postojiUPeriodu(@Param("id") Long id, @Param("od") LocalDateTime od);

    @Query("SELECT COUNT(s) FROM Spajanje s WHERE s.datum > :od")
    long countOd(@Param("od") LocalDateTime od);

    // Poslednjih N spajanja koja imaju poziciju
    @Query("SELECT s FROM Spajanje s WHERE s.pozicija IS NOT NULL ORDER BY s.datum DESC")
    List<Spajanje> nadjiPoslednja(org.springframework.data.domain.Pageable pageable);

    // Prosečan broj dana za developer spajanja
    @Query("SELECT AVG(s.vremenaDana) FROM Spajanje s WHERE s.tip = 'programer' AND s.vremenaDana IS NOT NULL")
    Double prosekDanaDeveloper();

    // Prosečan broj dana za firma spajanja
    @Query("SELECT AVG(s.vremenaDana) FROM Spajanje s WHERE s.tip = 'firma' AND s.vremenaDana IS NOT NULL")
    Double prosekDanaFirma();
}