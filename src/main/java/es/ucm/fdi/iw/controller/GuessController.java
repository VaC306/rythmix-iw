package es.ucm.fdi.iw.controller;

import es.ucm.fdi.iw.LocalData;
import es.ucm.fdi.iw.auxiliar.AuditHelper;
import es.ucm.fdi.iw.model.*;
import es.ucm.fdi.iw.repository.*;
import es.ucm.fdi.iw.service.AudioAvailabilityService;

import jakarta.servlet.http.HttpSession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Controller
@RequestMapping("/guess")
public class GuessController {

  @Autowired
  private AuditHelper auditHelper;

  private static final Logger log = LogManager.getLogger(GuessController.class);

  private final SongRepository songRepo;
  private final SongLayerRepository layerRepo;
  private final DailyGameRepository dailyRepo;
  private final AttemptRepository attemptRepo;
  private final ScoreRepository scoreRepo;
  private final SongReportRepository songReportRepository;
  private final AudioAvailabilityService audioAvailabilityService;

  public GuessController(
      SongRepository songRepo,
      SongLayerRepository layerRepo,
      DailyGameRepository dailyRepo,
      AttemptRepository attemptRepo,
      ScoreRepository scoreRepo, 
      SongReportRepository songReportRepository,
      AudioAvailabilityService audioAvailabilityService) {
    this.songRepo = songRepo;
    this.layerRepo = layerRepo;
    this.dailyRepo = dailyRepo;
    this.attemptRepo = attemptRepo;
    this.scoreRepo = scoreRepo;
    this.songReportRepository = songReportRepository;
    this.audioAvailabilityService = audioAvailabilityService;
  }

  @ModelAttribute
  public void populateModel(HttpSession session, Model model) {
    User u = (User) session.getAttribute("u");
    model.addAttribute("u", u);
    model.addAttribute("logged", u != null);
  }

  // ---------------- GET /guess ----------------
  @GetMapping
  @Transactional(readOnly = true)
  public String page(HttpSession session, Model model) {

    User u = (User) session.getAttribute("u");
    log.debug("Acceso a GET /guess por usuario={}", u != null ? u.getId() : "anon");

    DailyGame dg = getOrCreateDaily(LocalDate.now());
    Song song = dg != null ? dg.getSong() : null;
    List<SongLayer> layers = visibleLayersForDaily(dg, song);
    boolean dailyAvailable = dg != null && audioAvailabilityService.hasAllAvailableAudio(layers);

    if (!dailyAvailable) {
      if (song != null) {
        log.warn("Daily {} no disponible para songId={} por capas incompletas o audios ausentes",
            dg.getId(), song.getId());
      } else {
        log.warn("No hay canciones válidas con todas las capas de audio disponibles para crear daily");
      }

      model.addAttribute("dailyAvailable", false);
      model.addAttribute("noAudioAvailable", true);
      model.addAttribute("finished", true);
      model.addAttribute("success", false);
      model.addAttribute("songList", audioAvailabilityService.availableSongs());

      Object msg = session.getAttribute("guessMsg");
      if (msg != null) {
        model.addAttribute("msg", msg.toString());
        session.removeAttribute("guessMsg");
      } else {
        model.addAttribute("msg", "guess.noAudioAdmin");
      }

      if (u == null) {
        model.addAttribute("loginWarning", true);
      }

      return "guess";
    }

    Attempt at = null;
    if (u != null) {
      at = attemptRepo.findByUserAndDailyGame(u, dg).orElse(null);
      log.debug("Attempt recuperado para user={} daily={}: {}",
          u.getId(), dg.getId(), at != null ? "sí" : "no");
    }

    int layerIndex = (at == null) ? 0 : at.getCurrentLayer();
    int tries = (at == null) ? 0 : at.getTries();
    boolean success = (at != null && at.isSuccess());
    boolean finished = success || tries >= dg.getMaxTries();

    layerIndex = clamp(layerIndex, 0, layers.size() - 1);

    model.addAttribute("dailyGame", dg);
    model.addAttribute("song", song);
    model.addAttribute("layerIndex", layerIndex);
    model.addAttribute("currentLayer", layers.get(layerIndex));
    model.addAttribute("maxLayer", layers.size() - 1);
    model.addAttribute("tries", tries);
    model.addAttribute("maxTries", dg.getMaxTries());
    model.addAttribute("dailyAvailable", true);
    model.addAttribute("finished", finished);
    model.addAttribute("success", success);
    model.addAttribute("noAudioAvailable", false);

    Object msg = session.getAttribute("guessMsg");
    if (msg != null) {
      log.debug("Mensaje de sesión en /guess: {}", msg);
      model.addAttribute("msg", msg.toString());
      session.removeAttribute("guessMsg");
    }

    if (u == null) {
      log.debug("Usuario anónimo accediendo a /guess");
      model.addAttribute("loginWarning", true);
    }

    model.addAttribute("songList", audioAvailabilityService.availableSongs());
    return "guess";
  }

  // ---------------- POST /guess/nav ----------------
  @PostMapping("/nav")
  @Transactional
  public String nav(@RequestParam String dir, HttpSession session) {

    User u = (User) session.getAttribute("u");
    if (u == null) {
      log.info("Intento de navegación de capas sin login");
      session.setAttribute("guessMsg", "Inicia sesión para jugar.");
      return "redirect:/login";
    }

    DailyGame dg = getOrCreateDaily(LocalDate.now());
    if (dg == null) {
      session.setAttribute("guessMsg", "guess.noAudioAdmin");
      return "redirect:/guess";
    }
    List<SongLayer> layers = visibleLayersForDaily(dg, dg.getSong());
    if (!audioAvailabilityService.hasAllAvailableAudio(layers)) {
      log.info("Usuario {} intentó navegar capas sin audios disponibles en daily={}", u.getId(), dg.getId());
      session.setAttribute("guessMsg", "guess.noAudioAdmin");
      return "redirect:/guess";
    }
    int max = Math.max(0, layers.size() - 1);

    Attempt at = attemptRepo.findByUserAndDailyGame(u, dg)
        .orElseGet(() -> {
          log.info("No existía attempt para user={} daily={}. Se crea uno nuevo desde /nav", u.getId(), dg.getId());
          return createAttempt(u, dg);
        });

    if (at.isSuccess() || at.getTries() >= dg.getMaxTries()) {
      log.info("Usuario {} intentó navegar en un daily ya terminado (daily={})", u.getId(), dg.getId());
      session.setAttribute("guessMsg", "Ya terminaste el daily de hoy.");
      return "redirect:/guess";
    }

    int previousLayer = at.getCurrentLayer();
    int layerIndex = previousLayer;

    if ("prev".equals(dir)) {
      layerIndex--;
    } else if ("next".equals(dir)) {
      layerIndex++;
    } else {
      log.warn("Dirección de navegación inválida '{}' para user={} daily={}", dir, u.getId(), dg.getId());
    }

    layerIndex = clamp(layerIndex, 0, max);
    at.setCurrentLayer(layerIndex);
    attemptRepo.save(at);

    log.debug("Usuario {} movió capa en daily={}: {} -> {}", u.getId(), dg.getId(), previousLayer, layerIndex);

    return "redirect:/guess";
  }

  @PostMapping("/reportSong")
  @Transactional
  public String reportSong(@RequestParam long songId, @RequestParam(required = false) String details, HttpSession session){
    User u = (User) session.getAttribute("u");
    if (u == null) {
      log.info("Intento de submit sin login");
      session.setAttribute("guessMsg", "Inicia sesión para jugar.");
      return "redirect:/login";
    }

    Song song = songRepo.findById(songId).orElse(null);
    if(song == null){
      log.warn("Se ha rechazado el reporte de la canción con ID-{}, debido a que no se encuentra la canción", songId);
      return "redirect:/guess";
    }

    SongReport songReport= new SongReport();
    songReport.setSong(song);
    songReport.setUser(u);
    details = (details != null) ? details.trim() : "";
    songReport.setContentReport(details);
    songReport.setDateRegistered(LocalDateTime.now());
    songReportRepository.save(songReport);
    
    log.info("El usuario con ID-{} reportó la canción con ID-{}", u.getId(), songId);
    session.setAttribute("guessMsg", "Se ha generado el reporte de la canción");
    return "redirect:/guess";
  }

  // ---------------- POST /guess/submit ----------------
  @PostMapping("/submit")
  @Transactional
  public String submit(@RequestParam String answer, HttpSession session) {

    User u = (User) session.getAttribute("u");
    if (u == null) {
      log.info("Intento de submit sin login");
      session.setAttribute("guessMsg", "Inicia sesión para jugar.");
      return "redirect:/login";
    }

    DailyGame dg = getOrCreateDaily(LocalDate.now());
    if (dg == null) {
      session.setAttribute("guessMsg", "guess.noAudioAdmin");
      return "redirect:/guess";
    }
    Song song = dg.getSong();
    List<SongLayer> layers = visibleLayersForDaily(dg, song);
    if (!audioAvailabilityService.hasAllAvailableAudio(layers)) {
      log.info("Usuario {} intentó enviar respuesta sin audios disponibles en daily={}", u.getId(), dg.getId());
      session.setAttribute("guessMsg", "guess.noAudioAdmin");
      return "redirect:/guess";
    }
    int maxLayer = Math.max(0, layers.size() - 1);

    Attempt at = attemptRepo.findByUserAndDailyGame(u, dg)
        .orElseGet(() -> {
          log.info("No existía attempt para user={} daily={}. Se crea uno nuevo desde /submit", u.getId(), dg.getId());
          return createAttempt(u, dg);
        });

    if (at.isSuccess() || at.getTries() >= dg.getMaxTries()) {
      log.info("Usuario {} intentó enviar respuesta en un daily ya terminado (daily={})", u.getId(), dg.getId());
      session.setAttribute("guessMsg", "Ya terminaste el daily de hoy.");
      return "redirect:/guess";
    }

    at.setGuess(answer);

    boolean ok = String.valueOf(song.getId()).equals(answer.trim());
    if (ok) {
      at.setSuccess(true);

      int points = calcPoints(dg, at, maxLayer);
      updateScore(u, true, points);

      log.info("Usuario {} acertó la canción del daily={} y obtuvo {} puntos", u.getId(), dg.getId(), points);
      session.setAttribute("guessMsg", "✅ Correcto! +" + points + " puntos");
      auditHelper.log(u, "GUESSED_SONG", " Se ha acertado al canción con id: " + song.getId());
    } else {
      at.setTries(at.getTries() + 1);

      int newLayer = Math.min(at.getCurrentLayer() + 1, maxLayer);
      at.setCurrentLayer(newLayer);

      if (at.getTries() >= dg.getMaxTries()) {
        updateScore(u, false, 0);
        log.info("Usuario {} agotó sus intentos en daily={}. Derrota final. Canción correcta: {} - {}",
            u.getId(), dg.getId(), song.getTitle(), song.getArtist());
        session.setAttribute("guessMsg", "❌ Sin intentos. Era: " + song.getTitle() + " - " + song.getArtist());
      } else if (newLayer == maxLayer) {
        log.debug("Usuario {} tuvo fallo intermedio en daily={} y desbloqueó la última capa", u.getId(), dg.getId());
        session.setAttribute("guessMsg", "Fallaste. Última capa desbloqueada");
      } else {
        log.debug("Usuario {} tuvo fallo intermedio en daily={} y avanzó a la capa {}", u.getId(), dg.getId(), newLayer);
        session.setAttribute("guessMsg", "Fallaste. Siguiente capa desbloqueada");
      }
    }

    attemptRepo.save(at);
    return "redirect:/guess";
  }

  // ---------------- helpers ----------------

  private Attempt createAttempt(User u, DailyGame dg) {
    log.debug("Creando attempt para user={} daily={}", u.getId(), dg.getId());

    Attempt at = new Attempt();
    at.setUser(u);
    at.setDailyGame(dg);
    at.setCurrentLayer(0);
    at.setTries(0);
    at.setSuccess(false);
    at.setCreatedAt(LocalDateTime.now());
    return attemptRepo.save(at);
  }

  private DailyGame getOrCreateDaily(LocalDate today) {
    return dailyRepo.findByGameDay(today).orElseGet(() -> {
      List<Song> songs = audioAvailabilityService.availableSongs();
      if (songs.isEmpty()) {
        log.error("No se puede crear el daily para {}: ninguna canción tiene todas sus capas mp3 disponibles", today);
        return null;
      }

      Song chosen = songs.get(ThreadLocalRandom.current().nextInt(songs.size()));

      log.info("Creando daily para {} con songId={} ({})", today, chosen.getId(), chosen.getTitle());

      DailyGame dg = new DailyGame();
      dg.setGameDay(today);
      dg.setSong(chosen);
      // maxLayers/maxTries ya tienen defaults en la clase
      dg.setActive(true);
      return dailyRepo.save(dg);
    });
  }

  private List<SongLayer> visibleLayersForDaily(DailyGame dg, Song song) {
    if (song == null) {
      return List.of();
    }
    List<SongLayer> allLayers = layerRepo.findBySongOrderByIdxAsc(song);
    if (dg == null || allLayers.isEmpty()) {
      return allLayers;
    }
    int configuredMaxLayers = Math.max(1, dg.getMaxLayers());
    int visibleLayerCount = Math.min(configuredMaxLayers, allLayers.size());
    return allLayers.subList(0, visibleLayerCount);
  }

  private void updateScore(User u, boolean won, int points) {
    Score sc = scoreRepo.findByUser(u).orElseGet(() -> {
      log.debug("Creando score inicial para user={}", u.getId());
      Score s = new Score();
      s.setUser(u);
      return s;
    });

    sc.setGamesPlayed(sc.getGamesPlayed() + 1);

    if (won) {
      sc.setGamesWon(sc.getGamesWon() + 1);
      sc.setTotalPoints(sc.getTotalPoints() + points);
      sc.setCurrentStreak(sc.getCurrentStreak() + 1);
      sc.setBestStreak(Math.max(sc.getBestStreak(), sc.getCurrentStreak()));

      log.debug("Score actualizado para user={}: win=true, points={}, played={}, won={}, streak={}, bestStreak={}",
          u.getId(), points, sc.getGamesPlayed(), sc.getGamesWon(), sc.getCurrentStreak(), sc.getBestStreak());
    } else {
      sc.setCurrentStreak(0);

      log.debug("Score actualizado para user={}: win=false, played={}, streak reiniciada",
          u.getId(), sc.getGamesPlayed());
    }

    scoreRepo.save(sc);
  }

  private int calcPoints(DailyGame dg, Attempt at, int maxLayer) {
    final int minPoints = 20;
    final int maxPoints = 100;
    final double layerWeight = 0.35;
    final double triesWeight = 0.65;

    int usedLayer = clamp(at.getCurrentLayer(), 0, Math.max(0, maxLayer));
    int usedTries = clamp(at.getTries(), 0, Math.max(0, dg.getMaxTries()));

    double layerPenalty = maxLayer == 0 ? 0 : (double) usedLayer / maxLayer;
    double triesPenalty = dg.getMaxTries() == 0 ? 0 : (double) usedTries / dg.getMaxTries();
    double totalPenalty = (layerPenalty * layerWeight) + (triesPenalty * triesWeight);
    totalPenalty = Math.max(0, Math.min(1, totalPenalty));

    int basePoints = (int) Math.round(maxPoints - ((maxPoints - minPoints) * totalPenalty));

    int difficulty = dg.getSong() == null ? 1 : dg.getSong().getDifficulty();
    int normalizedDifficulty = clamp(difficulty, 1, 4);
    int bonusPct = (normalizedDifficulty - 1) * 10;
    int finalPoints = (int) Math.round(basePoints * (1 + bonusPct / 100.0));

    log.debug("Puntos daily={}: layerPenalty={}, triesPenalty={}, totalPenalty={}, basePoints={}, difficulty={}, bonusPct={}, finalPoints={}",
        dg.getId(), layerPenalty, triesPenalty, totalPenalty, basePoints, normalizedDifficulty, bonusPct, finalPoints);

    return Math.max(minPoints, finalPoints);
  }

  private static int clamp(int v, int min, int max) {
    return Math.max(min, Math.min(max, v));
  }

  private static String normalize(String s) {
    return s == null ? "" : s.trim().toLowerCase();
  }

}
