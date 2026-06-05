"use strict";

const selectors = {
  gameContainer: "#game-container",
  startButton: "#start-button",
  playButton: "#play",
  stopButton: "#stop",
  progressBar: "#progress",
  loopButton: "#loop",
  pauseButton: "#pause",
  clearButton: "#clear",
  pianoRollContainer: "#piano-roll",
  instructionsModal: "#instructions-modal",
  instructionsModalLabel: "#instructions-modal-label",
  instructionsModalBody: "#instructions-modal-body",
  sendButton: "#tmpSaveButton",
  playerCounter: ".player-counter",
  playerList: "#player-list",
  endScreenCardsContainer: "#end-screen-cards-container",
  waitingRoomTemplate: "#waiting-room",
  gameScreenTemplate: "#game-screen",
  trackSentTemplate: "#track-sent",
  endScreenTemplate: "#end-screen",
  instrumentSelectContainer: "#instrument-select-container",
  numberOfRoundsSelector: "#select-rounds",
};

let pianoRoll,
  availableInstruments = null,
  waitingForNewRound = false,
  ended = false;

function subscribeWhenReady(lobbyCode) {
  const interval = setInterval(() => {
    if (ws.stompClient && ws.stompClient.connected) {
      try {
        ws.stompClient.subscribe("/topic/gartic/lobby/" + lobbyCode, (m) =>
          handleMessage(JSON.parse(m.body)),
        );
        console.log("Hopefully subscribed to topic and queue");
      } catch (e) {
        console.log("Error, could not subscribe", e);
      }
      clearInterval(interval);
    }
  }, 100);
}

function handleMessage(m) {
  console.log(m.data);
  switch (m.type) {
    case "PLAYERSUPDATED":
      updatePlayers(m.data);
      break;
    case "GAMESTARTED":
    case "NEWROUND":
      waitingForNewRound = false;
      showScreen(selectors.gameScreenTemplate);
      gameData = m.data;
      setupGameScreen();
      break;
    case "TRACKRECEIVED":
      showScreen(selectors.trackSentTemplate);
      break;
    case "GAMEENDED":
      ended = true;
      showScreen(selectors.endScreenTemplate);
      setupEndScreen();
      break;
    case "KICKED":
      window.location.href = "/";
      break;
  }
}

function updatePlayers(list) {
  console.log("Updating players...");
  document.querySelector(selectors.playerList).innerHTML = "";
  document
    .querySelectorAll(selectors.playerCounter)
    .forEach((el) => (el.textContent = list.length));
  const ownerBadge = `<span class="badge bg-warning text-dark">Owner</span>`;
  list.forEach((player) => {
    let kickButton = "";
    if (isOwner && !player.isOwner)
      kickButton = `<button onclick="kickPlayer('${player.username}')" class="btn btn-sm btn-danger">Kick</button>`;
    const html = `
    <div class="list-group-item d-flex bg-transparent align-items-center">
      <div class="bg-primary text-white rounded-circle d-flex align-items-center justify-content-center me-3"
            style="width: 40px; height: 40px;">
          <span>${player.username.substring(0, 1)}</span>
      </div>
      <span class="flex-grow-1 text-start">${player.username}</span>
      ${player.isOwner ? ownerBadge : ""}
      ${kickButton}
    </div>
    `;
    document
      .querySelector(selectors.playerList)
      .insertAdjacentHTML("beforeend", html);
  });
}

function showScreen(selector) {
  console.log(`Showing screen "${selector}"`);
  const gameContainer = document.querySelector(selectors.gameContainer);
  const template = document.querySelector(`${selector}`);
  const instance = template.content.cloneNode(true);
  gameContainer.replaceChildren();
  gameContainer.appendChild(instance);
}

function sendStartRequest() {
  console.log("Sending start request...");
  let body = {
    totalRounds: parseInt(
      document.querySelector(selectors.numberOfRoundsSelector).value,
    ),
    roundInstruments: [],
  };
  for (let i = 0; i < body.totalRounds; i++)
    body.roundInstruments.push(
      parseInt(document.querySelector(`#select-instrument-round-${i}`).value),
    );
  console.log({
    method: "POST",
    "X-CSRF-TOKEN": config.csrf.value,
    body: body,
  });
  fetch(`/api/gartic/lobby/${lobbyCode}/start`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "X-CSRF-TOKEN": config.csrf.value,
    },
    body: JSON.stringify(body),
  }).then((r) => {
    if (r.ok) console.log("Start request sent correctly");
    else {
      console.log(r.status);
    }
  });
}

function sendTrack() {
  waitingForNewRound = true;
  console.log("Sending created track...");
  fetch(`/api/gartic/lobby/${lobbyCode}/track/post`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "X-CSRF-TOKEN": config.csrf.value,
    },
    body: JSON.stringify(pianoRoll.getEditableTrack()),
  }).then((r) => {
    if (r.ok && waitingForNewRound && !ended)
      showScreen(selectors.trackSentTemplate);
    else {
      console.log(r.status);
    }
  });
}

async function setupPianoRoll(selectors) {
  const sequence = gameData.roundData.sequence;
  const instrumentData = gameData.roundData.instrumentData;
  pianoRoll = new PianoRoll({ instrument: instrumentData.program });
  pianoRoll.createVisualElement(
    selectors.pianoRollContainer,
    instrumentData.notes,
  );
  pianoRoll.setFixedTracks(sequence.tracks);
  pianoRoll.bindControls(selectors);
  document
    .querySelector(selectors.sendButton)
    .addEventListener("click", async (e) => {
      sendTrack();
    });
  setupSaveLoadButtons(selectors);
}

function setupSaveLoadButtons(selectors) {
  const saveBtn = document.querySelector("#saveSequenceBtn");
  const loadBtn = document.querySelector("#loadSequenceBtn");
  if (saveBtn) {
    saveBtn.addEventListener("click", () => {
      openSaveModal(
        () => pianoRoll.getEditableTrack(),
      );
    });
  }
  if (loadBtn) {
    const currentInstrument = gameData.roundData.instrumentData.program;
    const currentInstrumentName = gameData.roundData.instrumentData.instrumentName;
    loadBtn.addEventListener("click", () => {
      openLoadModal(
        (trackData) => pianoRoll.loadTrack(trackData),
        currentInstrument,
        currentInstrumentName,
      );
    });
  }
}

function openSaveModal(getTrackFn) {
  const modal = document.querySelector("#save-sequence-modal");
  const nameInput = document.querySelector("#save-sequence-name");
  const errorDiv = document.querySelector("#save-sequence-error");
  const confirmBtn = document.querySelector("#save-sequence-confirm");
  if (!modal) return;

  nameInput.value = "";
  errorDiv.classList.add("d-none");
  const bsModal = new bootstrap.Modal(modal);
  bsModal.show();

  function handleSave() {
    const name = nameInput.value.trim();
    if (!name) {
      errorDiv.textContent = "Debes dar un nombre a la secuencia";
      errorDiv.classList.remove("d-none");
      return;
    }
    confirmBtn.disabled = true;
    const track = getTrackFn();
    fetch("/api/sequence/save", {
      method: "POST",
      headers: {
        "Content-Type": "application/json; charset=utf-8",
        "X-CSRF-TOKEN": config.csrf.value,
      },
      body: JSON.stringify({ name, tracks: [track] }),
    }).then((r) => {
      if (r.ok) {
        bsModal.hide();
      } else {
        errorDiv.textContent = "Error al guardar la secuencia";
        errorDiv.classList.remove("d-none");
      }
    }).finally(() => {
      confirmBtn.disabled = false;
    });
  }

  confirmBtn.onclick = handleSave;
  nameInput.onkeydown = (e) => { if (e.key === "Enter") handleSave(); };
}

function openLoadModal(loadTrackFn, currentInstrument, currentInstrumentName) {
  const modal = document.querySelector("#load-sequence-modal");
  const listDiv = document.querySelector("#load-sequence-list");
  const emptyDiv = document.querySelector("#load-sequence-empty");
  const emptyFilteredDiv = document.querySelector("#load-sequence-empty-filtered");
  const loginWarning = document.querySelector("#load-sequence-login-warning");
  const instrumentInfo = document.querySelector("#load-sequence-instrument-info");
  if (!modal) return;

  listDiv.innerHTML = "";
  emptyDiv.classList.add("d-none");
  emptyFilteredDiv.classList.add("d-none");
  loginWarning.classList.add("d-none");
  instrumentInfo.classList.add("d-none");

  fetch("/api/sequence/saved").then((r) => {
    if (r.status === 401) {
      loginWarning.classList.remove("d-none");
      return [];
    }
    return r.json();
  }).then((sequences) => {
    if (!sequences || sequences.length === 0) {
      emptyDiv.classList.remove("d-none");
      return;
    }

    const matching = sequences.filter(
      (seq) => seq.tracks && seq.tracks.length > 0 && seq.tracks[0].instrument === currentInstrument,
    );

    if (matching.length === 0) {
      emptyFilteredDiv.textContent = `No tienes secuencias guardadas con el instrumento actual (${currentInstrumentName}). Solo puedes cargar secuencias del mismo instrumento.`;
      emptyFilteredDiv.classList.remove("d-none");
      return;
    }

    instrumentInfo.textContent = `Mostrando solo secuencias para el instrumento: ${currentInstrumentName}`;
    instrumentInfo.classList.remove("d-none");

    matching.forEach((seq, i) => {
      const dateStr = seq.createdAt ? new Date(seq.createdAt).toLocaleDateString("es-ES") : "";
      const trackCount = seq.tracks ? seq.tracks.length : 0;
      const card = document.createElement("div");
      card.className = "card mb-3";
      const instrHtml = seq.tracks ? seq.tracks.map((t) => {
        const name = getInstrumentName(t.instrument);
        return `<span class="badge bg-secondary me-1">${name}</span>`;
      }).join("") : "";
      card.innerHTML = `
        <div class="card-body">
          <div class="d-flex justify-content-between align-items-start mb-2">
            <div>
              <strong>${seq.name}</strong>
              <small class="text-muted ms-2">${dateStr}</small>
            </div>
            <div>
              <small class="text-muted me-3">${trackCount} pista(s)</small>
              ${instrHtml}
            </div>
          </div>
          <div class="row align-items-center">
            <div class="col">
              <input id="loadProgress${i}" type="range" class="form-range">
            </div>
            <div class="col-auto d-flex gap-2">
              <button id="loadPlay${i}" class="btn btn-primary btn-sm"><i class="bi bi-play-fill"></i></button>
              <button id="loadPause${i}" class="btn btn-primary btn-sm"><i class="bi bi-pause-fill"></i></button>
              <button id="loadStop${i}" class="btn btn-primary btn-sm"><i class="bi bi-stop-fill"></i></button>
              <button id="loadSelect${i}" class="btn btn-success btn-sm">Cargar</button>
            </div>
          </div>
        </div>
      `;
      listDiv.appendChild(card);

      if (seq.tracks && seq.tracks.length > 0) {
        let pr = new PianoRoll({});
        pr.setFixedTracks(seq.tracks);
        pr.bindControls({
          playButton: `#loadPlay${i}`,
          pauseButton: `#loadPause${i}`,
          stopButton: `#loadStop${i}`,
          progressBar: `#loadProgress${i}`,
        });
      }

      document.querySelector(`#loadSelect${i}`).addEventListener("click", () => {
        if (seq.tracks && seq.tracks.length > 0) {
          loadTrackFn(seq.tracks[0]);
        }
        const bsModal = bootstrap.Modal.getInstance(modal);
        if (bsModal) bsModal.hide();
      });
    });
  });

  const bsModal = new bootstrap.Modal(modal);
  bsModal.show();
}

async function showInstructionsModal(selectors) {
  const instrumentData = gameData.roundData.instrumentData;
  document.querySelector(selectors.instructionsModalLabel).textContent =
    `Ronda ${gameData.currentRound + 1} de ${gameData.totalRounds}`;
  document.querySelector(selectors.instructionsModalBody).textContent =
    `Crea una pista de ${instrumentData.instrumentName} para la canción!`;
  const bsModal = new bootstrap.Modal(
    document.querySelector(selectors.instructionsModal),
  );
  bsModal.show();
}

function setupWaitingRoom() {
  if (isOwner) {
    document.querySelector(selectors.startButton).onclick = sendStartRequest;
    if (availableInstruments == null) {
      fetch("/api/game/instrument/getall").then((r) => {
        if (r.ok)
          r.json().then((list) => {
            availableInstruments = list;
            createInstrumentSelects();
          });
      });
    } else createInstrumentSelects();
    document
      .querySelector(selectors.numberOfRoundsSelector)
      .addEventListener("change", () => {
        createInstrumentSelects();
      });
  }
}

function createInstrumentSelects() {
  console.log(
    "dasfkjnl",
    parseInt(document.querySelector(selectors.numberOfRoundsSelector).value),
  );
  document.querySelector(selectors.instrumentSelectContainer).innerHTML = "";
  for (
    let i = 0;
    i <
    parseInt(document.querySelector(selectors.numberOfRoundsSelector).value);
    i++
  ) {
    document
      .querySelector(selectors.instrumentSelectContainer)
      .insertAdjacentHTML(
        "beforeend",
        `
      <div class="mb-3 form-floating">
        <select id="select-instrument-round-${i}" class="form-select">
          ${"".concat(
            ...availableInstruments.map(
              (ins, idx) =>
                `
            <option value=${ins.program} ${idx > 0 && ins.program != 128 && idx == i - 1 ? "selected" : ""} ${i == 0 && ins.program == 128 ? "selected" : ""}>
              ${ins.instrumentName}
            </option>
            `,
            ),
          )}
        </select>
        <label for="select-instrument-round-${i}" class="form-label">Ronda ${i + 1}</label>
      </div>
      `,
      );
  }
}

async function getRoundSequence(retries = 5) {
  let delay = 500;
  for (let i = 0; i < retries; i++) {
    const r = await fetch(
      `/api/gartic/lobby/${lobbyCode}/sequence/get?currentRound=${gameData.currentRound}`,
      {
        method: "GET",
        headers: { "Content-Type": "application/json; charset=utf-8" },
      },
    );
    if (r.ok) return r.json();
    if (r.status != 409) return null;
    console.log(`Retrying in ${delay}ms`);
    await new Promise((res) => setTimeout(res, delay));
    delay *= 2;
  }
}

async function getRoundInstrument() {
  return fetch(`/api/game/instrument/get/${gameData.instrument}`).then((r) => {
    if (r.ok) return r.json();
    else return null;
  });
}

async function setupGameScreen() {
  try {
    gameData.roundData = {};
    gameData.roundData.instrumentData = await getRoundInstrument();
    gameData.roundData.sequence = await getRoundSequence();
    setupPianoRoll(selectors);
    showInstructionsModal(selectors);
    console.log(gameData.roundData);
  } catch (e) {
    console.error("Error setting up game screen:", e);
  }
}

function getInstrumentName(program) {
  const instrument = availableInstruments?.find((ins) => ins.program === program);
  return instrument ? instrument.instrumentName : `Instrumento #${program}`;
}

function tracksToHtml(tracks) {
  if (!tracks || tracks.length === 0) return "";
  return tracks.map((t) => {
    const name = getInstrumentName(t.instrument);
    return `<span class="badge bg-secondary me-1">${name}</span>`;
  }).join("");
}

function createCardHTML(params) {
  const html = `
    <div class="card mb-3">
      <div class="card-body">
        ${params.tracksHtml ? `<div class="mb-2 small text-muted">${params.tracksHtml}</div>` : ""}
        <div class="d-flex align-items-center py-3 px-2">
          <div class="flex-grow-1">
            <input id="${params.progressBarId}" type="range" class="form-range">
          </div>
          <div class="ms-5">
            <div class="btn-group" role="group">
              <button id="${params.playButtonId}" type="button" class="btn btn-primary" th:title="#{topSongs.play}">
                <i class="bi bi-play-fill"></i>
              </button>
              <button id="${params.pauseButtonId}" type="button" class="btn btn-primary" th:title="#{topSongs.pause}">
                <i class="bi bi-pause-fill"></i>
              </button>
              <button id="${params.stopButtonId}" type="button" class="btn btn-primary" th:title="#{topSongs.stop}">
                <i class="bi bi-stop-fill"></i>
              </button>
              <button id="${params.saveButtonId}" type="button" class="btn btn-outline-success"
                onclick="saveFavoriteSong(${params.midiSequenceId}, this)">
                <i class="bi bi-heart"></i>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
    `;
  return html;
}

function setupCards(sequences) {
  for (let i in sequences) {
    document
      .querySelector(selectors.endScreenCardsContainer)
      .insertAdjacentHTML(
        "beforeend",
        createCardHTML({
          progressBarId: `progressBarEnd${i}`,
          playButtonId: `playButtonEnd${i}`,
          pauseButtonId: `pauseButtonEnd${i}`,
          stopButtonId: `stopButtonEnd${i}`,
          saveButtonId: `saveButtonEnd${i}`,
          midiSequenceId: sequences[i].id,
          tracksHtml: tracksToHtml(sequences[i].tracks),
        }),
      );
    let pr = new PianoRoll({});
    pr.setFixedTracks(sequences[i].tracks);
    pr.bindControls({
      playButton: `#playButtonEnd${i}`,
      pauseButton: `#pauseButtonEnd${i}`,
      stopButton: `#stopButtonEnd${i}`,
      progressBar: `#progressBarEnd${i}`,
    });
  }
}

async function getAllSequences(retries = 5) {
  let delay = 500;
  for (let i = 0; i < retries; i++) {
    const r = await fetch(`/api/gartic/lobby/${lobbyCode}/sequence/getall`, {
      method: "GET",
      headers: { "Content-Type": "application/json; charset=utf-8" },
    });
    if (r.ok) return r.json();
    if (r.status != 409) return null;
    console.log(`Retrying in ${delay}ms`);
    await new Promise((res) => setTimeout(res, delay));
    delay *= 2;
  }
}

async function saveFavoriteSong(midiSequenceId, button) {
  const r = await fetch(`/api/favSong/${midiSequenceId}`, { method: "POST" });
  if (r.ok) {
    button.classList.remove("btn-outline-success");
    button.classList.add("btn-success");
    button.disabled = true;
  }
}

async function setupEndScreen() {
  const sequences = await getAllSequences();
  console.log(sequences);
  setupCards(sequences);
}

function kickPlayer(username) {
  fetch(`/gartic/lobby/${lobbyCode}/kick/${username}`, {
    method: "POST",
    headers: { "X-CSRF-TOKEN": config.csrf.value },
  });
}

document.addEventListener("DOMContentLoaded", (e) => {
  ws.receive = (msg) => {
    if (msg?.type === "KICKED") window.location.href = "/";
  };
  subscribeWhenReady(lobbyCode);
  if (initialGameStatus == "WAITING") {
    showScreen(selectors.waitingRoomTemplate);
    setupWaitingRoom();
  } else if (initialGameStatus == "FINISHED") {
    ended = true;
    showScreen(selectors.endScreenTemplate);
    setupEndScreen();
  } else if (initialGameStatus == "PLAYING") {
    if (!initialTrackSent) {
      waitingForNewRound = false;
      showScreen(selectors.gameScreenTemplate);
      setupGameScreen();
    } else {
      waitingForNewRound = true;
      showScreen(selectors.trackSentTemplate);
    }
  }
});
