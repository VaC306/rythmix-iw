"use strict";

// Get variables from the global scope (set in continue.html)
const isOwner = window.isOwner !== undefined ? window.isOwner : false;
const lobbyCode = window.lobbyCode || '';
const username = window.username || 'Unknown';
const userId = window.userId || null;

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

let gameData, pianoRoll, availableInstruments = null;
let pollingInterval = null;
let lastUpdateTime = Date.now();

// Also expose userId globally for other scripts
window.userId = userId;

function updatePlayers(list) {
  console.log("Updating players...", list);
  const playerListEl = document.querySelector(selectors.playerList);
  if (!playerListEl) {
    console.error("Player list element not found!");
    return;
  }
  
  playerListEl.innerHTML = "";
  
  // Update player counters
  document.querySelectorAll(selectors.playerCounter).forEach((el) => {
    if (el) el.textContent = list.length;
  });
  
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
    playerListEl.insertAdjacentHTML("beforeend", html);
  });
}

function showScreen(selector) {
  console.log(`Showing screen "${selector}"`);
  const gameContainer = document.querySelector(selectors.gameContainer);
  if (!gameContainer) {
    console.error("Game container not found!");
    return;
  }
  
  const template = document.querySelector(selector);
  if (!template) {
    console.error(`Template "${selector}" not found!`);
    return;
  }
  
  const instance = template.content.cloneNode(true);
  gameContainer.replaceChildren();
  gameContainer.appendChild(instance);
}

function handleMessage(m) {
  console.log("Received message:", m);
  
  // Handle user-specific messages
  if (m.userId && m.userId !== userId) {
    console.log("Skipping message for other user:", m.userId);
    return;
  }
  
  switch (m.type) {
    case "PLAYERSUPDATED":
      updatePlayers(m.data);
      break;
    case "GAMESTARTED":
    case "NEWROUND":
      showScreen(selectors.gameScreenTemplate);
      gameData = m.data;
      setupGameScreen();
      break;
    case "TRACKRECEIVED":
      showScreen(selectors.trackSentTemplate);
      // Auto-return to waiting room after 3 seconds? Or stay on track-sent
      setTimeout(() => {
        // Don't automatically redirect, just stay on track-sent until next round
        console.log("Track received, waiting for next round...");
      }, 1000);
      break;
    case "GAMEENDED":
      showScreen(selectors.endScreenTemplate);
      setupEndScreen(m.data);
      if (pollingInterval) clearInterval(pollingInterval);
      break;
    case "CHAT_MESSAGE":
      // Chat messages are handled by game-chat.js
      if (window.displayChatMessage) {
        window.displayChatMessage(m.data);
      }
      break;
    default:
      console.log("Unknown message type:", m.type);
  }
}

async function startPolling(lobbyCode) {
  if (!lobbyCode) {
    console.error("Cannot start polling: no lobby code");
    return;
  }
  
  console.log("Starting polling for lobby:", lobbyCode);
  
  if (pollingInterval) clearInterval(pollingInterval);
  
  pollingInterval = setInterval(async () => {
    try {
      const response = await fetch(`/continue/api/lobby/${lobbyCode}/updates?lastUpdateTime=${lastUpdateTime}`);
      if (response.ok) {
        const updates = await response.json();
        for (const update of updates) {
          handleMessage(update);
          lastUpdateTime = Math.max(lastUpdateTime, update.timestamp);
        }
      } else if (response.status === 404) {
        console.error("Lobby not found");
        if (pollingInterval) clearInterval(pollingInterval);
      }
    } catch (error) {
      console.error("Polling error:", error);
    }
  }, 1000);
}

async function sendStartRequest() {
  console.log("Sending start request...");
  
  const roundsSelect = document.querySelector(selectors.numberOfRoundsSelector);
  if (!roundsSelect) {
    console.error("Rounds selector not found");
    return;
  }
  
  const body = {
    userId: userId,
    totalRounds: parseInt(roundsSelect.value),
    roundInstruments: [],
  };
  
  for (let i = 0; i < body.totalRounds; i++) {
    const instrumentSelect = document.querySelector(`#select-instrument-round-${i}`);
    if (instrumentSelect) {
      body.roundInstruments.push(parseInt(instrumentSelect.value));
    }
  }
  
  console.log("Start request body:", body);
  
  try {
    const response = await fetch(`/continue/api/lobby/${lobbyCode}/start`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(body)
    });
    
    if (!response.ok) {
      const error = await response.text();
      console.error("Failed to start game:", error);
      showError("Failed to start game: " + error);
    } else {
      console.log("Game started successfully");
    }
  } catch (error) {
    console.error("Error starting game:", error);
    showError("Network error while starting game");
  }
}

async function sendTrack() {
  console.log("Sending created track...");
  
  if (!pianoRoll) {
    console.error("PianoRoll not initialized");
    return;
  }
  
  const trackData = {
    userId: userId,
    track: pianoRoll.getEditableTrack()
  };
  
  try {
    const response = await fetch(`/continue/api/lobby/${lobbyCode}/tracks/submit`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(trackData)
    });
    
    if (!response.ok) {
      const error = await response.text();
      console.error("Failed to submit track:", error);
      showError("Failed to submit track: " + error);
    } else {
      console.log("Track submitted successfully");
    }
  } catch (error) {
    console.error("Error submitting track:", error);
    showError("Network error while submitting track");
  }
}

function showError(message) {
  console.error(message);
  // Create a toast notification
  const toastContainer = document.getElementById('toast-container') || createToastContainer();
  const toastId = 'toast-' + Date.now();
  const toastHtml = `
    <div id="${toastId}" class="toast align-items-center text-white bg-danger border-0" role="alert" aria-live="assertive" aria-atomic="true" data-bs-autohide="true" data-bs-delay="5000">
      <div class="d-flex">
        <div class="toast-body">
          ${message}
        </div>
        <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button>
      </div>
    </div>
  `;
  toastContainer.insertAdjacentHTML('beforeend', toastHtml);
  const toastElement = document.getElementById(toastId);
  const toast = new bootstrap.Toast(toastElement);
  toast.show();
  toastElement.addEventListener('hidden.bs.toast', () => toastElement.remove());
}

function createToastContainer() {
  const container = document.createElement('div');
  container.id = 'toast-container';
  container.className = 'position-fixed bottom-0 end-0 p-3';
  container.style.zIndex = '11';
  document.body.appendChild(container);
  return container;
}

async function setupPianoRoll(selectors) {
  const sequence = gameData.roundData.sequence;
  const instrumentData = gameData.roundData.instrumentData;
  pianoRoll = new PianoRoll({ instrument: instrumentData.program });
  pianoRoll.createVisualElement(selectors.pianoRollContainer, instrumentData.notes);
  pianoRoll.setFixedTracks(sequence.tracks);
  pianoRoll.bindControls(selectors);
  const sendButton = document.querySelector(selectors.sendButton);
  if (sendButton) {
    sendButton.addEventListener("click", async (e) => {
      sendTrack();
    });
  }
}

async function showInstructionsModal(selectors) {
  const instrumentData = gameData.roundData.instrumentData;
  const modalLabel = document.querySelector(selectors.instructionsModalLabel);
  const modalBody = document.querySelector(selectors.instructionsModalBody);
  
  if (modalLabel) {
    modalLabel.textContent = `Ronda ${gameData.currentRound + 1} de ${gameData.totalRounds}`;
  }
  if (modalBody) {
    modalBody.textContent = `Crea una pista de ${instrumentData.instrumentName} para la canción!`;
  }
  
  const modalElement = document.querySelector(selectors.instructionsModal);
  if (modalElement) {
    const bsModal = new bootstrap.Modal(modalElement);
    bsModal.show();
  }
}

function setupWaitingRoom() {
  console.log("Setting up waiting room, isOwner:", isOwner);
  
  // Initialize player list from server data
  if (window.initialPlayers && window.initialPlayers.length > 0) {
    updatePlayers(window.initialPlayers);
  }
  
  if (isOwner) {
    const startButton = document.querySelector(selectors.startButton);
    if (startButton) {
      startButton.onclick = sendStartRequest;
    }
    
    if (availableInstruments == null) {
      fetch("/api/game/instrument/getall").then((r) => {
        if (r.ok)
          r.json().then((list) => {
            availableInstruments = list;
            createInstrumentSelects();
          });
      });
    } else {
      createInstrumentSelects();
    }
    
    const roundsSelect = document.querySelector(selectors.numberOfRoundsSelector);
    if (roundsSelect) {
      roundsSelect.addEventListener("change", () => {
        createInstrumentSelects();
      });
    }
  }
  
  // Start polling for updates
  if (lobbyCode) {
    startPolling(lobbyCode);
  }
}

function createInstrumentSelects() {
  const roundsSelect = document.querySelector(selectors.numberOfRoundsSelector);
  if (!roundsSelect) return;
  
  const container = document.querySelector(selectors.instrumentSelectContainer);
  if (!container) return;
  
  const numRounds = parseInt(roundsSelect.value);
  console.log("Creating instrument selects for", numRounds, "rounds");
  container.innerHTML = "";
  
  for (let i = 0; i < numRounds; i++) {
    container.insertAdjacentHTML(
      "beforeend",
      `
      <div class="mb-3 form-floating">
        <select id="select-instrument-round-${i}" class="form-select">
          ${availableInstruments ? availableInstruments.map(
            (ins, idx) => `
            <option value=${ins.program} ${idx > 0 && ins.program != 128 && idx == i - 1 ? "selected" : ""} ${i == 0 && ins.program == 128 ? "selected" : ""}>
              ${ins.instrumentName}
            </option>
            `).join('') : ''}
        </select>
        <label for="select-instrument-round-${i}" class="form-label">Ronda ${i + 1}</label>
      </div>
      `
    );
  }
}

function setupGameScreen() {
  setupPianoRoll(selectors);
  showInstructionsModal(selectors);
  console.log("Game data:", gameData.roundData);
}

function createCardHTML(params) {
  return `
    <div class="card mb-3">
      <div class="card-body row align-items-center py-5">
        <div class="col col-8">
          <input id="${params.progressBarId}" type="range" class="form-range">
        </div>
        <div class="col col-4">                        
          <div class="btn-group" role="group">
            <button id="${params.playButtonId}" type="button" class="btn btn-primary" title="Play">
              <i class="bi bi-play-fill"></i>
            </button>
            <button id="${params.pauseButtonId}" type="button" class="btn btn-primary" title="Pause">
              <i class="bi bi-pause-fill"></i>
            </button>
            <button id="${params.stopButtonId}" type="button" class="btn btn-primary" title="Stop">
              <i class="bi bi-stop-fill"></i>
            </button>
          </div>
        </div>
      </div>
    </div>
  `;
}

function setupCards(sequences) {
  const container = document.querySelector(selectors.endScreenCardsContainer);
  if (!container) return;
  
  container.innerHTML = "";
  for (let i in sequences) {
    container.insertAdjacentHTML(
      "beforeend",
      createCardHTML({
        progressBarId: `progressBarEnd${i}`,
        playButtonId: `playButtonEnd${i}`,
        pauseButtonId: `pauseButtonEnd${i}`,
        stopButtonId: `stopButtonEnd${i}`,
      })
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

function setupEndScreen(sequences) {
  console.log("Setting up end screen with sequences:", sequences);
  setupCards(sequences);
}

// Initialize when DOM is ready
document.addEventListener("DOMContentLoaded", (e) => {
  console.log("DOM loaded, lobbyCode:", lobbyCode, "isOwner:", isOwner, "userId:", userId);
  
  if (!lobbyCode) {
    console.error("No lobby code found!");
    return;
  }
  
  showScreen(selectors.waitingRoomTemplate);
  setupWaitingRoom();
});