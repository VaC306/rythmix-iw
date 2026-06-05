package es.ucm.fdi.iw.controller;

import es.ucm.fdi.iw.LocalData;
import es.ucm.fdi.iw.model.Song;
import es.ucm.fdi.iw.model.SongLayer;
import es.ucm.fdi.iw.model.DailyGame;
import es.ucm.fdi.iw.service.AudioAvailabilityService;
import es.ucm.fdi.iw.repository.SongLayerRepository;
import es.ucm.fdi.iw.repository.SongReportRepository;
import es.ucm.fdi.iw.repository.SongRepository;
import es.ucm.fdi.iw.repository.AuditWebRepository;
import es.ucm.fdi.iw.repository.DailyGameRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import es.ucm.fdi.iw.model.Topic;
import es.ucm.fdi.iw.model.AppStats;
import es.ucm.fdi.iw.model.Lorem;
import es.ucm.fdi.iw.model.Message;
import es.ucm.fdi.iw.model.Transferable;
import es.ucm.fdi.iw.model.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;

/**
 * Site administration.
 *
 * Access to this end-point is authenticated - see SecurityConfig
 */
@Controller
@RequestMapping("admin")
public class AdminController {

  @Autowired
  private AuditWebRepository auditWebRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private EntityManager entityManager;

  @Autowired
  private SongRepository songRepository;

  @Autowired
  private SongLayerRepository songLayerRepository;

  @Autowired
  private DailyGameRepository dailyGameRepository;

   @Autowired
  private SongReportRepository songReportRepository;

  private final AudioAvailabilityService audioAvailabilityService;

  @Autowired
  private LocalData localData;

  @Value("${app.storage.music-dir:music/layer}")
  private String musicDir;

  @Value("${app.audio.trim.enabled:false}")
  private boolean trimEnabled;

  @Value("${app.audio.trim.max-seconds:60}")
  private int trimMaxSeconds;

  @Value("${app.audio.compress.bitrate:192k}")
  private String compressBitrate;

  @Value("${app.audio.ffmpeg.command:ffmpeg}")
  private String ffmpegCommand;

  @Value("${app.audio.ffprobe.command:ffprobe}")
  private String ffprobeCommand;

  @ModelAttribute
  public void populateModel(HttpSession session, Model model) {
    for (String name : new String[] { "u", "url", "ws", "topics"}) {
      model.addAttribute(name, session.getAttribute(name));
    }
  }

  private static final Logger log = LogManager.getLogger(AdminController.class);

  public AdminController(AudioAvailabilityService audioAvailabilityService) {
    this.audioAvailabilityService = audioAvailabilityService;
  }

  @GetMapping("/")
  public String index(
      @RequestParam(required = false) String audioOk,
      @RequestParam(required = false) String audioErr,
      @RequestParam(required = false) String dailyOk,
      @RequestParam(required = false) String dailyErr,
      Model model) {
    log.info("Admin acaba de entrar");
    model.addAttribute("users",
        entityManager.createQuery("select u from User u").getResultList());
    List<Song> songs = songRepository.findAll();
    Map<Long, SongAudioStatus> songAudioStatus = buildSongAudioStatus(songs);
    model.addAttribute("songAudioStatus", songAudioStatus);
    dailyGameRepository.findByGameDay(LocalDate.now())
        .ifPresent(dg -> model.addAttribute("todayDailySongId", dg.getSong().getId()));
    model.addAttribute("songs", songs);
    model.addAttribute("layerRepo", songLayerRepository);
    model.addAttribute("audioOk", audioOk);
    model.addAttribute("audioErr", audioErr);
    model.addAttribute("dailyOk", dailyOk);
    model.addAttribute("dailyErr", dailyErr);
    return "admin";
  }

  @PostMapping("/daily/reshuffle")
  @Transactional
  public String reshuffleDailySong() {
    LocalDate today = LocalDate.now();
    DailyGame daily = dailyGameRepository.findByGameDay(today).orElse(null);
    if (daily == null) {
      return "redirect:/admin/?dailyErr=admin.daily.err.notCreated";
    }

    List<Song> availableSongs = songRepository.findAll().stream()
        .filter(audioAvailabilityService::songHasAllLayersAvailable)
        .toList();
    if (availableSongs.isEmpty()) {
      return "redirect:/admin/?dailyErr=admin.daily.err.noCompleteSongs";
    }

    Song currentSong = daily.getSong();
    List<Song> alternatives = availableSongs.stream()
        .filter(s -> s.getId() != currentSong.getId())
        .toList();

    Song chosen;
    if (alternatives.isEmpty()) {
      chosen = currentSong;
      return "redirect:/admin/?dailyOk=admin.daily.ok.onlyOneComplete";
    }

    chosen = alternatives.get(ThreadLocalRandom.current().nextInt(alternatives.size()));
    daily.setSong(chosen);
    dailyGameRepository.save(daily);
    log.info("Daily de {} cambiado manualmente de songId={} a songId={}", today, currentSong.getId(), chosen.getId());
    return "redirect:/admin/?dailyOk=admin.daily.ok.reshuffled";
  }

  @PostMapping("/song-layer/{id}/audio")
  @Transactional
  public String uploadLayerAudio(@PathVariable long id, @RequestParam("audio") MultipartFile audioFile) {
    SongLayer layer = songLayerRepository.findById(id).orElse(null);
    if (layer == null) {
      log.warn("Subida rechazada: capa {} no encontrada", id);
      return "redirect:/admin/?audioErr=admin.audio.err.layerNotFound";
    }

    if (audioFile == null || audioFile.isEmpty()) {
      log.warn("Subida rechazada para capa {}: fichero vacío", id);
      return "redirect:/admin/?audioErr=admin.audio.err.emptyFile";
    }

    String originalName = audioFile.getOriginalFilename();
    String safeName = originalName == null ? "" : originalName.trim().toLowerCase();
    if (!safeName.endsWith(".mp3")) {
      log.warn("Subida rechazada para capa {}: extensión inválida ({})", id, originalName);
      return "redirect:/admin/?audioErr=admin.audio.err.onlyMp3";
    }

    String contentType = audioFile.getContentType();
    if (contentType != null &&
        !"audio/mpeg".equalsIgnoreCase(contentType) &&
        !"audio/mp3".equalsIgnoreCase(contentType)) {
      log.warn("Subida rechazada para capa {}: contentType inválido ({})", id, contentType);
      return "redirect:/admin/?audioErr=admin.audio.err.invalidMime";
    }

    File out = localData.getFile(musicDir, id + ".mp3");
    File tmpIn = localData.getFile(musicDir, id + ".upload.mp3");
    File tmpOut = localData.getFile(musicDir, id + ".processed.mp3");

    try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(tmpIn))) {
      stream.write(audioFile.getBytes());} 
    catch (Exception e) {
      log.error("Error al escribir el audio para capa {}", id, e);
      return "redirect:/admin/?audioErr=admin.audio.err.saveFailed";
    }
    
    try {

      if (trimEnabled) {
        ProcessingResult pr = processWithFfmpeg(tmpIn, tmpOut);
        if (!pr.ok()) {
          log.warn("Subida rechazada para capa {}: {}", id, pr.message());
          return "redirect:/admin/?audioErr=" + pr.message();
        }
        Files.move(tmpOut.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);
      } else {
        Files.move(tmpIn.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }

      layer.setAudioUrl("/song-layer/" + id + "/audio");
      songLayerRepository.save(layer);
      log.info("Audio subido para capa {} en {}", id, out.getAbsolutePath());
      return "redirect:/admin/?audioOk=admin.audio.ok.updated";
    } catch (IOException e) {
      log.error("Error IO al subir audio para capa {}", id, e);
      return "redirect:/admin/?audioErr=admin.audio.err.saveFailed";
    } finally {
      if (tmpIn.exists() && !tmpIn.delete()) {
        log.warn("No se pudo borrar temporal {}", tmpIn.getAbsolutePath());
      }
      if (tmpOut.exists() && !tmpOut.delete()) {
        log.warn("No se pudo borrar temporal {}", tmpOut.getAbsolutePath());
      }
    }
  }

  private ProcessingResult processWithFfmpeg(File input, File output) {
    Double duration = probeDurationSeconds(input);
    if (duration == null) {
      return new ProcessingResult(false, "admin.audio.err.ffprobeFailed");
    }

    double start = 0.0d;
    double clipLength = duration;
    if (duration > trimMaxSeconds) {
      start = (duration - trimMaxSeconds) / 2.0d;
      clipLength = trimMaxSeconds;
    }

    List<String> cmd = List.of(
        ffmpegCommand,
        "-y",
        "-ss", String.format(Locale.US, "%.3f", start),
        "-i", input.getAbsolutePath(),
        "-t", String.format(Locale.US, "%.3f", clipLength),
        "-acodec", "libmp3lame",
        "-b:a", compressBitrate,
        output.getAbsolutePath());

    try {
      Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
      String logs = readProcessOutput(p);
      int code = p.waitFor();
      if (code != 0 || !output.exists() || output.length() == 0) {
        log.error("ffmpeg fallo (code={}): {}", code, logs);
        return new ProcessingResult(false, "admin.audio.err.ffmpegProcessing");
      }
      log.info("Audio procesado con ffmpeg: duracion_original={}s, inicio={}s, duracion_final={}s, bitrate={}",
          duration, start, clipLength, compressBitrate);
      return new ProcessingResult(true, "ok");
    } catch (IOException e) {
      log.error("ffmpeg no disponible", e);
      return new ProcessingResult(false, "admin.audio.err.ffmpegMissing");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Interrumpido al ejecutar ffmpeg", e);
      return new ProcessingResult(false, "admin.audio.err.ffmpegInterrupted");
    }
  }

  private Double probeDurationSeconds(File input) {
    List<String> cmd = List.of(
        ffprobeCommand,
        "-v", "error",
        "-show_entries", "format=duration",
        "-of", "default=noprint_wrappers=1:nokey=1",
        input.getAbsolutePath());
    try {
      Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
      String out = readProcessOutput(p).trim();
      int code = p.waitFor();
      if (code != 0 || out.isEmpty()) {
        log.error("ffprobe fallo (code={}): {}", code, out);
        return null;
      }
      return Double.parseDouble(out);
    } catch (IOException e) {
      log.error("ffprobe no disponible", e);
      return null;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Interrumpido al ejecutar ffprobe", e);
      return null;
    } catch (NumberFormatException e) {
      log.error("No se pudo parsear duracion de ffprobe: {}", e.getMessage());
      return null;
    }
  }

  private String readProcessOutput(Process process) throws IOException {
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        if (!sb.isEmpty()) {
          sb.append('\n');
        }
        sb.append(line);
      }
      return sb.toString();
    }
  }

  private record ProcessingResult(boolean ok, String message) {
  }

  private Map<Long, SongAudioStatus> buildSongAudioStatus(List<Song> songs) {
    Map<Long, SongAudioStatus> statusBySongId = new HashMap<>();
    for (Song song : songs) {
      List<SongLayer> layers = songLayerRepository.findBySongOrderByIdxAsc(song);
      int totalLayers = layers.size();
      int uploadedLayers = 0;
      for (SongLayer layer : layers) {
        if (audioAvailabilityService.isAudioAvailable(layer)) {
          uploadedLayers++;
        }
      }
      boolean complete = totalLayers > 0 && uploadedLayers == totalLayers;
      statusBySongId.put(song.getId(), new SongAudioStatus(totalLayers, uploadedLayers, complete));
    }
    return statusBySongId;
  }

  private record SongAudioStatus(int totalLayers, int uploadedLayers, boolean complete) {
  }

  @PostMapping("/toggle/{id}")
  @Transactional
  @ResponseBody
  public String toggleUser(@PathVariable long id, Model model) {
    log.info("Admin cambia estado de " + id);
    User target = entityManager.find(User.class, id);
    target.setEnabled(!target.isEnabled());
    return "{\"enabled\":" + target.isEnabled() + "}";
  }

  /**
   * Returns JSON with all received messages
   */
  @GetMapping(path = "all-messages", produces = "application/json")
  @Transactional // para no recibir resultados inconsistentes
  @ResponseBody // para indicar que no devuelve vista, sino un objeto (jsonizado)
  public List<Message.Transfer> retrieveMessages(HttpSession session) {
    TypedQuery<Message> query = entityManager.createQuery("select m from Message m", Message.class);
    query.setMaxResults(5);
    query.setFirstResult(0); // para paginar: cambias el 1er resultado
    // devuelve resultado
    return query.getResultList().stream().map(Transferable::toTransfer)
        .collect(Collectors.toList());
  }

  @RequestMapping("/populate")
  @ResponseBody
  @Transactional
  public String populate(Model model) {

    // create some groups
    Topic g1 = new Topic();
    g1.setName("g1");
    g1.setKey(UserController.generateRandomBase64Token(6));
    entityManager.persist(g1);
    Topic g2 = new Topic();
    g2.setName("g2");
    g2.setKey(UserController.generateRandomBase64Token(6));
    entityManager.persist(g2);

    // create some users & assign to groups
    for (int i = 0; i < 15; i++) {
      User u = new User();
      u.setUsername("user" + i);
      u.setPassword(passwordEncoder
          .encode("aa"));
            //UserController.generateRandomBase64Token(9)));
      u.setEnabled(true);
      u.setRoles(User.Role.USER.toString());
      u.setFirstName(Lorem.nombreAlAzar());
      u.setLastName(Lorem.apellidoAlAzar());
      entityManager.persist(u);
      if (i%2 == 0) {
        g1.getMembers().add(u);
        // u.getTopics().add(g1); NO FUNCIONA: propietario es g, no u
      }
      if (i%3 == 0) {
        g2.getMembers().add(u);
      }
    }
    return "{\"admin\": \"populated\"}";
  }


  @GetMapping("/dashboard")
  public String dashboard(@RequestParam(required = false) Long userId, @RequestParam(required = false) String actionPerformed, Model model) {
    long totalUsers = (Long) entityManager.createQuery("select count(u) from User u").getSingleResult();
    
    long publicRooms = (Long) entityManager.createQuery("select count(g) from MIDIGame g where g.isPublic=true").getSingleResult();
    
    long privateRooms = (Long) entityManager.createQuery("select count(g) from MIDIGame g where g.isPublic=false").getSingleResult();
    
    long totalSongs = (Long) entityManager.createQuery("select count(s) from Song s").getSingleResult();

    AppStats stats = new AppStats(publicRooms, privateRooms, totalUsers, totalSongs);
    
    model.addAttribute("stats", stats);

    if (actionPerformed != null && actionPerformed.isBlank())
      actionPerformed = null;
    
    model.addAttribute("logs", auditWebRepository.findByFiltersDesc(userId, actionPerformed));
    model.addAttribute("users", entityManager.createQuery("select u from User u").getResultList());
    model.addAttribute("selectedUserId", userId); 
    if (userId != null) model.addAttribute("selectedUser", entityManager.find(User.class, userId));

    return "dashboard";
  }

  @GetMapping("/reports")
  public String reports(Model model) {
    model.addAttribute("songReports", songReportRepository.findByOrderByDateRegisteredDesc());
    return "songReportsView";
  }
  
}


