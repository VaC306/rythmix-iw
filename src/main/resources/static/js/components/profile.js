"use strict";

const profileAudioContext = new (window.AudioContext || window.webkitAudioContext)();

async function loadInstrumentMap() {
  const response = await fetch("/api/game/instrument/getall");
  if (!response.ok) {
    return new Map();
  }
  const instruments = await response.json();
  return new Map(instruments.map(i => [i.program, i.instrumentName]));
}

function buildTrackSummary(tracks, instrumentMap) {
  if (!tracks?.length) {
    return '<div class="small text-muted mb-2">Sin pistas registradas.</div>';
  }

  const items = tracks.map(track => {
    const instrumentName = instrumentMap.get(track.instrument) || `Instrumento #${track.instrument}`;
    const authorText = track.author ? ` · ${track.author}` : "";
    return `<li class="list-group-item px-0 py-1 small">${instrumentName}${authorText}</li>`;
  }).join("");

  return `<ul class="list-group list-group-flush mb-3">${items}</ul>`;
}

async function initGameHistory() {
  const instrumentMap = await loadInstrumentMap();
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

    if (!sequences.length) {
      cardsContainer.innerHTML = '<div class="alert alert-light border mb-0">No hay melodias finales guardadas para esta partida.</div>';
      continue;
    }

    for (let i = 0; i < sequences.length; i++) {
      const seq = sequences[i];
      const cardId = `hist-${gameId}-${i}`;
      const title = sequences.length === 1 ? "Melodia final" : `Melodia final ${i + 1}`;
      const trackSummary = buildTrackSummary(seq.tracks, instrumentMap);

      cardsContainer.insertAdjacentHTML("beforeend", `
        <div class="card mb-3 border-light-subtle shadow-sm">
          <div class="card-body py-3 px-3">
            <div class="d-flex justify-content-between align-items-center mb-2 gap-2 flex-wrap">
              <strong class="small text-uppercase">${title}</strong>
              <span class="badge text-bg-light">${seq.tracks?.length || 0} pistas</span>
            </div>
            ${trackSummary}
            <div class="d-flex align-items-center gap-3 flex-wrap">
              <div class="flex-grow-1">
                <input id="progress-${cardId}" type="range" class="form-range mb-0">
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
        </div>
      `);

      const pianoRoll = new PianoRoll({ audioContext: profileAudioContext });
      pianoRoll.setFixedTracks(seq.tracks || []);
      pianoRoll.bindControls({
        playButton: `#play-${cardId}`,
        pauseButton: `#pause-${cardId}`,
        stopButton: `#stop-${cardId}`,
        progressBar: `#progress-${cardId}`,
      });
    }
  }
}

document.addEventListener("DOMContentLoaded", initGameHistory);
