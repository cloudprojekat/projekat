package com.example.devbaza.programer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProgramerRepositoryCustom {

    // Stari metod — striktni AND filteri, zadrzavamo ga
    Page<Programer> filtriraj(
            String nivo, String grad,
            Integer minIskustvo, Integer maxIskustvo,
            Integer minPlata, Integer maxPlata,
            String nacinRada, String tech, String q,
            Boolean imaCv, Boolean imaGithub,
            String pozicija, String engleski,
            String dostupnost, String angazovanje,
            Pageable pageable
    );

    // Novi metod — vraća SVE aktivne profile.
    // Jedini hard filter je keyword q — ako postoji, mora pogoditi ime/opis/grad/tehnologiju.
    // Sortiranje i filtriranje po relevantnosti rade score u kontroleru.
    Page<Programer> pretraziSoft(String q, Pageable pageable);
}