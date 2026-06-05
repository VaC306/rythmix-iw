const audioContext = new (window.AudioContext || window.webkitAudioContext)();

async function getSavedSequences() {
    const res = await fetch("/api/sequence/saved");

    if (res.status === 401) {
        document.getElementById("login-message").classList.remove("d-none");
        return;
    }

    const sequences = await res.json();
    if (sequences.length === 0) {
        document.getElementById("empty-sequences").classList.remove("d-none");
        return;
    }

    const instrumentRes = await fetch("/api/game/instrument/getall");
    const instruments = instrumentRes.ok ? await instrumentRes.json() : [];

    const neededPrograms = new Set();
    for (const seq of sequences) {
        if (seq.tracks) {
            for (const t of seq.tracks) {
                neededPrograms.add(t.instrument);
            }
        }
    }

    const keyCache = {};
    for (const prog of neededPrograms) {
        const ir = await fetch(`/api/game/instrument/get/${prog}`);
        if (ir.ok) {
            const data = await ir.json();
            keyCache[prog] = data.notes;
        }
    }

    function instrName(program) {
        const inst = instruments.find(x => x.program === program);
        return inst ? inst.instrumentName : `Instrumento #${program}`;
    }

    const container = document.getElementById("saved-sequences-container");

    sequences.forEach((seq, i) => {
        const dateStr = seq.createdAt
            ? new Date(seq.createdAt).toLocaleDateString("es-ES")
            : "";
        const trackCount = seq.tracks ? seq.tracks.length : 0;
        const instrHtml = seq.tracks ? seq.tracks.map(t =>
            `<span class="badge bg-secondary me-1">${instrName(t.instrument)}</span>`
        ).join("") : "";

        const card = document.createElement("div");
        card.className = "card mb-3 shadow-sm";
        card.id = `seq-card-${seq.id}`;
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
                        <button id="deleteSeq${i}" class="btn btn-outline-danger btn-sm ms-2">
                            <i class="bi bi-trash"></i>
                        </button>
                    </div>
                </div>
                <div id="miniRollContainer${i}" class="mb-2"></div>
                <div class="row align-items-center">
                    <div class="col">
                        <input id="seqProgress${i}" type="range" class="form-range">
                    </div>
                    <div class="col-auto d-flex gap-2">
                        <button id="seqPlay${i}" class="btn btn-primary btn-sm"><i class="bi bi-play-fill"></i></button>
                        <button id="seqPause${i}" class="btn btn-primary btn-sm"><i class="bi bi-pause-fill"></i></button>
                        <button id="seqStop${i}" class="btn btn-primary btn-sm"><i class="bi bi-stop-fill"></i></button>
                    </div>
                </div>
            </div>
        `;
        container.appendChild(card);

        const miniContainer = document.getElementById(`miniRollContainer${i}`);
        const instrPrograms = new Set();
        if (seq.tracks) for (const t of seq.tracks) instrPrograms.add(t.instrument);
        for (const prog of instrPrograms) {
            const keys = keyCache[prog];
            if (!keys) continue;
            const progTracks = seq.tracks.filter(t => t.instrument === prog);
            const label = document.createElement("div");
            label.className = "mini-roll-label";
            label.textContent = instrName(prog);
            miniContainer.appendChild(label);
            PianoRoll.createMiniReadOnly(miniContainer, keys, progTracks);
        }

        if (seq.tracks && seq.tracks.length > 0) {
            let pr = new PianoRoll({ audioContext: audioContext });
            pr.setFixedTracks(seq.tracks);
            pr.bindControls({
                playButton: `#seqPlay${i}`,
                pauseButton: `#seqPause${i}`,
                stopButton: `#seqStop${i}`,
                progressBar: `#seqProgress${i}`,
            });
        }

        document.querySelector(`#deleteSeq${i}`).addEventListener("click", () => {
            if (!confirm("¿Eliminar esta secuencia?")) return;
            fetch(`/api/sequence/saved/${seq.id}`, {
                method: "DELETE",
                headers: { "X-CSRF-TOKEN": config.csrf.value },
            }).then((r) => {
                if (r.ok) {
                    card.remove();
                    const remaining = container.querySelectorAll(".card").length;
                    if (remaining === 0) {
                        document.getElementById("empty-sequences").classList.remove("d-none");
                    }
                }
            });
        });
    });
}

getSavedSequences();
