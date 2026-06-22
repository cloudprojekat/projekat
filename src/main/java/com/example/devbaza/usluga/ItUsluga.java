package com.example.devbaza.usluga;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "it_usluge", indexes = {
        @Index(name = "idx_usluga_aktivan", columnList = "aktivan"),
        @Index(name = "idx_usluga_kategorija", columnList = "kategorija"),
        @Index(name = "idx_usluga_programer", columnList = "programer_id")
})
public class ItUsluga {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "programer_id")
    private Long programerId;

    private String programerIme;

    @NotBlank @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String naziv;

    @NotBlank
    @Column(nullable = false)
    private String kategorija;

    @NotBlank @Size(min = 30)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String opis;

    private Integer cenaOd;
    private String rokIsporuke;
    private String nacinRada;
    private String grad;

    @Email
    private String kontaktEmail;
    private String portfolioLink;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "usluga_tehnologije", joinColumns = @JoinColumn(name = "usluga_id"))
    @Column(name = "tehnologija")
    private List<String> tehnologije;

    private LocalDateTime kreirano;
    private Boolean aktivan = true;
    private Integer pregleda = 0;

    @PrePersist protected void onCreate() { kreirano = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getProgramerId() { return programerId; }
    public void setProgramerId(Long p) { this.programerId = p; }
    public String getProgramerIme() { return programerIme; }
    public void setProgramerIme(String p) { this.programerIme = p; }
    public String getNaziv() { return naziv; }
    public void setNaziv(String n) { this.naziv = n; }
    public String getKategorija() { return kategorija; }
    public void setKategorija(String k) { this.kategorija = k; }
    public String getOpis() { return opis; }
    public void setOpis(String o) { this.opis = o; }
    public Integer getCenaOd() { return cenaOd; }
    public void setCenaOd(Integer c) { this.cenaOd = c; }
    public String getRokIsporuke() { return rokIsporuke; }
    public void setRokIsporuke(String r) { this.rokIsporuke = r; }
    public String getNacinRada() { return nacinRada; }
    public void setNacinRada(String n) { this.nacinRada = n; }
    public String getGrad() { return grad; }
    public void setGrad(String g) { this.grad = g; }
    public String getKontaktEmail() { return kontaktEmail; }
    public void setKontaktEmail(String e) { this.kontaktEmail = e; }
    public String getPortfolioLink() { return portfolioLink; }
    public void setPortfolioLink(String p) { this.portfolioLink = p; }
    public List<String> getTehnologije() { return tehnologije; }
    public void setTehnologije(List<String> t) { this.tehnologije = t; }
    public LocalDateTime getKreirano() { return kreirano; }
    public Boolean getAktivan() { return aktivan; }
    public void setAktivan(Boolean a) { this.aktivan = a; }
    public Integer getPregleda() { return pregleda; }
    public void setPregleda(Integer p) { this.pregleda = p; }
}
