package com.example.devbaza.korisnik;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface KorisnikRepository extends JpaRepository<Korisnik, Long> {

    Optional<Korisnik> findByEmail(String email);

    boolean existsByEmail(String email);

    // OVO JE KLJUČNO: Spring Data JPA će sam napraviti SQL upit:
    // "SELECT * FROM korisnici WHERE tip = ?"
    List<Korisnik> findByTip(String tip);
    long countByTip(String tip);
    @Query("SELECT COUNT(k) FROM Korisnik k WHERE k.kreiranDatum >= :od")
    Long countRegistrovanihOd(@Param("od") LocalDateTime od);
}