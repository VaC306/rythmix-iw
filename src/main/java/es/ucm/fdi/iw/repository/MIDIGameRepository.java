package es.ucm.fdi.iw.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import es.ucm.fdi.iw.model.MIDIGame;

public interface MIDIGameRepository extends JpaRepository<MIDIGame, Long>  {
    Optional<MIDIGame> findByLobbyCode(String lobbyCode);

    boolean existsByLobbyCode (String lobbyCode);

    List<MIDIGame> findByFinishedTrueOrderByDateEndedDesc(Pageable pageable);

    @Query("SELECT g FROM MIDIGame g JOIN g.players p WHERE p.id = :userId AND g.finished = true ORDER BY g.dateEnded DESC")
    List<MIDIGame> findFinishedGamesByPlayer(@Param("userId") long userId);
}
