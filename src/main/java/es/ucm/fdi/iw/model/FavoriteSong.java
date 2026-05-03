package es.ucm.fdi.iw.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
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
    
    public MIDISequence.Transfer toMidiSequenceTransfer() {
      return midiSequence.toTransfer();
    }

}
