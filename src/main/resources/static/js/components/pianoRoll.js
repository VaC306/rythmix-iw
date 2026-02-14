let audioCtx = new AudioContext();
let seq,
    trackNumber,
    synth,
    loop = false;
let bpm = 140;
let inst = 1;
let beats = 32;
const notes = [
    // Octave 3
    { pitch: 48, note: "C", octave: 3, isBlack: false },
    { pitch: 49, note: "C#", octave: 3, isBlack: true },
    { pitch: 50, note: "D", octave: 3, isBlack: false },
    { pitch: 51, note: "D#", octave: 3, isBlack: true },
    { pitch: 52, note: "E", octave: 3, isBlack: false },
    { pitch: 53, note: "F", octave: 3, isBlack: false },
    { pitch: 54, note: "F#", octave: 3, isBlack: true },
    { pitch: 55, note: "G", octave: 3, isBlack: false },
    { pitch: 56, note: "G#", octave: 3, isBlack: true },
    { pitch: 57, note: "A", octave: 3, isBlack: false },
    { pitch: 58, note: "A#", octave: 3, isBlack: true },
    { pitch: 59, note: "B", octave: 3, isBlack: false },

    // Octave 4
    { pitch: 60, note: "C", octave: 4, isBlack: false },
    { pitch: 61, note: "C#", octave: 4, isBlack: true },
    { pitch: 62, note: "D", octave: 4, isBlack: false },
    { pitch: 63, note: "D#", octave: 4, isBlack: true },
    { pitch: 64, note: "E", octave: 4, isBlack: false },
    { pitch: 65, note: "F", octave: 4, isBlack: false },
    { pitch: 66, note: "F#", octave: 4, isBlack: true },
    { pitch: 67, note: "G", octave: 4, isBlack: false },
    { pitch: 68, note: "G#", octave: 4, isBlack: true },
    { pitch: 69, note: "A", octave: 4, isBlack: false },
    { pitch: 70, note: "A#", octave: 4, isBlack: true },
    { pitch: 71, note: "B", octave: 4, isBlack: false },

    // Octave 5
    { pitch: 72, note: "C", octave: 5, isBlack: false },
    { pitch: 73, note: "C#", octave: 5, isBlack: true },
    { pitch: 74, note: "D", octave: 5, isBlack: false },
    { pitch: 75, note: "D#", octave: 5, isBlack: true },
    { pitch: 76, note: "E", octave: 5, isBlack: false },
    { pitch: 77, note: "F", octave: 5, isBlack: false },
    { pitch: 78, note: "F#", octave: 5, isBlack: true },
    { pitch: 79, note: "G", octave: 5, isBlack: false },
    { pitch: 80, note: "G#", octave: 5, isBlack: true },
    { pitch: 81, note: "A", octave: 5, isBlack: false },
    { pitch: 82, note: "A#", octave: 5, isBlack: true },
    { pitch: 83, note: "B", octave: 5, isBlack: false },
];

let timeline = Array.from({ length: beats }, () => new Set());
console.log(timeline);

async function buildPianoRoll(notes) {
    notes.forEach((n) => {
        roll = document.createElement("div");
        roll.setAttribute("id", `roll-${n}`);
        roll.classList.add("note-roll");
        for (let i = 0; i < beats; i++) {
            cb = document.createElement("div");
            if (i == 0 && n.note === "C")
                cb.insertAdjacentHTML(
                    "beforeend",
                    `<span class="note-text">${n.note + n.octave}</span>`,
                );
            cb.setAttribute("id", `${n.abc}${i}`);
            cb.classList.add("note");
            if (n.isBlack) cb.classList.add("black-key");
            else cb.classList.add("white-key");
            if (i % 8 == 7) cb.classList.add("section-end");
            Object.assign(cb.dataset, n);
            cb.dataset.time = `${i}`;
            roll.append(cb);
        }
        pianoRoll.prepend(roll);
    });
}

async function addNoteListeners() {
    document.querySelectorAll(".note").forEach((key) => {
        key.addEventListener("click", (e) => {
            sequenceChanged = true;
            key.classList.toggle("active");
            if (key.classList.contains("active")) {
                timeline[key.dataset.time].add(key.dataset.pitch);
                addNote(key.dataset.pitch, key.dataset.time);
            } else {
                timeline[key.dataset.time].delete(key.dataset.pitch);
                removeNote(key.dataset.pitch, key.dataset.time);
            }
        });
    });
}

async function createSequence() {
    seq = new ABCJS.synth.SynthSequence();
    trackNumber = seq.addTrack();
    seq.setInstrument(trackNumber, inst);
    seq.totalDuration = 4;
}

async function createSynth() {
    synth = new ABCJS.synth.CreateSynth();
}

async function addNote(pitch, time) {
    var note = {
        cmd: "note",
        duration: 4 / beats,
        gap: 0,
        instrument: seq.currentInstrument[trackNumber],
        pitch: parseInt(pitch),
        start: (4 * parseInt(time)) / beats,
        volume: 80,
    };
    seq.tracks[trackNumber].push(note);
}

async function removeNote(pitch, time) {
    const idx = seq.tracks[trackNumber].findIndex(
        (note) =>
            note.pitch === parseInt(pitch) &&
            note.start === (4 * parseInt(time)) / beats,
    );
    if (idx !== -1) seq.tracks[0].splice(idx, 1);
}

document.querySelector("#tempo").addEventListener("change", (e) => {
    bpm = document.querySelector("#tempo").value;
});

document.querySelector("#inst").addEventListener("change", (e) => {
    inst = document.querySelector("#inst").value;
    seq.tracks[trackNumber].forEach((el) => {
        el.instrument = inst;
    });
});

document.querySelector("#play").addEventListener("click", async (e) => {
    await synth.init({
        sequence: seq,
        audioContext: audioCtx,
        millisecondsPerMeasure: (4 * 60000) / bpm,
    });
    await synth.prime();
    await synth.start();
});

function playLoop() {
  if (!loop) return;

  synth.prime().then(() => {
    synth.start();

    // Schedule the next loop after the total duration
    setTimeout(() => {
      playLoop();
    }, synth.duration * 1000); // duration in ms
  });
}

document.querySelector("#loop").addEventListener("click", async ()=>{
    loop = true
    await synth.init({
        sequence: seq,
        audioContext: audioCtx,
        millisecondsPerMeasure: (4 * 60000) / bpm,
    });
    playLoop()
})

document.querySelector("#stop").addEventListener("click", () => {
    loop = false;
    synth.stop();
});

document.addEventListener("DOMContentLoaded", (e) => {
    document.getElementById("tempo").value = bpm;
    buildPianoRoll(notes)
        .then(() => createSequence())
        .then(() => addNoteListeners())
        .then(() => createSynth())
        .then(() => (document.querySelector("#pianoRoll").scrollTop = 300));
});
