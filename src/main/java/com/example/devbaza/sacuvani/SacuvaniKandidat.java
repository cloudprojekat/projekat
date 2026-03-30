package com.example.devbaza.sacuvani;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sacuvani_kandidati", indexes = {
        @Index(name = "idx_sacuvani_firma", columnList = "firma_id"),
        @Index(name = "idx_sacuvani_programer", columnList = "programer_id"),
        @Index(name = "idx_sacuvani_unique", columnList = "firma_id, programer_id", unique = true)
})
public class SacuvaniKandidat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "firma_id", nullable = false)
    private Long firmaId;

    @Column(name = "programer_id", nullable = false)
    private Long programerId;

    @Column(name = "datum_sacuvan")
    private LocalDateTime datumSacuvan;

    public SacuvaniKandidat() { this.datumSacuvan = LocalDateTime.now(); }

    public SacuvaniKandidat(Long firmaId, Long programerId) {
        this.firmaId = firmaId;
        this.programerId = programerId;
        this.datumSacuvan = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getFirmaId() { return firmaId; }
    public void setFirmaId(Long firmaId) { this.firmaId = firmaId; }
    public Long getProgramerId() { return programerId; }
    public void setProgramerId(Long programerId) { this.programerId = programerId; }
    public LocalDateTime getDatumSacuvan() { return datumSacuvan; }
}
