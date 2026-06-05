package es.ucm.fdi.iw.controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import es.ucm.fdi.iw.model.ContinueGame;
import es.ucm.fdi.iw.model.ContinueGame.ContinueGameStatus;
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

        // Create final track sequence that accumulates all winning tracks
        MIDISequence finalTrack = new MIDISequence();
        finalTrack.setTracks(new LinkedList<MIDITrack>());
        finalTrack.setGame(game);
        game.getSequences().add(finalTrack);
        midiSequenceRepository.save(finalTrack);
        game.setFinalSequenceId(finalTrack.getId());

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

    @PostMapping("/lobby/{lobbyCode}/options")
    @Transactional
    public LobbyOptionsUpdate updateLobbyOptions(
            @PathVariable String lobbyCode,
            HttpSession session,
            @RequestBody LobbyOptionsUpdate request) {
        User u = (User) session.getAttribute("u");
        if (u == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not logged in");

        ContinueGame game = (ContinueGame) midiGameRepository.findByLobbyCode(lobbyCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lobby not found"));
        if (game.getOwner().getId() != u.getId())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the owner of this lobby");
        if (game.getStatus() != ContinueGameStatus.WAITING)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Lobby is no longer configurable");

        int totalRounds = request.totalRounds();
        List<Integer> roundInstruments = request.roundInstruments();
        if (totalRounds < 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Total rounds must be positive");
        if (roundInstruments == null || roundInstruments.size() != totalRounds)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid round instruments");

        game.setTotalRounds(totalRounds);
        game.setRoundInstruments(new ArrayList<>(roundInstruments));
        midiGameRepository.save(game);

        LobbyOptionsUpdate payload = new LobbyOptionsUpdate(game.getTotalRounds(), List.copyOf(game.getRoundInstruments()));
        messagingTemplate.convertAndSend("/topic/continue/lobby/" + lobbyCode,
                new GameUpdate("LOBBYOPTIONSUPDATED", payload));
        return payload;
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

    @PostMapping("/lobby/{lobbyCode}/vote")
    @Transactional
    public Map<String, String> receiveVote(HttpSession session, @PathVariable String lobbyCode,
            @RequestBody VoteRequest voteRequest) {
        User u = (User) session.getAttribute("u");
        if (u == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not logged in");

        ContinueGame game = (ContinueGame) midiGameRepository.findByLobbyCode(lobbyCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lobby not found"));

        if (!game.getPlayers().stream().anyMatch(p -> p.getId() == u.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not in lobby");

        if (game.get_voterToVoted().containsKey(u.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vote already submitted");

        // Encontrar el jugador que posee la sequencia votada por el jugador que mando el voto
        long votedSequenceId = voteRequest.sequenceId();
        Long votedPlayerId = game.getSequenceAssignments().entrySet().stream()
                .filter(e -> e.getValue() == votedSequenceId)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sequence owner not found"));

        game.get_voterToVoted().put(u.getId(), votedPlayerId);
        game.getVoteCounts().put(votedSequenceId, game.getVoteCounts().getOrDefault(votedSequenceId, 0) + 1);
        messagingTemplate.convertAndSend("/topic/continue/lobby/" + lobbyCode,
                        new GameUpdate("UPDATEVOTES",
                                Map.of("sequences", game.getSequences().stream()
                                        .filter(s -> s.getId() != game.getFinalSequenceId())
                                        .map(MIDISequence::toTransfer).toList(),
                                        "voteCounts", game.getVoteCounts())));
        midiGameRepository.save(game);

        if (game.get_voterToVoted().size() == game.getPlayers().size()) {
            // Contar votos por Jugador/Sequencia
            Map<Long, Long> voteCountPerPlayer = new HashMap<>();
            for (Long votedForPlayerId : game.get_voterToVoted().values()) {
                voteCountPerPlayer.merge(votedForPlayerId, 1L, Long::sum);
            }

            // Encontrar el jugador con mas votos.
            Long winningPlayerId = voteCountPerPlayer.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No votes found"));

            long winningSequenceId = game.getSequenceAssignments().get(winningPlayerId);
            MIDISequence winningSequence = midiSequenceRepository.findById(winningSequenceId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Winning sequence not found"));

            // Copy winning tracks into the accumulated final track
            MIDISequence finalTrack = midiSequenceRepository.findById(game.getFinalSequenceId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Final track not found"));
            for (MIDITrack track : winningSequence.getTracks()) {
                MIDITrack newTrack = new MIDITrack();
                newTrack.setSequence(finalTrack);
                newTrack.setInstrument(track.getInstrument());
                newTrack.setNotes(track.getNotes());
                newTrack.setAuthor(track.getAuthor());
                finalTrack.getTracks().add(newTrack);
            }

            // Clear each player's sequence and copy winning tracks into them
            for (User player : game.getPlayers()) {
                long playerSequenceId = game.getSequenceAssignments().get(player.getId());
                MIDISequence playerSequence = midiSequenceRepository.findById(playerSequenceId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player sequence not found"));
                playerSequence.getTracks().clear();
                for (MIDITrack track : winningSequence.getTracks()) {
                    MIDITrack newTrack = new MIDITrack();
                    newTrack.setSequence(playerSequence);
                    newTrack.setInstrument(track.getInstrument());
                    newTrack.setNotes(track.getNotes());
                    newTrack.setAuthor(track.getAuthor());
                    playerSequence.getTracks().add(newTrack);
                }
            }

            // Reset votes for next round
            game.get_voterToVoted().clear();
            game.getVoteCounts().clear();

            // Increment round
            game.setCurrentRound(game.getCurrentRound() + 1);

            if (game.getCurrentRound() == game.getTotalRounds()) {
                game.setStatus(ContinueGameStatus.FINISHED);
                game.setFinished(true);
                game.setDateEnded(LocalDateTime.now());
                midiGameRepository.save(game);
                final String code1 = lobbyCode;
                final SimpMessagingTemplate msg1 = messagingTemplate;
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        msg1.convertAndSend("/topic/continue/lobby/" + code1, new GameUpdate("GAMEENDED", null));
                    }
                });
                return Map.of("result", "ok");
            }

            game.getTrackSubmissions().replaceAll((k, v) -> false);
            midiGameRepository.save(game);

            final String code2 = lobbyCode;
            final int newRound = game.getCurrentRound();
            final int totalRounds = game.getTotalRounds();
            final int instrument = game.getRoundInstruments().get(game.getCurrentRound());
            final String winningPlayerUsername = game.getPlayers().stream()
                    .filter(player -> player.getId() == winningPlayerId)
                    .map(User::getUsername)
                    .findFirst()
                    .orElse("Jugador desconocido");
            final SimpMessagingTemplate msg2 = messagingTemplate;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    msg2.convertAndSend("/topic/continue/lobby/" + code2,
                            new GameUpdate("NEWROUND",
                                    Map.of("currentRound", newRound, "totalRounds", totalRounds,
                                            "instrument", instrument,
                                            "winnerUsername", winningPlayerUsername)));
                }
            });
        }
        return Map.of("result", "ok");
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
            MIDITrack track = new MIDITrack(submission, sequence);
            track.setAuthor(u.getUsername());
            sequence.getTracks().add(track);
            game.getTrackSubmissions().put(u.getId(), true);
            midiSequenceRepository.save(sequence);
            midiGameRepository.save(game);
            if (!game.getTrackSubmissions().containsValue(false)) {
                // Todos los jugadores han acabado - entrar en votación
                game.getTrackSubmissions().replaceAll((k, v) -> false);
                // Initialize vote counts for all sequences (except final track)
                game.getVoteCounts().clear();
                for (MIDISequence seq : game.getSequences()) {
                    if (seq.getId() != game.getFinalSequenceId()) {
                        game.getVoteCounts().put(seq.getId(), 0);
                    }
                }
                midiGameRepository.save(game);
                final String code = lobbyCode;
                final SimpMessagingTemplate msg = messagingTemplate;
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        msg.convertAndSend("/topic/continue/lobby/" + code,
                                new GameUpdate("VOTINGSTARTED",
                                        Map.of("sequences", game.getSequences().stream()
                                                .filter(s -> s.getId() != game.getFinalSequenceId())
                                                .map(MIDISequence::toTransfer).toList(),
                                                "voteCounts", game.getVoteCounts())));
                    }
                });
            }
            return Map.of("result", "ok");
        }
    @GetMapping("/lobby/{lobbyCode}/sequence/getall")
    @Transactional
    public MIDISequence.Transfer getFinalSequences(HttpSession session, @PathVariable String lobbyCode) {
        User u = (User) session.getAttribute("u");
        if (u == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not logged in");
        ContinueGame game = (ContinueGame) midiGameRepository.findByLobbyCode(lobbyCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lobby not found"));

        if (!game.getPlayers().stream().anyMatch(p -> p.getId() == u.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not in lobby");

        if (game.getStatus() != ContinueGameStatus.FINISHED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Game not yet finished");

        MIDISequence finalTrack = midiSequenceRepository.findById(game.getFinalSequenceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Final track not found"));
        return finalTrack.toTransfer();
    }

    public record GameStartRequest(int totalRounds, List<Integer> roundInstruments) {
    }

    public record VoteRequest(long sequenceId) {
    }

    public record LobbyOptionsUpdate(int totalRounds, List<Integer> roundInstruments) {
    }

    public record GameUpdate(String type, Object data) {
    }
}
