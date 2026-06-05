const audioContext = new (window.AudioContext || window.webkitAudioContext)();
let songs = [];

async function getFavoriteSongs() {
    const favRes = await fetch("/api/favSong");

    if(favRes.status === 401){
        document.getElementById('login-message').classList.remove('d-none');
        return;
    }

    const favorites = await favRes.json();
    if(favorites.length === 0){
        document.getElementById('empty-songs').classList.remove('d-none');
        return;
    }

    //const favoriteSongsContainer = document.getElementById('favorite-songs-container');
    const instrumentRes = await fetch("/api/game/instrument/getall");
    const instruments = instrumentRes.ok ? await instrumentRes.json() : [];

    const container = document.getElementById('favorite-songs-container');

    favorites.forEach( (fav,i) => {
        const date = fav.gameDate ? new Date(fav.gameDate).toLocaleDateString('es-ES') : '';
        const isContinue = fav.gameType === 'Continuación de Canción';

        const tracksHTML = fav.tracks.map(t => {
            const instr = instruments.find(x => x.program === t.instrument);
            const instrName = instr ? instr.instrumentName : `Instrumento #${t.instrument}`;
            const authorPart = t.author ? `<em class="text-muted">${t.author}</em>` : '';
            return `<li class="list-group-item list-group-item-flush py-1 px-0 small text-center"></span>${instrName}</span> - ${authorPart? `- ${authorPart}`: '' }</li>`;
        }).join('');

        const playersHtml = !isContinue && fav.players?.length ? `<div class="small text-muted mt-1"> Jugadores: ${fav.players.join(', ')}</div>` : '';

        container.insertAdjacentHTML('beforeend', `
            <div class="card mb-3 shadow-sm">
                <div class="card-body">
                    <div class="d-flex justify-content-between align-items-start mb-2">
                        <div>
                            <span class="badge bg-secondary me-3">${fav.gameType}</span>
                            <small class="text-muted">${date}</small>
                            ${playersHtml}
                        </div>
                        <small class="text-muted"> Secuencia #${fav.sequenceId}</small>
                    </div>
                    <ul class="list-group mb-2">${tracksHTML}</ul>
                    <div class="row align-items-center">
                        <div class="col">
                            <input id="favProgress${i}" type="range" class="form-range">
                        </div>
                        <div class="col-auto d-flex  gap-2">
                            <button id="favPlay${i}"  class="btn btn-primary btn-sm"><i class="bi bi-play-fill"></i></button>
                            <button id="favPause${i}" class="btn btn-primary btn-sm"><i class="bi bi-pause-fill"></i></button>
                            <button id="favStop${i}"  class="btn btn-primary btn-sm"><i class="bi bi-stop-fill"></i></button>
                        </div>
                    </div>
                </div>
            </div>
        `);

        let pr = new PianoRoll({audioContext: audioContext});
        pr.setFixedTracks(fav.tracks);
        pr.bindControls({
            playButton: `#favPlay${i}`,
            pauseButton: `#favPause${i}`,
            stopButton: `#favStop${i}`,
            progressBar: `#favProgress${i}`,
        });
        songs.push(pr);
    })
}

async function saveFavoriteSong(midiSequenceId, button) {
    const r = await fetch(`/api/favSong/${midiSequenceId}`, {method: 'POST'});
    if (r.ok) {
        button.classList.remove('btn-outline-success');
        button.classList.add('btn-success');
        button.disabled = true;
    }
}

getFavoriteSongs();