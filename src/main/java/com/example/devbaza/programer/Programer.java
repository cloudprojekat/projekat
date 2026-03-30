package com.example.devbaza.programer;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "programeri", indexes = {
        @Index(name = "idx_programer_aktivan", columnList = "aktivan"),
        @Index(name = "idx_programer_grad", columnList = "grad"),
        @Index(name = "idx_programer_nivo", columnList = "nivo"),
        @Index(name = "idx_programer_korisnik", columnList = "korisnik_id"),
        @Index(name = "idx_programer_kreirano", columnList = "kreiran_datum")
})
public class Programer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "korisnik_id")
    private Long korisnikId;

    @NotBlank(message = "Ime je obavezno")
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String ime;

    @Size(max = 80)
    private String grad;

    @Min(0) @Max(50)
    @Column(name = "godine_iskustva", columnDefinition = "integer default 0")
    private Integer iskustvo = 0;

    @Size(max = 200)
    private String edukacija;

    private Integer plata;

    @Size(max = 20)
    private String nivo;

    @Size(max = 255)
    private String github;

    @Size(max = 255)
    private String cv;

    @Size(max = 50)
    @Column(name = "nacin_rada", columnDefinition = "varchar(50) default 'Remote'")
    private String nacinRada = "Remote";

    @Size(max = 100)
    @Column(length = 100)
    private String pozicija;

    @Size(max = 10)
    @Column(length = 10)
    private String engleski;

    @Size(max = 30)
    @Column(length = 30)
    private String dostupnost;

    @Size(max = 30)
    @Column(length = 30)
    private String angazovanje;

    @Column(columnDefinition = "TEXT")
    private String opis;

    // Podaci su u koloni "tehnologija" u tabeli programer_tehnologije
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "programer_tehnologije",
            joinColumns = @JoinColumn(name = "programer_id"))
    @Column(name = "tehnologija", length = 255)
    private List<String> tehnologije;

    @Column(name = "kreiran_datum")
    private LocalDateTime kreiranDatum;

    @Column(nullable = false)
    private Boolean aktivan = true;

    @Column(name = "broj_pregleda")
    private Integer brojPregleda = 0;

    @PrePersist
    protected void onCreate() {
        kreiranDatum = LocalDateTime.now();
    }

    // ── Getteri i Setteri ──
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getKorisnikId() { return korisnikId; }
    public void setKorisnikId(Long korisnikId) { this.korisnikId = korisnikId; }

    public String getIme() { return ime; }
    public void setIme(String ime) { this.ime = ime; }

    public String getGrad() { return grad; }
    public void setGrad(String grad) { this.grad = grad; }

    public Integer getIskustvo() { return iskustvo; }
    public void setIskustvo(Integer iskustvo) { this.iskustvo = iskustvo; }

    public String getEdukacija() { return edukacija; }
    public void setEdukacija(String edukacija) { this.edukacija = edukacija; }

    public Integer getPlata() { return plata; }
    public void setPlata(Integer plata) { this.plata = plata; }

    public String getNivo() { return nivo; }
    public void setNivo(String nivo) { this.nivo = nivo; }

    public String getGithub() { return github; }
    public void setGithub(String github) { this.github = github; }

    public String getCv() { return cv; }
    public void setCv(String cv) { this.cv = cv; }

    public String getNacinRada() { return nacinRada; }
    public void setNacinRada(String nacinRada) { this.nacinRada = nacinRada; }

    public String getOpis() { return opis; }
    public void setOpis(String opis) { this.opis = opis; }

    public List<String> getTehnologije() { return tehnologije; }
    public void setTehnologije(List<String> tehnologije) { this.tehnologije = tehnologije; }

    public LocalDateTime getKreiranDatum() { return kreiranDatum; }

    public Boolean getAktivan() { return aktivan; }
    public void setAktivan(Boolean aktivan) { this.aktivan = aktivan; }

    public Integer getBrojPregleda() { return brojPregleda; }
    public void setBrojPregleda(Integer brojPregleda) { this.brojPregleda = brojPregleda; }

    public String getPozicija() { return pozicija; }
    public void setPozicija(String pozicija) { this.pozicija = pozicija; }

    public String getEngleski() { return engleski; }
    public void setEngleski(String engleski) { this.engleski = engleski; }

    public String getDostupnost() { return dostupnost; }
    public void setDostupnost(String dostupnost) { this.dostupnost = dostupnost; }

    public String getAngazovanje() { return angazovanje; }
    public void setAngazovanje(String angazovanje) { this.angazovanje = angazovanje; }
}