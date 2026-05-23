package es.ucm.fdi.iw.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class ContinueGame extends MIDIGame {

    public enum ContinueGameStatus {
        WAITING,
        PLAYING,
        VOTING,
        FINISHED,
    }

    @ElementCollection
    @CollectionTable(
        name = "continue_seq_assignment",
        joinColumns = @JoinColumn(name = "game_id")
    )
    @MapKeyColumn(name = "player_id")
    @Column(name = "seq_id")
    private Map<Long, Long> sequenceAssignments = new HashMap<>();

    @ElementCollection
    @CollectionTable(
        name = "vote_counts",
        joinColumns = @JoinColumn(name = "game_id")
    )
    @MapKeyColumn(name = "seq_id")
    @Column(name = "vote_count")
    private Map<Long, Integer> voteCounts = new HashMap<>();

    @ElementCollection
    @CollectionTable(
        name = "votes_submitted",
        joinColumns = @JoinColumn(name = "game_id")
    )
    @MapKeyColumn(name = "voter_id")
    @Column(name = "voted_id")
    private Map<Long, Long> _voterToVoted = new HashMap<>();

    @ElementCollection
    @CollectionTable(
        name = "continue_track_submissions",
        joinColumns = @JoinColumn(name = "game_id")
    )
    @MapKeyColumn(name = "player_id")
    @Column(name = "submitted")
    private Map<Long, Boolean> trackSubmissions = new LinkedHashMap<>();

    private List<Integer> roundInstruments;

    private int currentRound;

    private int totalRounds;

    private int roundTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContinueGameStatus status;

    private long finalSequenceId;

    public void addPlayer(User user) {
        if (!players.contains(user)) {
            this.players.add(user);
        }
    }
}
