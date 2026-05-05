package es.ucm.fdi.iw.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import es.ucm.fdi.iw.model.FavoriteSong;
import es.ucm.fdi.iw.model.MIDISequence;
import es.ucm.fdi.iw.model.User;

public interface FavoritesSongsRepository extends JpaRepository<FavoriteSong, Long> {
    List<FavoriteSong> findByUser(User user);
    boolean existsByUserAndMidiSequence(User user, MIDISequence midiSequence);
}
