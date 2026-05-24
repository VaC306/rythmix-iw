/*
 * Ejemplos de uso de JS para interaccionar con servidores
 * y añadir interactividad a la página.
 *
 * Estos ejemplos se usan desde templates/user.html
 */

// envio de mensajes con AJAX
let b = document.getElementById("sendmsg");
b.onclick = (e) => {
    e.preventDefault();
    go(b.parentNode.action, 'POST', {
            message: document.getElementById("message").value
        })
        .then(d => console.log("happy", d))
        .catch(e => console.log("sad", e))
}

// cómo pintar 1 mensaje (devuelve html que se puede insertar en un div)
function renderMsg(msg) {
    console.log("rendering: ", msg);
    return `<div>${msg.from} @${msg.sent}: ${msg.text}</div>`;
}

// pinta mensajes viejos al cargarse, via AJAX
let messageDiv = document.getElementById("mensajes");
go(config.rootUrl + "/user/received", "GET").then(ms =>
    ms.forEach(m => messageDiv.insertAdjacentHTML("beforeend", renderMsg(m))));

// y aquí pinta mensajes según van llegando
if (ws.receive) {
    const oldFn = ws.receive; // guarda referencia a manejador anterior
    ws.receive = (m) => {
        oldFn(m); // llama al manejador anterior
        messageDiv.insertAdjacentHTML("beforeend", renderMsg(m));
    }
}

