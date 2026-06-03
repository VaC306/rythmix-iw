"use strict";

function initGameHistory() {
  const items = document.querySelectorAll("[data-game-history-id]");
  for (const container of items) {
    const gameId = container.dataset.gameHistoryId;
    const sequencesRaw = document.getElementById(`sequences-${gameId}`)?.textContent;
    if (!sequencesRaw) continue;
    let sequences;
    try {
      sequences = JSON.parse(sequencesRaw);
    } catch (e) {
      console.error("Failed to parse sequences for game", gameId, e);
      continue;
    }
    const cardsContainer = container.querySelector(".history-cards-container");
    if (!cardsContainer) continue;

    for (let i = 0; i < sequences.length; i++) {
      const seq = sequences[i];
      const cardId = `hist-${gameId}-${i}`;
      cardsContainer.insertAdjacentHTML("beforeend", `
        <div class="card mb-2">
          <div class="card-body d-flex align-items-center py-3 px-3">
            <div class="flex-grow-1 me-3">
              <input id="progress-${cardId}" type="range" class="form-range">
            </div>
            <div class="btn-group btn-group-sm" role="group">
              <button id="play-${cardId}" type="button" class="btn btn-primary" title="Reproducir">
                <i class="bi bi-play-fill"></i>
              </button>
              <button id="pause-${cardId}" type="button" class="btn btn-primary" title="Pausar">
                <i class="bi bi-pause-fill"></i>
              </button>
              <button id="stop-${cardId}" type="button" class="btn btn-primary" title="Parar">
                <i class="bi bi-stop-fill"></i>
              </button>
            </div>
          </div>
        </div>
      `);

      let pr = new PianoRoll({});
      pr.setFixedTracks(seq.tracks);
      pr.bindControls({
        playButton: `#play-${cardId}`,
        pauseButton: `#pause-${cardId}`,
        stopButton: `#stop-${cardId}`,
        progressBar: `#progress-${cardId}`,
      });
    }
  }
}

document.addEventListener("DOMContentLoaded", initGameHistory);
