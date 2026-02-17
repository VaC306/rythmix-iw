package es.ucm.fdi.iw.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Entity
@Data
@NoArgsConstructor
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"day"}))
public class DailyGame {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gen")
  @SequenceGenerator(name = "gen", sequenceName = "gen")
  private long id;

  @Column(nullable = false)
  private LocalDate day;

  @ManyToOne(optional = false)
  private Song song;

  private int maxLayers = 4;
  private int maxTries = 2;

  private boolean active = true; // para deshabilitarlo en caso de acabar los intentos
}
