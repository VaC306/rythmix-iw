package es.ucm.fdi.iw.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import es.ucm.fdi.iw.model.AuditWeb;

public interface AuditWebRepository extends JpaRepository<AuditWeb, Long> {
    
    @Query(
        "SELECT a FROM AuditWeb a WHERE "+
        "(:userId is NULL OR a.user.id = :userId) AND " + 
        "(:actionPerformed is NULL OR a.actionPerformed = :actionPerformed) " +
        "ORDER BY a.time DESC"
    )
    List<AuditWeb> findByFiltersDesc(@Param("userId") Long userId, @Param("actionPerformed") String actionPerformed);

}
