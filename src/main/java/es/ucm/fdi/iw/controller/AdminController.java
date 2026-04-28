package es.ucm.fdi.iw.controller;

import es.ucm.fdi.iw.LocalData;
import es.ucm.fdi.iw.model.Song;
import es.ucm.fdi.iw.model.SongLayer;
import es.ucm.fdi.iw.repository.SongLayerRepository;
import es.ucm.fdi.iw.repository.SongRepository;
import es.ucm.fdi.iw.repository.AuditWebRepository;
import java.util.List;
import java.util.stream.Collectors;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

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
import jakarta.servlet.http.HttpServletRequest;
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
  private LocalData localData;

  @Value("${app.storage.music-dir:music/layer}")
  private String musicDir;

  @ModelAttribute
  public void populateModel(HttpSession session, Model model) {
    for (String name : new String[] { "u", "url", "ws", "topics"}) {
      model.addAttribute(name, session.getAttribute(name));
    }
  }

  private static final Logger log = LogManager.getLogger(AdminController.class);

  @GetMapping("/")
  public String index(
      @RequestParam(required = false) String audioOk,
      @RequestParam(required = false) String audioErr,
      Model model) {
    log.info("Admin acaba de entrar");
    model.addAttribute("users",
        entityManager.createQuery("select u from User u").getResultList());
    List<Song> songs = songRepository.findAll();
    model.addAttribute("songs", songs);
    model.addAttribute("layerRepo", songLayerRepository);
    model.addAttribute("audioOk", audioOk);
    model.addAttribute("audioErr", audioErr);
    return "admin";
  }

  @PostMapping("/song-layer/{id}/audio")
  @Transactional
  public String uploadLayerAudio(@PathVariable long id, @RequestParam("audio") MultipartFile audioFile) {
    SongLayer layer = songLayerRepository.findById(id).orElse(null);
    if (layer == null) {
      log.warn("Subida rechazada: capa {} no encontrada", id);
      return "redirect:/admin/?audioErr=Capa_no_encontrada";
    }

    if (audioFile == null || audioFile.isEmpty()) {
      log.warn("Subida rechazada para capa {}: fichero vacío", id);
      return "redirect:/admin/?audioErr=Fichero_vacio";
    }

    String originalName = audioFile.getOriginalFilename();
    String safeName = originalName == null ? "" : originalName.trim().toLowerCase();
    if (!safeName.endsWith(".mp3")) {
      log.warn("Subida rechazada para capa {}: extensión inválida ({})", id, originalName);
      return "redirect:/admin/?audioErr=Solo_mp3";
    }

    String contentType = audioFile.getContentType();
    if (contentType != null &&
        !"audio/mpeg".equalsIgnoreCase(contentType) &&
        !"audio/mp3".equalsIgnoreCase(contentType)) {
      log.warn("Subida rechazada para capa {}: contentType inválido ({})", id, contentType);
      return "redirect:/admin/?audioErr=MIME_invalido";
    }

    File out = localData.getFile(musicDir, id + ".mp3");
    try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(out))) {
      stream.write(audioFile.getBytes());
      layer.setAudioUrl("/song-layer/" + id + "/audio");
      songLayerRepository.save(layer);
      log.info("Audio subido para capa {} en {}", id, out.getAbsolutePath());
      return "redirect:/admin/?audioOk=Audio_actualizado_capa_" + id;
    } catch (IOException e) {
      log.error("Error IO al subir audio para capa {}", id, e);
      return "redirect:/admin/?audioErr=Error_guardando_fichero";
    }
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
    
    long publicRooms = 0;
    // A definir la consulta
    /*(Long) entityManager.createQuery("select count(r) from Rooms r where r.publicRoom=true").getSingleResult();*/ 
    
    long privateRooms = 0;
    // A definir la consulta
    /*(Long) entityManager.createQuery("select count(r) from Rooms r where r.publicRoom=false").getSingleResult();*/
    
    long totalWinners = 0;
    // A definir la consulta

    AppStats stats = new AppStats(publicRooms, privateRooms, totalUsers, totalWinners);
    
    model.addAttribute("stats", stats);

    if (actionPerformed != null && actionPerformed.isBlank())
      actionPerformed = null;
    
    model.addAttribute("logs", auditWebRepository.findByFiltersDesc(userId, actionPerformed));
    model.addAttribute("users", entityManager.createQuery("select u from User u").getResultList());
    model.addAttribute("selectedUserId", userId); 

    return "dashboard";
  }
}


