package es.ucm.fdi.iw.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

//usaremos esto para un posible ranking

@Entity
@Data
@NoArgsConstructor
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"user_id"}))
public class Score {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gen")
  @SequenceGenerator(name = "gen", sequenceName = "gen")
  private long id;

  @OneToOne(optional = false)
  private User user;

  private int totalPoints = 0;
  private int gamesPlayed = 0;
  private int gamesWon = 0;

  private int currentStreak = 0;
  private int bestStreak = 0;
}
