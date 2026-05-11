package es.ucm.fdi.iw.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import es.ucm.fdi.iw.model.SongReport;

public interface SongReportRepository extends JpaRepository<SongReport, Long> {
    List<SongReport> findByOrderByDateRegisteredDesc();
}