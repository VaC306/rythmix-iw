const audioContext = new (window.AudioContext || window.webkitAudioContext)();
const songs = [];

function formatDate(dateValue) {
    if (!dateValue) return "Fecha desconocida";
    return new Date(dateValue).toLocaleString('es-ES', {
        dateStyle: 'medium',
        timeStyle: 'short'
    });
}

function buildTracksHTML(tracks, instrumentMap) {
    return tracks.map(track => {
        const instrumentName = instrumentMap.get(track.instrument) || `Instrumento #${track.instrument}`;
        const author = track.author ? track.author : 'Autor desconocido';
        return `
            <li class="list-group-item px-0 py-2 small">
                <div class="fw-semibold">${instrumentName}</div>
                <div class="text-muted">Pista creada por ${author}</div>
            </li>
        `;
    }).join('');
}

function buildTrackBadgesHTML(tracks, instrumentMap) {
    return tracks.map(track => {
        const instrumentName = instrumentMap.get(track.instrument) || `Instrumento #${track.instrument}`;
        const authorPart = track.author ? ` - <em class="text-muted">${track.author}</em>` : '';
        return `<span class="badge bg-secondary me-1 mb-1">${instrumentName}</span>${authorPart}`;
    }).join(' ');
}

async function getFavoriteSongs() {
    const favRes = await fetch('/api/favSong');

    if (favRes.status === 401) {
        document.getElementById('login-message').classList.remove('d-none');
        return;
    }

    const favorites = await favRes.json();
    if (favorites.length === 0) {
        document.getElementById('empty-songs').classList.remove('d-none');
        return;
    }

    const instrumentRes = await fetch('/api/game/instrument/getall');
    const instruments = instrumentRes.ok ? await instrumentRes.json() : [];
    const instrumentMap = new Map(instruments.map(i => [i.program, i.instrumentName]));

    const container = document.getElementById('favorite-songs-container');

    favorites.forEach((fav, i) => {
        const isContinue = fav.gameType === 'Continuación de Canción';
        const dateText = formatDate(fav.gameDate);
        const tracksHTML = buildTracksHTML(fav.tracks || [], instrumentMap);
        const trackBadgesHTML = buildTrackBadgesHTML(fav.tracks || [], instrumentMap);
        const playersHtml = fav.players?.length
            ? `<div class="small text-muted mt-2">${isContinue ? 'Participantes' : 'Jugadores de la partida'}: ${fav.players.join(', ')}</div>`
            : '';

        container.insertAdjacentHTML('beforeend', `
            <div class="card mb-3 shadow-sm">
                <div class="card-body">
                    <div class="d-flex justify-content-between align-items-start mb-3 gap-3 flex-wrap">
                        <div>
                            <span class="badge bg-secondary me-2">${fav.gameType}</span>
                            <span class="small text-muted">${dateText}</span>
                            ${playersHtml}
                        </div>
                        <small class="text-muted">Secuencia #${fav.sequenceId}</small>
                    </div>
                    <div class="mb-2">${trackBadgesHTML}</div>
                    <div class="small fw-semibold mb-2">Autores e instrumentos de la melodía</div>
                    <ul class="list-group list-group-flush mb-3">${tracksHTML}</ul>
                    <div class="row align-items-center g-2">
                        <div class="col">
                            <input id="favProgress${i}" type="range" class="form-range">
                        </div>
                        <div class="col-auto d-flex gap-2">
                            <button id="favPlay${i}" class="btn btn-primary btn-sm"><i class="bi bi-play-fill"></i></button>
                            <button id="favPause${i}" class="btn btn-primary btn-sm"><i class="bi bi-pause-fill"></i></button>
                            <button id="favStop${i}" class="btn btn-primary btn-sm"><i class="bi bi-stop-fill"></i></button>
                        </div>
                    </div>
                </div>
            </div>
        `);

        const pianoRoll = new PianoRoll({ audioContext });
        pianoRoll.setFixedTracks(fav.tracks || []);
        pianoRoll.bindControls({
            playButton: `#favPlay${i}`,
            pauseButton: `#favPause${i}`,
            stopButton: `#favStop${i}`,
            progressBar: `#favProgress${i}`,
        });
        songs.push(pianoRoll);
    });
}

async function saveFavoriteSong(midiSequenceId, button) {
    const r = await fetch(`/api/favSong/${midiSequenceId}`, { method: 'POST' });
    if (r.ok) {
        button.classList.remove('btn-outline-success');
        button.classList.add('btn-success');
        button.disabled = true;
    }
}

getFavoriteSongs();
