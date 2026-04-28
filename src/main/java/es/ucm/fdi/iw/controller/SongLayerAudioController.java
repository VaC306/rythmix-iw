package es.ucm.fdi.iw.controller;

import es.ucm.fdi.iw.LocalData;
import es.ucm.fdi.iw.model.SongLayer;
import es.ucm.fdi.iw.repository.SongLayerRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.File;
import java.io.IOException;

@Controller
public class SongLayerAudioController {

  private static final Logger log = LogManager.getLogger(SongLayerAudioController.class);

  @Autowired
  private SongLayerRepository songLayerRepository;

  @Autowired
  private LocalData localData;

  @Value("${app.storage.music-dir:music/layer}")
  private String musicDir;

  @GetMapping("/song-layer/{id}/audio")
  public ResponseEntity<Resource> getLayerAudio(@PathVariable long id) {
    SongLayer layer = songLayerRepository.findById(id).orElse(null);
    if (layer == null) {
      log.warn("Audio no encontrado: capa {} no existe", id);
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    File localFile = localData.getFile(musicDir, id + ".mp3");
    if (!localFile.exists() || !localFile.isFile()) {
      log.warn("Audio no encontrado para capa {} en {}", id, localFile.getAbsolutePath());
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    Resource resource = new FileSystemResource(localFile);
    try {
      resource.contentLength();
      return ResponseEntity.ok()
          .contentType(MediaType.valueOf("audio/mpeg"))
          .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=layer-" + id + ".mp3")
          .body(resource);
    } catch (IOException e) {
      log.error("Error IO sirviendo audio de capa {}", id, e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }
}
