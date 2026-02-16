package es.ucm.fdi.iw.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.SequenceGenerator;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@Table(
  uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "daily_game_id"})
)
public class Attempt {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gen")
  @SequenceGenerator(name = "gen", sequenceName = "gen")
  private long id;

  @ManyToOne(optional = false)
  private User user;

  @ManyToOne(optional = false)
  @JoinColumn(name = "daily_game_id")
  private DailyGame dailyGame;

  private int currentLayer;   //en que capa de cancion va o algo asi
  private int tries;
  private boolean success;

  private String guess;       // sera util cuando metamos en el daily guess el buscador en vez del selector
  private LocalDateTime createdAt = LocalDateTime.now();
}
