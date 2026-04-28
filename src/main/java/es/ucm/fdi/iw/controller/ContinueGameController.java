package es.ucm.fdi.iw.controller;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import es.ucm.fdi.iw.auxiliar.GameUtils;
import es.ucm.fdi.iw.model.ContinueGame;
import es.ucm.fdi.iw.model.MIDIGame;
import es.ucm.fdi.iw.model.MIDIInstrument;
import es.ucm.fdi.iw.model.MIDISequence;
import es.ucm.fdi.iw.model.MIDITrack;
import es.ucm.fdi.iw.model.User;
import es.ucm.fdi.iw.model.ContinueGame.ContinueGameStatus;
import es.ucm.fdi.iw.repository.MIDIGameRepository;
import es.ucm.fdi.iw.repository.MIDIInstrumentRepository;
import es.ucm.fdi.iw.repository.MIDISequenceRepository;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
@Controller()
@RequestMapping("/continue")
public class ContinueGameController {
    private static final Logger log = LogManager.getLogger(ContinueGame.class);
    // Store pending game updates for polling
    private final ConcurrentHashMap<String, List<GameUpdate>> pendingUpdates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastPollTime = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<ChatMessage>> chatMessages = new ConcurrentHashMap<>();

    private final MIDIGameRepository midiGameRepository;
    private final MIDISequenceRepository midiSequenceRepository;
    private final MIDIInstrumentRepository midiInstrumentRepository;

    public ContinueGameController(MIDIGameRepository midiGameRepository, MIDISequenceRepository midiSequenceRepository, MIDIInstrumentRepository midiInstrumentRepository) {
        this.midiSequenceRepository = midiSequenceRepository;
        this.midiGameRepository = midiGameRepository;
        this.midiInstrumentRepository = midiInstrumentRepository;
    }
    @ModelAttribute
    public void populateModel(HttpSession session, Model model) {
        for (String name : new String[] { "u", "url", "ws", "topics"}) {
          model.addAttribute(name, session.getAttribute(name));
        }
        model.addAttribute("gameMode", "continue");
        model.addAttribute("gameName", "Continuacion de cancion");
    }
    @ModelAttribute
    public void populateLobbyModel(@PathVariable(required = false) String lobbyCode, Model model) {
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
    public String createLobby(HttpSession session, Model model) throws IOException {
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

        session.setAttribute("currentGame", game);
        log.info("Created lobby {} for user {}", lobbyCode, u.getUsername());

        return "redirect:/continue/lobby/" + lobbyCode;
    }
    @GetMapping("/lobby/{lobbyCode}")
    public String getLobby(HttpSession session, @PathVariable String lobbyCode, Model model) {
        User u = (User) session.getAttribute("u");
        if (u == null) {
            model.addAttribute("showError", true);
            model.addAttribute("errorTitleKey", "lobby.error.notlogged.title");
            model.addAttribute("errorBodyKey", "lobby.error.notlogged.body");
            return "lobby";
        }

        Optional<MIDIGame> optGame = midiGameRepository.findByLobbyCode(lobbyCode);
        if (optGame.isEmpty()) {
            log.warn("Lobby not found for code {}", lobbyCode);
            model.addAttribute("showError", true);
            model.addAttribute("errorTitleKey", "lobby.error.notfound.title");
            model.addAttribute("errorBodyKey", "lobby.error.notfound.body");
            return "lobby";
        }
        ContinueGame game = (ContinueGame)optGame.get();
        if (!game.getPlayers().stream().anyMatch(lu->lu.getId() == u.getId())){
            model.addAttribute("showError", true);
            model.addAttribute("errorTitleKey", "lobby.error.notjoined.title");
            model.addAttribute("errorBodyKey", "lobby.error.notjoined.body");
            return "lobby";
        }

        log.debug("User {} accessing lobby {}", u == null ? "anonymous" : u.getUsername(), lobbyCode);
        if(game.getCurrentRound() == game.getTotalRounds()){
            game.setStatus(ContinueGameStatus.FINISHED);
            midiGameRepository.save(game);
        } else {
            model.addAttribute("instrument", game.getRoundInstruments().get(game.getCurrentRound()));
        }
        
        // IMPORTANT: Add these attributes for JavaScript
        model.addAttribute("lobbyCode", lobbyCode);
        model.addAttribute("userId", u.getId());
        model.addAttribute("isOwner", game.getOwner().getId() == u.getId());
        model.addAttribute("currentRound", game.getCurrentRound());
        model.addAttribute("totalRounds", game.getTotalRounds());
        model.addAttribute("gameStatus", game.getStatus());
        model.addAttribute("playerList", game.getPlayers().stream().map((p)->new PlayerInfo(p.getUsername(), game.getOwner().getId() == p.getId())).toList());
        
        log.info("Lobby {} has {} players", lobbyCode, game.getPlayers().size());
        return "continue";
    }
    @PostMapping("/lobby/join")
    @Transactional
    public String joinLobby(HttpSession session, @RequestParam String lobbyCode, Model model) throws IOException {
        User u = (User) session.getAttribute("u");
        if (u == null) {
            log.warn("Unauthorized user tried to join lobby {}", lobbyCode);
            model.addAttribute("showError", true);
            model.addAttribute("errorTitleKey", "lobby.error.notlogged.title");
            model.addAttribute("errorBodyKey", "lobby.error.notlogged.body");
            return "lobby";
        }
        Optional<MIDIGame> optGame = midiGameRepository.findByLobbyCode(lobbyCode);
        if (optGame.isEmpty()) {
            log.warn("User {} tried to join missing lobby {}", u.getUsername(), lobbyCode);
            model.addAttribute("showError", true);
            model.addAttribute("errorTitleKey", "lobby.error.notfound.title");
            model.addAttribute("errorBodyKey", "lobby.error.notfound.body");
            return "lobby";
        }
        ContinueGame game = (ContinueGame) optGame.get();
        log.info("User {} joining lobby {}", u.getUsername(), lobbyCode);
        game.addPlayer(u);
         midiGameRepository.save(game);
        
        // Store update for polling
        addGameUpdate(lobbyCode, new GameUpdate("PLAYERSUPDATED",
                game.getPlayers().stream()
                        .map((p) -> new PlayerInfo(p.getUsername(), game.getOwner().getId() == p.getId())).toList()));
        
        return "redirect:/continue/lobby/" + lobbyCode;
    }
    // NEW: REST endpoint for polling updates
    @GetMapping("/api/lobby/{lobbyCode}/updates")
    @ResponseBody
    public ResponseEntity<List<GameUpdate>> pollUpdates(@PathVariable String lobbyCode, 
                                                        @RequestParam long lastUpdateTime) {
        Optional<MIDIGame> optGame = midiGameRepository.findByLobbyCode(lobbyCode);
        if (optGame.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        List<GameUpdate> updates = pendingUpdates.getOrDefault(lobbyCode, new LinkedList<>());
        List<GameUpdate> newUpdates = new LinkedList<>();
        
        // Only return updates after the last poll time
        for (GameUpdate update : updates) {
            if (update.timestamp > lastUpdateTime) {
                newUpdates.add(update);
            }
        }
        
        return ResponseEntity.ok(newUpdates);
    }

    @PostMapping("/api/lobby/{lobbyCode}/start")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> startGame(@PathVariable String lobbyCode, 
                                       @RequestBody GameStartRequest request,
                                       HttpSession session) {
        User u = (User) session.getAttribute("u");
        if (u == null) {
            return ResponseEntity.status(401).body("Not logged in");
        }
        
        ContinueGame game = (ContinueGame) midiGameRepository.findByLobbyCode(lobbyCode)
                .orElseThrow(() -> new IllegalArgumentException("Invalid lobby code"));
        
        // Verify user is the owner
        if (game.getOwner().getId() != u.getId()) {
            return ResponseEntity.status(403).body("Only the owner can start the game");
        }
        
        log.info("Starting game for lobby {} with {} players", lobbyCode, game.getPlayers().size());
        
        game.setStatus(ContinueGameStatus.PLAYING);
        game.setTotalRounds(request.totalRounds);
        game.setRoundInstruments(request.roundInstruments);
        
        for (User p : game.getPlayers()) {
            log.debug("Creating sequence for player {} in lobby {}", p.getUsername(), lobbyCode);
            MIDISequence seq = new MIDISequence();
            seq.setTracks(new LinkedList<MIDITrack>());
            seq.setGame(game);
            game.getSequences().add(seq);
            midiSequenceRepository.save(seq);
            game.getSequenceAssignments().put(p.getId(), seq.getId());
            game.getTrackSubmissions().put(p.getId(), false);
            
            MIDIInstrument.Transfer instruData = midiInstrumentRepository
                    .findByProgram(game.getRoundInstruments().get(game.getCurrentRound()))
                    .orElseThrow(() -> new IllegalArgumentException("Invalid Program")).toTransfer();
            GameData data = new GameData(game.getCurrentRound(), game.getTotalRounds(), game.getStatus().name(),
                    new RoundData(instruData, seq.toTransfer()));
            
            // Store individual player updates
            addGameUpdate(lobbyCode, new GameUpdate("GAMESTARTED", data, p.getId()));
        }
        
        midiGameRepository.save(game);
        return ResponseEntity.ok().build();
    }

     // NEW: REST endpoint to submit track (replaces WebSocket)
    @PostMapping("/api/lobby/{lobbyCode}/tracks/submit")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> submitTrack(@PathVariable String lobbyCode, 
                                         @RequestBody TrackSubmission submission,
                                         HttpSession session) {
        User u = (User) session.getAttribute("u");
        if (u == null || u.getId() != submission.userId) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        
        ContinueGame game = (ContinueGame) midiGameRepository.findByLobbyCode(lobbyCode)
                .orElseThrow(() -> new IllegalArgumentException("Invalid lobby code"));
        
        // Validate the submission
        if (game.getStatus() != ContinueGameStatus.PLAYING) {
            return ResponseEntity.badRequest().body("Game is not in playing state");
        }
        
        if (game.getTrackSubmissions().getOrDefault(submission.userId, true)) {
            return ResponseEntity.badRequest().body("Track already submitted for this round");
        }
        
        // Save the track to the user's sequence
        long playerSequenceId = game.getSequenceAssignments().get(submission.userId);
        MIDISequence seq = midiSequenceRepository.findById(playerSequenceId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid sequence ID"));
        
        MIDITrack track = new MIDITrack(submission.track, seq);
        seq.getTracks().add(track);
        midiSequenceRepository.save(seq);
        
        game.getTrackSubmissions().put(submission.userId, true);
        
        // Notify the player that their track was received
        addGameUpdate(lobbyCode, new GameUpdate("TRACKRECEIVED", null, submission.userId));
        
        // Check if all players have submitted
        if (!game.getTrackSubmissions().containsValue(false)) {
            processRoundEnd(game, lobbyCode);
        }
        
        midiGameRepository.save(game);
        return ResponseEntity.ok().build();
    }
    private void processRoundEnd(ContinueGame game, String lobbyCode) {
        // Determine best track (voting logic)
        Integer maxVotes = 0;
        long bestSequenceId = -1;
        for (User p : game.getPlayers()) {
            long playerSequenceId = game.getSequenceAssignments().get(p.getId());
            Integer votes = game.get_sequenceVotes().get(playerSequenceId);
            if (votes != null && votes > maxVotes) {
                maxVotes = votes;
                bestSequenceId = playerSequenceId;
            }
        }
        
        // Add best track to all sequences
        if (bestSequenceId != -1) {
            MIDISequence bestSequence = midiSequenceRepository.findById(bestSequenceId)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid sequence ID"));
            MIDITrack bestTrack = bestSequence.getTracks().get(bestSequence.getTracks().size() - 1);
            
            for (User p : game.getPlayers()) {
                long playerSequenceId = game.getSequenceAssignments().get(p.getId());
                MIDISequence playerSeq = midiSequenceRepository.findById(playerSequenceId)
                        .orElseThrow(() -> new IllegalArgumentException("Invalid sequence ID"));
                playerSeq.getTracks().add(bestTrack);
                midiSequenceRepository.save(playerSeq);
            }
        }
        
        game.setCurrentRound(game.getCurrentRound() + 1);
        
        if (game.getCurrentRound() >= game.getTotalRounds()) {
            // Game ended
            addGameUpdate(lobbyCode, new GameUpdate("GAMEENDED",
                    game.getSequences().stream().map(MIDISequence::toTransfer).toList()));
            game.setStatus(ContinueGameStatus.FINISHED);
        } else {
            // New round
            game.getTrackSubmissions().replaceAll((k, v) -> false);
            
            for (User p : game.getPlayers()) {
                long playerSequenceId = game.getSequenceAssignments().get(p.getId());
                MIDISequence seq = midiSequenceRepository.findById(playerSequenceId)
                        .orElseThrow(() -> new IllegalArgumentException("Invalid sequence ID"));
                MIDIInstrument.Transfer instruData = midiInstrumentRepository
                        .findByProgram(game.getRoundInstruments().get(game.getCurrentRound()))
                        .orElseThrow(() -> new IllegalArgumentException("Invalid Program")).toTransfer();
                GameData data = new GameData(game.getCurrentRound(), game.getTotalRounds(), game.getStatus().name(),
                        new RoundData(instruData, seq.toTransfer()));
                
                addGameUpdate(lobbyCode, new GameUpdate("NEWROUND", data, p.getId()));
            }
        }
        
        midiGameRepository.save(game);
    }
     private void addGameUpdate(String lobbyCode, GameUpdate update) {
        update.timestamp = System.currentTimeMillis();
        pendingUpdates.computeIfAbsent(lobbyCode, k -> new LinkedList<>()).add(update);
        
        // Clean old updates (keep last 100)
        List<GameUpdate> updates = pendingUpdates.get(lobbyCode);
        while (updates.size() > 100) {
            updates.remove(0);
        }
    }

    @PostMapping("/api/lobby/{lobbyCode}/chat")
@ResponseBody
public ResponseEntity<?> sendChatMessage(@PathVariable String lobbyCode, 
                                         @RequestBody ChatMessage msg) {
    // Validate user is in the lobby
    Optional<MIDIGame> optGame = midiGameRepository.findByLobbyCode(lobbyCode);
    if (optGame.isEmpty()) {
        return ResponseEntity.notFound().build();
    }
    
    ContinueGame game = (ContinueGame) optGame.get();
    
    // Add timestamp to message
    ChatMessage messageWithTime = new ChatMessage(
        msg.username(), 
        msg.text(), 
        System.currentTimeMillis()
    );
    
    // Store chat message
    chatMessages.computeIfAbsent(lobbyCode, k -> new LinkedList<>()).add(messageWithTime);
    
    // Also add as a game update for the main polling
    addGameUpdate(lobbyCode, new GameUpdate("CHAT_MESSAGE", messageWithTime));
    
    // Clean old messages (keep last 100)
    List<ChatMessage> messages = chatMessages.get(lobbyCode);
    while (messages.size() > 100) {
        messages.remove(0);
    }
    
    return ResponseEntity.ok().build();
}

@GetMapping("/api/lobby/{lobbyCode}/chat/updates")
@ResponseBody
public ResponseEntity<List<ChatMessage>> getChatUpdates(
        @PathVariable String lobbyCode,
        @RequestParam long since) {
    
    List<ChatMessage> messages = chatMessages.getOrDefault(lobbyCode, new LinkedList<>());
    List<ChatMessage> newMessages = new LinkedList<>();
    
    for (ChatMessage msg : messages) {
        if (msg.timestamp() > since) {
            newMessages.add(msg);
        }
    }
    
    return ResponseEntity.ok(newMessages);
}


 public static class GameUpdate {
        public String type;
        public Object data;
        public Long userId; // For user-specific updates
        public long timestamp;
        
        public GameUpdate(String type, Object data) {
            this.type = type;
            this.data = data;
            this.userId = null;
        }
        
        public GameUpdate(String type, Object data, Long userId) {
            this.type = type;
            this.data = data;
            this.userId = userId;
        }
    }
    
    public record GameStartRequest(long userId, int totalRounds, List<Integer> roundInstruments) {}
    public record TrackSubmission(long userId, MIDITrack.Transfer track) {}
    public record RoundData(MIDIInstrument.Transfer instrumentData, MIDISequence.Transfer sequence) {}
    public record GameData(int currentRound, int totalRounds, String status, RoundData roundData) {}
    public record PlayerInfo(String username, boolean isOwner) {}
    public record ChatMessage(String username, String text, long timestamp) {
        // Constructor for messages without timestamp (for receiving from client)
        public ChatMessage(String username, String text) {
            this(username, text, System.currentTimeMillis());
        }
    }
}
