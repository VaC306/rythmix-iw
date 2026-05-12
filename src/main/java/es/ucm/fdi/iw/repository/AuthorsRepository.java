package es.ucm.fdi.iw.repository;

import es.ucm.fdi.iw.model.Authors;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthorsRepository extends JpaRepository<Authors, Long> {
}