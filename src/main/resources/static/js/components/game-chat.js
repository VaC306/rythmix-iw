// game-chat.js - AJAX version

// Store chat messages locally
let chatMessages = [];
let lastChatUpdate = 0;

// Function to send chat message via AJAX
async function sendChatAJAX() {
    const input = document.getElementById('chat-input');
    if (!input.value.trim()) return;
    
    const message = {
        username: username,
        text: input.value.trim()
    };
    
    try {
        const response = await fetch(`/continue/api/lobby/${lobbyCode}/chat`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(message)
        });
        
        if (!response.ok) {
            console.error("Failed to send chat message");
        } else {
            console.log("Chat message sent");
        }
    } catch (error) {
        console.error("Error sending chat:", error);
    }
    
    input.value = '';
}

// Function to poll for new chat messages
async function pollChatMessages() {
    try {
        const response = await fetch(`/continue/api/lobby/${lobbyCode}/chat/updates?since=${lastChatUpdate}`);
        if (response.ok) {
            const newMessages = await response.json();
            for (const msg of newMessages) {
                displayChatMessage(msg);
                lastChatUpdate = Math.max(lastChatUpdate, msg.timestamp || Date.now());
            }
        }
    } catch (error) {
        console.error("Error polling chat:", error);
    }
}

// Function to display a chat message in the UI
function displayChatMessage(message) {
    const box = document.getElementById('chat-messages');
    if (!box) return;
    
    box.insertAdjacentHTML('beforeend',
        `<div class="d-flex gap-2 mb-1">
            <span class="fw-bold text-primary">${escapeHtml(message.username)}</span>
            <span>${escapeHtml(message.text)}</span>
        </div>`
    );
    box.scrollTop = box.scrollHeight;
    
    // Only show badge if chat is closed and message is from someone else
    const offcanvas = document.querySelector("#chat-offcanvas");
    const badge = document.querySelector("#chat-button-badge");
    if (badge && offcanvas && !offcanvas.classList.contains('show')) {
        const currentCount = parseInt(badge.textContent) || 0;
        badge.textContent = currentCount + 1;
        badge.classList.remove("visually-hidden");
    }
}

// Helper function to escape HTML
function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/[&<>]/g, function(m) {
        if (m === '&') return '&amp;';
        if (m === '<') return '&lt;';
        if (m === '>') return '&gt;';
        return m;
    });
}

// Start polling for chat messages
function startChatPolling() {
    if (!lobbyCode) return;
    
    // Poll every 2 seconds for chat messages
    setInterval(() => {
        pollChatMessages();
    }, 2000);
}

// Initialize chat when DOM is ready
document.addEventListener("DOMContentLoaded", () => {
    // Wait a bit for other scripts to initialize
    setTimeout(() => {
        if (!lobbyCode) {
            console.warn("Chat: No lobby code found, will retry");
            return;
        }
        
        console.log("Initializing chat for lobby:", lobbyCode);
        
        // Setup event listeners
        const sendButton = document.getElementById('chat-send');
        const chatInput = document.getElementById('chat-input');
        const offcanvas = document.querySelector("#chat-offcanvas");
        
        if (sendButton) {
            sendButton.addEventListener('click', sendChatAJAX);
        }
        
        if (chatInput) {
            chatInput.addEventListener('keydown', e => {
                if (e.key === 'Enter') sendChatAJAX();
            });
        }
        
        if (offcanvas) {
            offcanvas.addEventListener('hide.bs.offcanvas', () => {
                const badge = document.querySelector("#chat-button-badge");
                if (badge) {
                    badge.classList.add("visually-hidden");
                    badge.textContent = "0";
                }
            });
        }
        
        // Start polling for chat messages
        startChatPolling();
    }, 500);
});

// Also listen for chat messages from the main game polling
// This function will be called by game-client.js when it receives CHAT_MESSAGE updates
window.displayChatMessage = function(message) {
    displayChatMessage(message);
};