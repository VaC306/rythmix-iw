package es.ucm.fdi.iw.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import es.ucm.fdi.iw.model.SavedSequence;
import es.ucm.fdi.iw.model.User;

public interface SavedSequenceRepository extends JpaRepository<SavedSequence, Long> {
    List<SavedSequence> findByUserOrderByCreatedAtDesc(User user);
}
