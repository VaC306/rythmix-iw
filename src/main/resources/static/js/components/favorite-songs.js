const audioContext = new (window.AudioContext || window.webkitAudioContext)();
let songs = [];

async function getFavoriteSongs() {
    const r = await fetch("/api/favSong");

    if(r.status === 401){
        document.getElementById('login-message').classList.remove('d-none');
        return;
    }

    const midiSequences = await r.json();
    if(midiSequences.length === 0){
        document.getElementById('empty-songs').classList.remove('d-none');
        return;
    }

    const favoriteSongsContainer = document.getElementById('favorite-songs-container');

    midiSequences.forEach( (s,i) => {
        favoriteSongsContainer.insertAdjacentHTML('beforeend', `
            <div class="card mb-2 shadow-sm">
                <div class="card-body">
                    <div class="row align-items-center">
                        <div class="col">
                            <small class="text-muted">Secuencia #${s.id}</small>
                        </div>
                        <div class="col col-6">
                            <input id="favProgress${i}" type="range" class="form-range">
                        </div>
                        <div class="col d-flex justify-content-end gap-2">
                            <button id="favPlay${i}"  class="btn btn-primary"><i class="bi bi-play-fill"></i></button>
                            <button id="favPause${i}" class="btn btn-primary"><i class="bi bi-pause-fill"></i></button>
                            <button id="favStop${i}"  class="btn btn-primary"><i class="bi bi-stop-fill"></i></button>
                        </div>
                    </div>
                </div>
            </div>
        `);

        let pr = new PianoRoll({audioContext: audioContext});
        pr.setFixedTracks(s.tracks)
        pr.bindControls({
            playButton: `#favPlay${i}`,
            pauseButton: `#favPause${i}`,
            stopButton: `#favStop${i}`,
            progressBar: `#favProgress${i}`,
        })
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