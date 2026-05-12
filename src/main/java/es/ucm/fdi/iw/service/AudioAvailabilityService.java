package es.ucm.fdi.iw.service;

import es.ucm.fdi.iw.LocalData;
import es.ucm.fdi.iw.model.Song;
import es.ucm.fdi.iw.model.SongLayer;
import es.ucm.fdi.iw.repository.SongLayerRepository;
import es.ucm.fdi.iw.repository.SongRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

@Service
public class AudioAvailabilityService {

  private final LocalData localData;
  private final SongLayerRepository songLayerRepository;
  private final SongRepository songRepository;

  @Value("${app.storage.music-dir:music/layer}")
  private String musicDir;

  public AudioAvailabilityService(LocalData localData, SongLayerRepository songLayerRepository, SongRepository songRepository) {
    this.localData = localData;
    this.songLayerRepository = songLayerRepository;
    this.songRepository = songRepository;
  }

  public boolean isAudioAvailable(SongLayer layer) {
    File localFile = localData.getFile(musicDir, layer.getId() + ".mp3");
    if (localFile.exists() && localFile.isFile()) {
      return true;
    }
    String url = layer.getAudioUrl();
    if (url == null || url.isBlank()) {
      return false;
    }
    return AudioAvailabilityService.class.getClassLoader().getResource("static" + url) != null;
  }

  public boolean hasAllAvailableAudio(List<SongLayer> layers) {
    if (layers.isEmpty()) {
      return false;
    }
    for (SongLayer layer : layers) {
      if (!isAudioAvailable(layer)) {
        return false;
      }
    }
    return true;
  }

  public boolean songHasAllLayersAvailable(Song song) {
    List<SongLayer> layers = songLayerRepository.findBySongOrderByIdxAsc(song);
    return hasAllAvailableAudio(layers);
  }

  public List<Song> availableSongs() {
    return songRepository.findAll().stream()
        .filter(this::songHasAllLayersAvailable)
        .toList();
  }
}
