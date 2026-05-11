package es.ucm.fdi.iw.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import es.ucm.fdi.iw.model.SongReport;

public interface SongReportRepository extends JpaRepository<SongReport, Long> {}