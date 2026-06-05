package es.ucm.fdi.iw.model;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@Table(
  uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "midiSequence_id"})
)
public class FavoriteSong {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gen")
    @SequenceGenerator(name = "gen", sequenceName = "gen")
    private long id;

    @ManyToOne(optional=false)
    private User user;

    @ManyToOne(optional=false)
    private MIDISequence midiSequence;
    
    /* 
    public MIDISequence.Transfer toMidiSequenceTransfer() {
      return midiSequence.toTransfer();
    }*/


    @Getter
    @AllArgsConstructor      
    public static class Transfer {
      private long sequenceId;
      private String gameType;
      private LocalDateTime gameDate;
      private List<MIDITrack.Transfer> tracks;
      private List<String> players;
    }
    
    public Transfer toTransfer() {
      MIDIGame game = midiSequence.getGame();
      String gameType = (game instanceof GarticGame) ? "Canción Sorpresa" : "Continuación de Canción";
      List<String> players = game.getPlayers().stream().map(User::getUsername).toList();
      List<MIDITrack.Transfer> tracks = midiSequence.getTracks().stream().map(MIDITrack::toTransfer).toList();
      return new Transfer(midiSequence.getId(), gameType, game.getDateEnded(), tracks, players);
    }

}
