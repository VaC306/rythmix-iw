package es.ucm.fdi.iw.controller;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import es.ucm.fdi.iw.auxiliar.GameUtils;
import es.ucm.fdi.iw.model.ContinueGame;
import es.ucm.fdi.iw.model.ContinueGame.ContinueGameStatus;
import es.ucm.fdi.iw.model.GarticGame;
import es.ucm.fdi.iw.model.GarticGame.GarticGameStatus;
import es.ucm.fdi.iw.model.MIDISequence;
import es.ucm.fdi.iw.model.MIDITrack;
import es.ucm.fdi.iw.model.User;
import es.ucm.fdi.iw.repository.MIDIGameRepository;
import es.ucm.fdi.iw.repository.MIDIInstrumentRepository;
import es.ucm.fdi.iw.repository.MIDISequenceRepository;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;

@RestController
@RequestMapping("/api/continue")
public class ContinueApiController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private final MIDISequenceRepository midiSequenceRepository;
    private final MIDIGameRepository midiGameRepository;
    private final MIDIInstrumentRepository midiInstrumentRepository;

    public ContinueApiController(MIDISequenceRepository midiSequenceRepository, MIDIGameRepository midiGameRepository,
            MIDIInstrumentRepository midiInstrumentRepository) {
        this.midiSequenceRepository = midiSequenceRepository;
        this.midiGameRepository = midiGameRepository;
        this.midiInstrumentRepository = midiInstrumentRepository;
    }

    private static final Logger log = LogManager.getLogger(ContinueApiController.class);

    @PostMapping("/lobby/{lobbyCode}/start")
    @ResponseBody
    @Transactional
    public Map<String, String> startGame(@PathVariable String lobbyCode, HttpSession session,
            HttpServletResponse response, @RequestBody GameStartRequest o) {
        User u = (User) session.getAttribute("u");
        if (u == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not logged in");

        ContinueGame game = (ContinueGame) midiGameRepository.findByLobbyCode(lobbyCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lobby not found"));
        if (game.getOwner().getId() != u.getId())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the owner of this lobby");

        log.info("Starting continue game for lobby {} with {} players", lobbyCode, game.getPlayers().size());

        game.setStatus(ContinueGameStatus.PLAYING);
        game.setTotalRounds(o.totalRounds);
        game.setRoundInstruments(o.roundInstruments);

        for (User p : game.getPlayers()) {
            log.debug("Creating sequence for player {} in lobby {}", p.getUsername(), lobbyCode);
            MIDISequence seq = new MIDISequence();
            seq.setTracks(new LinkedList<MIDITrack>());
            seq.setGame(game);
            game.getSequences().add(seq);
            midiSequenceRepository.save(seq);
            game.getSequenceAssignments().put(p.getId(), seq.getId());
            game.getTrackSubmissions().put(p.getId(), false);
        }
        messagingTemplate.convertAndSend("/topic/continue/lobby/" + lobbyCode,
                new GameUpdate("GAMESTARTED",
                        Map.of("currentRound", game.getCurrentRound(), "totalRounds", game.getTotalRounds(),
                                "instrument", game.getRoundInstruments().get(game.getCurrentRound()))));
        return Map.of("result", "ok");
    }

    @GetMapping("/lobby/{lobbyCode}/sequence/get")
    public MIDISequence.Transfer getMidiSequence(HttpSession session, @PathVariable String lobbyCode,
            @RequestParam int currentRound) {
        User u = (User) session.getAttribute("u");
        if (u == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not logged in");
        ContinueGame game = (ContinueGame) midiGameRepository.findByLobbyCode(lobbyCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lobby not found"));

        if (!game.getPlayers().stream().anyMatch(p -> p.getId() == u.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not in lobby");
        if (game.getStatus() != ContinueGameStatus.PLAYING || game.getCurrentRound() != currentRound)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Game data not ready");
        long sequenceId = game.getSequenceAssignments()
                .get(((User) session.getAttribute("u")).getId());
        return midiSequenceRepository.findById(sequenceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sequence not found"))
                .toTransfer();
    }

    @PostMapping("/lobby/{lobbyCode}/track/post")
    @Transactional
    public Map<String, String> receiveTrack(HttpSession session, @PathVariable String lobbyCode,
            @RequestBody MIDITrack.Transfer submission) {
        // Verificar sesion de usuario
        User u = (User) session.getAttribute("u");
        if (u == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not logged in");

        // Obtenemos la partida
        ContinueGame game = (ContinueGame) midiGameRepository.findByLobbyCode(lobbyCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lobby not found"));

        if (!game.getPlayers().stream().anyMatch(p -> p.getId() == u.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not in lobby");

        if (game.getTrackSubmissions().get(u.getId()) == true)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Track already submitted");

        // Anadimos el nuevo track a la secuencia
        long sequenceId = game.getSequenceAssignments()
                .get(u.getId());
        MIDISequence sequence = midiSequenceRepository.findById(sequenceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sequence not found"));
        sequence.getTracks().add(new MIDITrack(submission, sequence));
        game.getTrackSubmissions().put(u.getId(), true);
        midiSequenceRepository.save(sequence);
        midiGameRepository.save(game);
        if (!game.getTrackSubmissions().containsValue(false)) {
            // Todos los jugadores han acabado
            // Actualizamos la ronda
            game.setCurrentRound(game.getCurrentRound() + 1);
            // El juego ha acabado
            if (game.getCurrentRound() == game.getTotalRounds()) {
                game.setStatus(ContinueGameStatus.FINISHED);
                midiGameRepository.save(game);
                messagingTemplate.convertAndSend("/topic/continue/lobby/" + lobbyCode, new GameUpdate("GAMEENDED", null));
                return Map.of("result", "ok");
            }
            // Ponemos que ningun jugador ha enviado su track
            game.getTrackSubmissions().replaceAll((k, v) -> false);
            // Desplazamos las secuencias de cada jugador
            game.setSequenceAssignments(GameUtils.shiftValuesRight(game.getSequenceAssignments()));
            // Notificamos que empieza una nueva ronda
            // secuencia
            messagingTemplate.convertAndSend("/topic/continue/lobby/" + lobbyCode,
                    new GameUpdate("NEWROUND",
                            Map.of("currentRound", game.getCurrentRound(), "totalRounds", game.getTotalRounds(),
                                    "instrument", game.getRoundInstruments().get(game.getCurrentRound()))));
        }
        return Map.of("result", "ok");
    }

    @GetMapping("/lobby/{lobbyCode}/sequence/getall")
    @Transactional
    public List<MIDISequence.Transfer> getFinalSequences(HttpSession session, @PathVariable String lobbyCode) {
        User u = (User) session.getAttribute("u");
        if (u == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not logged in");
        ContinueGame game = (ContinueGame) midiGameRepository.findByLobbyCode(lobbyCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lobby not found"));

        if (!game.getPlayers().stream().anyMatch(p -> p.getId() == u.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not in lobby");

        if (game.getStatus() != ContinueGameStatus.FINISHED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Game not yet finished");

        return game.getSequences().stream().map(MIDISequence::toTransfer).toList();
    }

    public record GameStartRequest(int totalRounds, List<Integer> roundInstruments) {
    }

    public record GameUpdate(String type, Object data) {
    }
}
