package com.example.devbaza.korisnik;

import java.time.LocalDateTime;

/**
 * DTO za bezbedno slanje podataka o korisniku.
 * NIKAD ne slati Korisnik entitet direktno — sadrži BCrypt hash lozinke!
 */
public class KorisnikDTO {

    private Long id;
    private String ime;
    private String email;
    private String tip;
    private Boolean aktivan;
    private LocalDateTime kreiranDatum;

    // ── Konstruktor iz entiteta ──
    public KorisnikDTO(Korisnik k) {
        this.id           = k.getId();
        this.ime          = k.getIme();
        this.email        = k.getEmail();
        this.tip          = k.getTip();
        this.aktivan      = k.getAktivan();
        this.kreiranDatum = k.getKreiranDatum();
        // lozinka se NIKAD ne kopira
    }

    public Long getId()                    { return id; }
    public String getIme()                 { return ime; }
    public String getEmail()               { return email; }
    public String getTip()                 { return tip; }
    public Boolean getAktivan()            { return aktivan; }
    public LocalDateTime getKreiranDatum() { return kreiranDatum; }
}