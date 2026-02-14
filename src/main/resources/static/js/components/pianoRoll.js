let myContext = new AudioContext();
let bpm = 140;
let inst = 1;
let beats = 32;
const notes = [
    // Octave 3
    { abc: "C,", note: "C", octave: 3 },
    { abc: "^C,", note: "C#", octave: 3 },
    { abc: "D,", note: "D", octave: 3 },
    { abc: "^D,", note: "D#", octave: 3 },
    { abc: "E,", note: "E", octave: 3 },
    { abc: "F,", note: "F", octave: 3 },
    { abc: "^F,", note: "F#", octave: 3 },
    { abc: "G,", note: "G", octave: 3 },
    { abc: "^G,", note: "G#", octave: 3 },
    { abc: "A,", note: "A", octave: 3 },
    { abc: "^A,", note: "A#", octave: 3 },
    { abc: "B,", note: "B", octave: 3 },

    // Octave 4
    { abc: "C", note: "C", octave: 4 },
    { abc: "^C", note: "C#", octave: 4 },
    { abc: "D", note: "D", octave: 4 },
    { abc: "^D", note: "D#", octave: 4 },
    { abc: "E", note: "E", octave: 4 },
    { abc: "F", note: "F", octave: 4 },
    { abc: "^F", note: "F#", octave: 4 },
    { abc: "G", note: "G", octave: 4 },
    { abc: "^G", note: "G#", octave: 4 },
    { abc: "A", note: "A", octave: 4 },
    { abc: "^A", note: "A#", octave: 4 },
    { abc: "B", note: "B", octave: 4 },

    // Octave 5
    { abc: "c", note: "C", octave: 5 },
    { abc: "^c", note: "C#", octave: 5 },
    { abc: "d", note: "D", octave: 5 },
    { abc: "^d", note: "D#", octave: 5 },
    { abc: "e", note: "E", octave: 5 },
    { abc: "f", note: "F", octave: 5 },
    { abc: "^f", note: "F#", octave: 5 },
    { abc: "g", note: "G", octave: 5 },
    { abc: "^g", note: "G#", octave: 5 },
    { abc: "a", note: "A", octave: 5 },
    { abc: "^a", note: "A#", octave: 5 },
    { abc: "b", note: "B", octave: 5 },
];

let timeline = Array.from({ length: beats }, () => new Set());
console.log(timeline)

async function pianoRollBuilder(notes) {
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
            if (n.abc.charAt(0) === "^") cb.classList.add("black-key");
            else cb.classList.add("white-key");
            if (i % 8 == 7) cb.classList.add("section-end");
            cb.dataset.note = `${n.abc}`;
            cb.dataset.time = `${i}`;
            roll.append(cb);
        }
        pianoRoll.prepend(roll);
    });
}

async function addNoteListeners() {
    document.querySelectorAll(".note").forEach((key) => {
        key.addEventListener("click", (e) => {
            key.classList.toggle("active");
            if (key.classList.contains("active"))
                timeline[key.dataset.time].add(key.dataset.note);
            else timeline[key.dataset.time].delete(key.dataset.note);
            fillTextRoll();
        });
    });
}

async function fillTextRoll() {
    let str = "";
    for (let i = 0; i < 32; i++) {
        let chord = "".concat(...timeline[i]);

        if (chord !== "") str += ` [${chord}]`;
        else str += "z";
        if (i % 8 == 7) str += "|";
    }
    document.querySelector("#abcTextRoll").textContent =
        `X:1\nM:4/4\nL:1/8\n%%MIDI program ${inst}\n${str}\n`;
    document
        .querySelector("#abcTextRoll")
        .dispatchEvent(new Event("change", {}));
}

document.getElementById("tempo").addEventListener("change", (e) => {
    bpm = document.getElementById("tempo").value;
    fillTextRoll();
});

document.getElementById("inst").addEventListener("change", (e) => {
    inst = document.getElementById("inst").value;
    fillTextRoll();
});

document.addEventListener("DOMContentLoaded", (e) => {
    document.getElementById("tempo").value = bpm;
    document.querySelector("#inst").value;
    pianoRollBuilder(notes)
        .then(addNoteListeners())
        .then((document.querySelector("#pianoRoll").scrollTop = 300));
});

var options = {};
var editor = new ABCJS.Editor("abcTextRoll", {
    canvas_id: "paper",
    warnings_id: "warnings",
    abcjsParams: options,
    synth: {
        el: "#audio",
        options: {
            displayLoop: true,
            displayPlay: true,
            displayProgress: true,
            displayWarp: true,
        },
    },
});
