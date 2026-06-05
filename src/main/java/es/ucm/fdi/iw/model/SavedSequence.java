package es.ucm.fdi.iw.model;

import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
public class SavedSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gen")
    @SequenceGenerator(name = "gen", sequenceName = "gen")
    private long id;

    @ManyToOne(optional = false)
    private User user;

    private String name;

    private LocalDateTime createdAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "varchar")
    private List<TrackData> tracks;

    @Getter
    @AllArgsConstructor
    public static class Transfer {
        private long id;
        private String name;
        private LocalDateTime createdAt;
        private List<TrackData> tracks;
    }

    public Transfer toTransfer() {
        return new Transfer(id, name, createdAt, tracks);
    }
}
