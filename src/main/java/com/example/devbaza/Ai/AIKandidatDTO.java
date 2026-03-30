package com.example.devbaza.Ai;

import java.util.List;

public class AIKandidatDTO {

    private Long id;
    private String ime;
    private String grad;
    private String nivo;
    private Integer iskustvo;
    private Integer plata;
    private String nacinRada;
    private List<String> tehnologije;
    private Long korisnikId;
    private String opis;
    private String github;
    private int procenat;
    private String obrazlozenje;

    // ── Nova polja ──
    private String pozicija;
    private String engleski;
    private String dostupnost;
    private String angazovanje;

    // ── Getteri i Setteri ──
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getIme() { return ime; }
    public void setIme(String ime) { this.ime = ime; }

    public String getGrad() { return grad; }
    public void setGrad(String grad) { this.grad = grad; }

    public String getNivo() { return nivo; }
    public void setNivo(String nivo) { this.nivo = nivo; }

    public Integer getIskustvo() { return iskustvo; }
    public void setIskustvo(Integer iskustvo) { this.iskustvo = iskustvo; }

    public Integer getPlata() { return plata; }
    public void setPlata(Integer plata) { this.plata = plata; }

    public String getNacinRada() { return nacinRada; }
    public void setNacinRada(String nacinRada) { this.nacinRada = nacinRada; }

    public List<String> getTehnologije() { return tehnologije; }
    public void setTehnologije(List<String> tehnologije) { this.tehnologije = tehnologije; }

    public Long getKorisnikId() { return korisnikId; }
    public void setKorisnikId(Long korisnikId) { this.korisnikId = korisnikId; }

    public String getOpis() { return opis; }
    public void setOpis(String opis) { this.opis = opis; }

    public String getGithub() { return github; }
    public void setGithub(String github) { this.github = github; }

    public int getProcenat() { return procenat; }
    public void setProcenat(int procenat) { this.procenat = procenat; }

    public String getObrazlozenje() { return obrazlozenje; }
    public void setObrazlozenje(String obrazlozenje) { this.obrazlozenje = obrazlozenje; }

    public String getPozicija() { return pozicija; }
    public void setPozicija(String pozicija) { this.pozicija = pozicija; }

    public String getEngleski() { return engleski; }
    public void setEngleski(String engleski) { this.engleski = engleski; }

    public String getDostupnost() { return dostupnost; }
    public void setDostupnost(String dostupnost) { this.dostupnost = dostupnost; }

    public String getAngazovanje() { return angazovanje; }
    public void setAngazovanje(String angazovanje) { this.angazovanje = angazovanje; }
}