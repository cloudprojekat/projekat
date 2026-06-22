package com.example.devbaza.korisnik;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

@Entity
@Table(name = "korisnici", indexes = {
        @Index(name = "idx_korisnik_email", columnList = "email", unique = true),
        @Index(name = "idx_korisnik_tip", columnList = "tip")
})
public class Korisnik {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Ime je obavezno")
    @Size(min = 2, max = 100)
    @Column(nullable = false, length = 100)
    private String ime;

    @NotBlank(message = "Email je obavezan")
    @Email(message = "Email nije ispravan")
    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @NotBlank
    @Column(nullable = false)
    private String lozinka; // BCrypt hash

    // "programer", "firma", "admin"
    @Column(nullable = false, length = 20)
    private String tip;

    @Column(name = "kreiran_datum")
    private LocalDateTime kreiranDatum;

    @Column(name = "aktivan")
    private Boolean aktivan = true;

    @PrePersist
    protected void onCreate() {
        kreiranDatum = LocalDateTime.now();
    }

    // Getteri i setteri
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getIme() { return ime; }
    public void setIme(String ime) { this.ime = ime; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getLozinka() { return lozinka; }
    public void setLozinka(String lozinka) { this.lozinka = lozinka; }

    public String getTip() { return tip; }
    public void setTip(String tip) { this.tip = tip; }

    public LocalDateTime getKreiranDatum() { return kreiranDatum; }

    public Boolean getAktivan() { return aktivan; }
    public void setAktivan(Boolean aktivan) { this.aktivan = aktivan; }
}
