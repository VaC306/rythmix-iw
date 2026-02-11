package es.ucm.fdi.iw.controller;

import es.ucm.fdi.iw.model.User;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/guess")
public class GuessController {
    
    private static final Logger log = LogManager.getLogger(GuessController.class);
    
    @ModelAttribute
    public void populateModel(HttpSession session, Model model) {
        User u = (User) session.getAttribute("u");
        model.addAttribute("u", u);
        model.addAttribute("logged", u != null);
    }

    private static List<Layer> layersFor(String songFolder) {
        return List.of(
            new Layer("Batería", "/music/" + songFolder + "/01_drums.mp3"),
            new Layer("Batería + Bajo", "/music/" + songFolder + "/02_drums_bass.mp3"),
            new Layer("Batería + Bajo + Melodía", "/music/" + songFolder + "/03_drums_bass_melody.mp3"),
            new Layer("Completa (con voz)", "/music/" + songFolder + "/04_full.mp3")
        );
    }


    private static final Map<String, Song> SONGS = Map.of(
        "song1", new Song("song1", "Get Lucky", "Daft Punk", "2013", "Pop", layersFor("song1")),
        "song2", new Song("song2", "Levitating", "Dua Lipa", "2020", "Pop", layersFor("song2")),
        "song3", new Song("song3", "Billie jean", "Michael jackson", "1982", "Pop", layersFor("song3")),
        "song4", new Song("song4", "Blinding lights", "The Weeknd", "2020", "R&B/Soul", layersFor("song4")),
        "song5", new Song("song5", "Take on me", "a-ha", "1985", "Synth pop", layersFor("song5"))
    );

    @GetMapping
    public String page(HttpSession session, Model model) {
        String songId = (String) session.getAttribute("guessSongId");
        Integer layerIndex = (Integer) session.getAttribute("guessLayerIndex");

        if (songId == null || !SONGS.containsKey(songId)) {
            songId = randomSongId();
            layerIndex = 0;
        }

        if (layerIndex == null) layerIndex = 0;

        Song s = SONGS.get(songId);
        layerIndex = clamp(layerIndex, 0, s.layers().size() - 1);

        session.setAttribute("guessSongId", songId);
        session.setAttribute("guessLayerIndex", layerIndex);

        model.addAttribute("song", s);
        model.addAttribute("songList", SONGS.values());
        model.addAttribute("layerIndex", layerIndex);
        model.addAttribute("currentLayer", s.layers().get(layerIndex));
        model.addAttribute("maxLayer", s.layers().size() - 1);

        Object msg = session.getAttribute("guessMsg");
        if (msg != null) {
            model.addAttribute("msg", msg.toString());
            session.removeAttribute("guessMsg");
        }
    
        return "guess";
    }

    @PostMapping("/nav")
    public String nav(@RequestParam String dir, HttpSession session) {
        String songId = (String) session.getAttribute("guessSongId");
        Integer layerIndex = (Integer) session.getAttribute("guessLayerIndex");

        if (songId == null || !SONGS.containsKey(songId)) songId = "song1";
        if (layerIndex == null) layerIndex = 0;

        Song s = SONGS.get(songId);
        int max = s.layers().size() - 1;

        if ("prev".equals(dir)) layerIndex--;
        if ("next".equals(dir)) layerIndex++;

        layerIndex = clamp(layerIndex, 0, max);
        session.setAttribute("guessLayerIndex", layerIndex);

        return "redirect:/guess";
    }

    @PostMapping("/submit")
    public String submit(@RequestParam String answer, HttpSession session) {

        String songId = (String) session.getAttribute("guessSongId");
        Integer layerIndex = (Integer) session.getAttribute("guessLayerIndex");

        if (songId == null || !SONGS.containsKey(songId)) songId = "song1";
        if (layerIndex == null) layerIndex = 0;

        Song s = SONGS.get(songId);
        int max = s.layers().size() - 1;

        boolean ok = answer.equals(s.id());

        if (ok) {
            String nextSongId = randomSongIdDifferentFrom(songId);

            session.setAttribute("guessSongId", nextSongId);
            session.setAttribute("guessLayerIndex", 0);
            session.setAttribute("guessMsg", "Correcto! Nueva canción desbloqueada");
        }
        else {
            int newIndex = Math.min(layerIndex + 1, max);
            session.setAttribute("guessLayerIndex", newIndex);

            if (newIndex == max) {
                session.setAttribute("guessMsg", "Fallaste. Última capa desbloqueada");
            } else {
                session.setAttribute("guessMsg", "Fallaste. Siguiente capa desbloqueada");
            }
        }

        return "redirect:/guess";
    }


    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    private static String randomSongId() {
        var keys = SONGS.keySet().toArray(new String[0]);
        return keys[ThreadLocalRandom.current().nextInt(keys.length)];
    }

    private static String randomSongIdDifferentFrom(String currentId) {
    var keys = SONGS.keySet().toArray(new String[0]);

    if (keys.length <= 1) {
        return currentId;
    }

    String next;
    do {
        next = keys[java.util.concurrent.ThreadLocalRandom.current()
                .nextInt(keys.length)];
    } while (next.equals(currentId));

    return next;
}


    public record Layer(String name, String audioUrl) {}
    public record Song(String id, String title, String artist, String year, String genre, List<Layer> layers) {}
}
