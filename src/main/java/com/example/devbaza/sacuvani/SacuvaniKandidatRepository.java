package com.example.devbaza.sacuvani;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SacuvaniKandidatRepository extends JpaRepository<SacuvaniKandidat, Long> {
    List<SacuvaniKandidat> findByFirmaId(Long firmaId);
    List<SacuvaniKandidat> findByProgramerId(Long programerId);
    Optional<SacuvaniKandidat> findByFirmaIdAndProgramerId(Long firmaId, Long programerId);

    @Query("SELECT COUNT(s) FROM SacuvaniKandidat s WHERE s.programerId = :programerId")
    Long countByProgramerId(Long programerId);

    void deleteByFirmaIdAndProgramerId(Long firmaId, Long programerId);
}
