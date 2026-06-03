function waitForStomp(cb) {
    const t = setInterval(() => {
        if (ws.stompClient?.connected) { clearInterval(t); cb(); }
    }, 300);
}

const chatLobbyCode = window.lobbyCode || document.documentElement.dataset.lobbyCode || "";

if (!chatLobbyCode) {
    console.warn("Chat disabled: lobbyCode is not available");
}

waitForStomp(() => {
    if (!chatLobbyCode) return;
    ws.stompClient.subscribe(`/topic/lobby-${chatLobbyCode}`, (msg) => {
        const { from, text, sent, id } = JSON.parse(msg.body);
        const box = document.getElementById('chat-messages');
        if (!box) return;
        box.insertAdjacentHTML('beforeend',
            `<div class="d-flex gap-2 mb-1">
                <span class="fw-bold text-primary">${from}</span>
                <span>${text}</span>
            </div>`
        );
        box.scrollTop = box.scrollHeight;
        const badge = document.querySelector("#chat-button-badge")
        if (badge) {
            badge.textContent = parseInt(badge.textContent) + 1
            badge.classList.remove("visually-hidden")
        }
    });

    document.getElementById('chat-send')?.addEventListener('click', sendChat);
    document.getElementById('chat-input')?.addEventListener('keydown', e => e.key === 'Enter' && sendChat());
    document.querySelector("#chat-offcanvas")?.addEventListener('hide.bs.offcanvas', ()=>{
        document.querySelector("#chat-button-badge")?.classList.add("visually-hidden");
        if (document.querySelector("#chat-button-badge")) {
            document.querySelector("#chat-button-badge").textContent = 0;
        }
    })
});

function sendChat() {
    const input = document.getElementById('chat-input');
    if (!input.value.trim()) return;
    fetch(`/api/topic/lobby-${chatLobbyCode}`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-CSRF-TOKEN': window.config?.csrf?.value || ''
        },
        body: JSON.stringify({ message: input.value.trim() })
    });
    input.value = '';
}
