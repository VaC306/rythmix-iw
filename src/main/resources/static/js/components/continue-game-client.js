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

let gameData,
  pianoRoll,
  availableInstruments = null,
  playing = false,
  ended=false;

function subscribeWhenReady(lobbyCode) {
  const interval = setInterval(() => {
    if (ws.stompClient && ws.stompClient.connected) {
      try {
        ws.stompClient.subscribe("/topic/continue/lobby/" + lobbyCode, (m) => handleMessage(JSON.parse(m.body)));
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
        playing = true;
      showScreen(selectors.gameScreenTemplate);
      gameData = m.data;
      setupGameScreen();
      break;
    case "TRACKRECEIVED":
      showScreen(selectors.trackSentTemplate);
      break;
    case "GAMEENDED":
        ended=true;
      showScreen(selectors.endScreenTemplate);
      setupEndScreen(m.data);
  }
}

function updatePlayers(list) {
  console.log("Updating players...");
  document.querySelector(selectors.playerList).innerHTML = "";
  document.querySelectorAll(selectors.playerCounter).forEach((el) => (el.textContent = list.length));
  const ownerBadge = `<span class="badge bg-warning text-dark">Owner</span>`;
  list.forEach((player) => {
    const html = `
    <div class="list-group-item d-flex bg-transparent align-items-center">
      <div class="bg-primary text-white rounded-circle d-flex align-items-center justify-content-center me-3" 
            style="width: 40px; height: 40px;">
          <span>${player.username.substring(0, 1)}</span>
      </div>
      <span class="flex-grow-1 text-start">${player.username}</span>
      ${player.isOwner ? ownerBadge : ""}
    </div>
    `;
    document.querySelector(selectors.playerList).insertAdjacentHTML("beforeend", html);
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
    totalRounds: parseInt(document.querySelector(selectors.numberOfRoundsSelector).value),
    roundInstruments: [],
  };
  for (let i = 0; i < body.totalRounds; i++)
    body.roundInstruments.push(parseInt(document.querySelector(`#select-instrument-round-${i}`).value));
  console.log({ method: "POST", "X-CSRF-TOKEN": config.csrf.value, body: body });
  fetch(`/api/continue/lobby/${lobbyCode}/start`, {
    method: "POST",
    headers: { "Content-Type": "application/json; charset=utf-8", "X-CSRF-TOKEN": config.csrf.value },
    body: JSON.stringify(body),
  }).then((r) => {
    if (r.ok) console.log("Start request sent correctly");
    else {
      console.log(r.status);
    }
  });
}

function sendTrack() {
    playing = false;
  console.log("Sending created track...");
  fetch(`/api/continue/lobby/${lobbyCode}/track/post`, {
    method: "POST",
    headers: { "Content-Type": "application/json; charset=utf-8", "X-CSRF-TOKEN": config.csrf.value },
    body: JSON.stringify(pianoRoll.getEditableTrack()),
  }).then((r) => {
    if (r.ok && !playing && !ended) showScreen(selectors.trackSentTemplate);
    else {
      console.log(r.status);
    }
  });
}

async function setupPianoRoll(selectors) {
  const sequence = gameData.roundData.sequence;
  const instrumentData = gameData.roundData.instrumentData;
  pianoRoll = new PianoRoll({ instrument: instrumentData.program });
  pianoRoll.createVisualElement(selectors.pianoRollContainer, instrumentData.notes);
  pianoRoll.setFixedTracks(sequence.tracks);
  pianoRoll.bindControls(selectors);
  document.querySelector(selectors.sendButton).addEventListener("click", async (e) => {
    sendTrack();
  });
}

async function showInstructionsModal(selectors) {
  const instrumentData = gameData.roundData.instrumentData;
  document.querySelector(selectors.instructionsModalLabel).textContent =
    `Ronda ${gameData.currentRound + 1} de ${gameData.totalRounds}`;
  document.querySelector(selectors.instructionsModalBody).textContent =
    `Crea una pista de ${instrumentData.instrumentName} para la canción!`;
  const bsModal = new bootstrap.Modal(document.querySelector(selectors.instructionsModal));
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
    document.querySelector(selectors.numberOfRoundsSelector).addEventListener("change", () => {
      createInstrumentSelects();
    });
  }
}

function createInstrumentSelects() {
  console.log("dasfkjnl", parseInt(document.querySelector(selectors.numberOfRoundsSelector).value));
  document.querySelector(selectors.instrumentSelectContainer).innerHTML = "";
  for (let i = 0; i < parseInt(document.querySelector(selectors.numberOfRoundsSelector).value); i++) {
    document.querySelector(selectors.instrumentSelectContainer).insertAdjacentHTML(
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
    const r = await fetch(`/api/continue/lobby/${lobbyCode}/sequence/get?currentRound=${gameData.currentRound}`, {
      method: "GET",
      headers: { "Content-Type": "application/json; charset=utf-8" },
    });
    if (r.ok) return r.json();
    if (r.status != 409) return null;
    console.log(`Retrying in ${delay}ms`)
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
  gameData.roundData = {};
  gameData.roundData.instrumentData = await getRoundInstrument();
  gameData.roundData.sequence = await getRoundSequence();
  setupPianoRoll(selectors);
  showInstructionsModal(selectors);
  console.log(gameData.roundData);
}

function createCardHTML(params) {
  const html = `
    <div class="card mb-3">
      <div class="card-body row align-items-center py-5">
        <div class="col col-8">
          <input id="${params.progressBarId}" type="range" class="form-range">
        </div>
        <div class="col col-4">                        
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
          </div>
        </div>
      </div>
    </div>
    `;
  return html;
}

function setupCards(sequences) {
  for (let i in sequences) {
    document.querySelector(selectors.endScreenCardsContainer).insertAdjacentHTML(
      "beforeend",
      createCardHTML({
        progressBarId: `progressBarEnd${i}`,
        playButtonId: `playButtonEnd${i}`,
        pauseButtonId: `pauseButtonEnd${i}`,
        stopButtonId: `stopButtonEnd${i}`,
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
    const r = await fetch(`/api/continue/lobby/${lobbyCode}/sequence/getall`, {
      method: "GET",
      headers: { "Content-Type": "application/json; charset=utf-8" },
    });
    if (r.ok) return r.json();
    if (r.status != 409) return null;
    console.log(`Retrying in ${delay}ms`)
    await new Promise((res) => setTimeout(res, delay));
    delay *= 2;
  }
}

async function setupEndScreen() {
    const sequences = await getAllSequences()
  console.log(sequences);
  setupCards(sequences);
}

document.addEventListener("DOMContentLoaded", (e) => {
  subscribeWhenReady(lobbyCode);
  showScreen(selectors.waitingRoomTemplate);
  setupWaitingRoom();
});
