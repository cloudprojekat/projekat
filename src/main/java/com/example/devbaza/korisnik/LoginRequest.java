package com.example.devbaza.korisnik;

/**
 * DTO klasa za primanje login podataka sa frontenda
 * Frontend šalje: { email: "...", lozinka: "..." }
 */
public class LoginRequest {

    private String email;
    private String lozinka;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getLozinka() { return lozinka; }
    public void setLozinka(String lozinka) { this.lozinka = lozinka; }
}