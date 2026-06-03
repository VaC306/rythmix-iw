package es.ucm.fdi.iw.controller;

import es.ucm.fdi.iw.auxiliar.AuditHelper;
import es.ucm.fdi.iw.auxiliar.GameUtils;
import es.ucm.fdi.iw.model.ContinueGame;
import es.ucm.fdi.iw.model.ContinueGame.ContinueGameStatus;
import es.ucm.fdi.iw.model.MIDIGame;
import es.ucm.fdi.iw.model.MIDIInstrument;
import es.ucm.fdi.iw.model.MIDISequence;
import es.ucm.fdi.iw.model.MIDITrack;
import es.ucm.fdi.iw.model.Topic;
import es.ucm.fdi.iw.model.User;
import es.ucm.fdi.iw.repository.MIDIGameRepository;
import es.ucm.fdi.iw.repository.MIDIInstrumentRepository;
import es.ucm.fdi.iw.repository.MIDISequenceRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller()
@RequestMapping("/continue")
public class ContinueGameController {

    private static final Logger log = LogManager.getLogger(
        ContinueGameController.class
    );

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private AuditHelper auditHelper;

    @Autowired
    private EntityManager entityManager;

    private final MIDIGameRepository midiGameRepository;
    private final MIDISequenceRepository midiSequenceRepository;
    private final MIDIInstrumentRepository midiInstrumentRepository;

    public ContinueGameController(
        MIDIGameRepository midiGameRepository,
        MIDISequenceRepository midiSequenceRepository,
        MIDIInstrumentRepository midiInstrumentRepository
    ) {
        this.midiSequenceRepository = midiSequenceRepository;
        this.midiGameRepository = midiGameRepository;
        this.midiInstrumentRepository = midiInstrumentRepository;
    }

    @ModelAttribute
    public void populateModel(HttpSession session, Model model) {
        for (String name : new String[] { "u", "url", "ws", "topics" }) {
            model.addAttribute(name, session.getAttribute(name));
        }
        model.addAttribute("gameMode", "continue");
        model.addAttribute("gameName", "Continuacion de Cancion");
    }

    @ModelAttribute
    public void populateLobbyModel(
        @PathVariable(required = false) String lobbyCode,
        Model model
    ) {
        if (lobbyCode != null) {
            model.addAttribute("lobbyCode", lobbyCode);
        }
    }

    @GetMapping("")
    public String mainPage(HttpSession session, Model model) {
        log.debug("Rendering main lobby page");
        return "lobby";
    }

    @PostMapping("/lobby/create")
    @Transactional
    public String createLobby(HttpSession session, Model model)
        throws IOException {
        User u = (User) session.getAttribute("u");
        if (u == null) {
            log.warn("Attempt to create lobby without being logged in");
            model.addAttribute("showError", true);
            model.addAttribute("errorTitleKey", "lobby.error.notlogged.title");
            model.addAttribute("errorBodyKey", "lobby.error.notlogged.body");
            return "lobby";
        }
        ContinueGame game = new ContinueGame();

        String lobbyCode;
        do {
            lobbyCode = GameUtils.generateRandomCode(6);
        } while (midiGameRepository.existsByLobbyCode(lobbyCode));

        game.setStatus(ContinueGameStatus.WAITING);
        game.setLobbyCode(lobbyCode);
        game.setOwner(u);
        game.addPlayer(u);
        game.setCurrentRound(0);
        game.setTotalRounds(4);
        game.setRoundTime(60);
        List<Integer> roundInstruments = Arrays.asList(128, 34, 1, 56);
        game.setRoundInstruments(roundInstruments);
        midiGameRepository.save(game);

        Topic lobbyTopic = new Topic();
        lobbyTopic.setKey("lobby-" + lobbyCode);
        lobbyTopic.setName("Lobby " + lobbyCode);
        lobbyTopic.getMembers().add(u);
        entityManager.persist(lobbyTopic);

        session.setAttribute("currentGame", game);
        log.info("Created lobby {} for user {}", lobbyCode, u.getUsername());

        return "redirect:/continue/lobby/" + lobbyCode;
    }

    @GetMapping("/lobby/{lobbyCode}")
    public String getLobby(
        HttpSession session,
        @PathVariable String lobbyCode,
        Model model
    ) {
        User u = (User) session.getAttribute("u");
        if (u == null) {
            model.addAttribute("showError", true);
            model.addAttribute("errorTitleKey", "lobby.error.notlogged.title");
            model.addAttribute("errorBodyKey", "lobby.error.notlogged.body");
            return "lobby";
        }

        Optional<MIDIGame> optGame = midiGameRepository.findByLobbyCode(
            lobbyCode
        );
        if (optGame.isEmpty() || !(optGame.get() instanceof ContinueGame)) {
            log.warn("Lobby not found for code {}", lobbyCode);
            model.addAttribute("showError", true);
            model.addAttribute("errorTitleKey", "lobby.error.notfound.title");
            model.addAttribute("errorBodyKey", "lobby.error.notfound.body");
            return "lobby";
        }
        ContinueGame game = (ContinueGame) optGame.get();
        if (
            !game
                .getPlayers()
                .stream()
                .anyMatch(lu -> lu.getId() == u.getId())
        ) {
            model.addAttribute("showError", true);
            model.addAttribute("errorTitleKey", "lobby.error.notjoined.title");
            model.addAttribute("errorBodyKey", "lobby.error.notjoined.body");
            return "lobby";
        }

        log.debug(
            "User {} accessing lobby {}",
            u == null ? "anonymous" : u.getUsername(),
            lobbyCode
        );
        if (game.getCurrentRound() == game.getTotalRounds()) {
            game.setStatus(ContinueGameStatus.FINISHED);
            game.setFinished(true);
            game.setDateEnded(LocalDateTime.now());
            midiGameRepository.save(game);
        } else {
            model.addAttribute(
                "instrument",
                game.getRoundInstruments().get(game.getCurrentRound())
            );
        }
        model.addAttribute("isOwner", game.getOwner().getId() == u.getId());
        model.addAttribute("isAdmin", u.hasRole(User.Role.ADMIN));
        model.addAttribute("currentRound", game.getCurrentRound());
        model.addAttribute("totalRounds", game.getTotalRounds());
        model.addAttribute("gameStatus", game.getStatus());
        model.addAttribute(
            "playerList",
            game
                .getPlayers()
                .stream()
                .map(p ->
                    new PlayerInfo(
                        p.getId(),
                        p.getUsername(),
                        game.getOwner().getId() == p.getId()
                    )
                )
                .toList()
        );
        log.info(
            "Lobby {} has {} players",
            lobbyCode,
            game.getPlayers().size()
        );
        return "continue";
    }

    @PostMapping("/lobby/join")
    @Transactional
    public String joinLobby(
        HttpSession session,
        @RequestParam String lobbyCode,
        Model model
    ) throws IOException {
        User u = (User) session.getAttribute("u");
        if (u == null) {
            log.warn("Unauthorized user tried to join lobby {}", lobbyCode);
            model.addAttribute("showError", true);
            model.addAttribute("errorTitleKey", "lobby.error.notlogged.title");
            model.addAttribute("errorBodyKey", "lobby.error.notlogged.body");
            return "lobby";
        }

        auditHelper.log(
            u,
            "JOINED_LOBBY",
            "Se ha unido a la sala con id de sala: " + lobbyCode
        );

        Optional<MIDIGame> optGame = midiGameRepository.findByLobbyCode(
            lobbyCode
        );
        if (optGame.isEmpty() || !(optGame.get() instanceof ContinueGame)) {
            log.warn(
                "User {} tried to join missing lobby {}",
                u.getUsername(),
                lobbyCode
            );
            model.addAttribute("showError", true);
            model.addAttribute("errorTitleKey", "lobby.error.notfound.title");
            model.addAttribute("errorBodyKey", "lobby.error.notfound.body");
            return "lobby";
        }
        ContinueGame game = (ContinueGame) optGame.get();
        log.info("User {} joining lobby {}", u.getUsername(), lobbyCode);
        game.addPlayer(u);

        Topic lobbyTopic = entityManager
            .createNamedQuery("Topic.byKey", Topic.class)
            .setParameter("key", "lobby-" + lobbyCode)
            .getSingleResult();
        lobbyTopic.getMembers().add(u);

        GameUpdate up = new GameUpdate(
            "PLAYERSUPDATED",
            game
                .getPlayers()
                .stream()
                .map(p ->
                    new PlayerInfo(
                        p.getId(),
                        p.getUsername(),
                        game.getOwner().getId() == p.getId()
                    )
                )
                .toList()
        );
        messagingTemplate.convertAndSend(
            "/topic/continue/lobby/" + lobbyCode,
            up
        );
        return "redirect:/continue/lobby/" + lobbyCode;
    }

    public record GameUpdate(String type, Object data) {}

    public record UserRequest(long userId) {}

    public record GameStartRequest(
        long userId,
        int totalRounds,
        List<Integer> roundInstruments
    ) {}

    public record TrackSubmission(long userId, MIDITrack.Transfer track) {}

    public record RoundData(
        MIDIInstrument.Transfer instrumentData,
        MIDISequence.Transfer sequence
    ) {}

    public record GameData(
        int currentRound,
        int totalRounds,
        String status,
        RoundData roundData
    ) {}

    public record PlayerInfo(Long id, String username, boolean isOwner) {}
}
