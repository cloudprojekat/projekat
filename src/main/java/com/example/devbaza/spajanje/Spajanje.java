package com.example.devbaza.spajanje;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "spajanja", indexes = {
        @Index(name = "idx_spajanje_korisnik", columnList = "korisnik_id"),
        @Index(name = "idx_spajanje_datum",    columnList = "datum"),
        @Index(name = "idx_spajanje_tip",      columnList = "tip")
})
public class Spajanje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "korisnik_id")
    private Long korisnikId;

    // "developer" — developer je dobio poziv
    // "firma"     — firma je pronašla developera
    @Column(name = "tip", length = 20)
    private String tip;

    // Developer: pozicija za koju je dobio poziv (npr. "React Developer")
    // Firma:     pozicija developera kojeg je zaposlila (npr. "Senior Java Backend")
    @Column(name = "pozicija", length = 100)
    private String pozicija;

    // Koliko dana je trebalo (npr. "3", "7", "14")
    @Column(name = "vreme_dana")
    private Integer vremenaDana;

    @Column(name = "datum")
    private LocalDateTime datum;

    @PrePersist
    protected void onCreate() { datum = LocalDateTime.now(); }

    public Long getId()                  { return id; }
    public Long getKorisnikId()          { return korisnikId; }
    public void setKorisnikId(Long k)    { this.korisnikId = k; }
    public String getTip()               { return tip; }
    public void setTip(String t)         { this.tip = t; }
    public String getPozicija()          { return pozicija; }
    public void setPozicija(String p)    { this.pozicija = p; }
    public Integer getVremenaDana()      { return vremenaDana; }
    public void setVremenaDana(Integer v){ this.vremenaDana = v; }
    public LocalDateTime getDatum()      { return datum; }
}