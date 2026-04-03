document.addEventListener('DOMContentLoaded', () => {
    // DOM Elements
    const urlInput = document.getElementById('url-input');
    const analyzeBtn = document.getElementById('analyze-btn');
    const loadingIndicator = document.getElementById('loading-indicator');
    const errorMsg = document.getElementById('error-message');
    const dashboard = document.getElementById('dashboard');
    
    // Stats Elements
    const siteTitle = document.getElementById('site-title');
    const siteWords = document.getElementById('site-words');
    const siteLinks = document.getElementById('site-links');
    const siteInteractive = document.getElementById('site-interactive');
    
    // Chat Elements
    const chatInput = document.getElementById('chat-input');
    const sendBtn = document.getElementById('send-btn');
    const chatWindow = document.getElementById('chat-window');

    let currentSessionId = null;

    // API URL Base
    const API_BASE = '/api';

    // Enter key listeners
    urlInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') analyzeBtn.click();
    });

    chatInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') sendBtn.click();
    });

    // Handle Analysis
    analyzeBtn.addEventListener('click', async () => {
        const url = urlInput.value.trim();
        if (!url) {
            showError('Please enter a valid URL.');
            return;
        }
        
        // Reset state
        hideError();
        dashboard.classList.add('hidden');
        loadingIndicator.classList.remove('hidden');
        analyzeBtn.disabled = true;

        try {
            const response = await fetch(`${API_BASE}/analyze`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ url: url })
            });

            const data = await response.json();

            if (!response.ok) {
                throw new Error(data.error || 'Failed to analyze website.');
            }

            // Success
            currentSessionId = data.sessionId;
            
            // Populate stats
            const preview = data.previewData;
            siteTitle.textContent = preview.title ? preview.title.substring(0, 50) + (preview.title.length > 50 ? '...' : '') : 'No title found';
            
            // Calculate approximate words
            const words = preview.mainText ? preview.mainText.split(/\s+/).length : 0;
            siteWords.textContent = words.toLocaleString();
            
            siteLinks.textContent = (preview.links ? preview.links.length : 0).toLocaleString();
            
            const interactiveCount = (preview.buttons ? preview.buttons.length : 0) + (preview.formFields ? preview.formFields.length : 0);
            siteInteractive.textContent = interactiveCount.toLocaleString();

            // Hide loading, show dashboard
            loadingIndicator.classList.add('hidden');
            dashboard.classList.remove('hidden');
            
            // Add system msg
            appendMessage('system', `Analysis complete for ${url}. I've summarized the main content and extracted ${interactiveCount} interactive elements. What would you like to know?`);

        } catch (error) {
            loadingIndicator.classList.add('hidden');
            showError(error.message);
        } finally {
            analyzeBtn.disabled = false;
        }
    });

    // Handle Chat
    sendBtn.addEventListener('click', async () => {
        const question = chatInput.value.trim();
        if (!question || !currentSessionId) return;

        // Clear input, show user message
        chatInput.value = '';
        appendMessage('user', question);
        
        // Disable input while generating
        chatInput.disabled = true;
        sendBtn.disabled = true;

        // Add loading indicator
        const typingId = 'typing-' + Date.now();
        appendTypingIndicator(typingId);

        try {
            const response = await fetch(`${API_BASE}/chat`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ 
                    sessionId: currentSessionId,
                    question: question
                })
            });

            const data = await response.json();
            
            // Remove typing indicator
            removeElement(typingId);

            if (!response.ok) {
                throw new Error(data.error || 'Failed to get answer.');
            }

            appendMessage('system', data.answer);

        } catch (error) {
            removeElement(typingId);
            appendMessage('system', 'Sorry, I encountered an error: ' + error.message);
        } finally {
            chatInput.disabled = false;
            sendBtn.disabled = false;
            chatInput.focus();
        }
    });

    // Helpers
    function showError(msg) {
        errorMsg.textContent = msg;
        errorMsg.classList.remove('hidden');
    }

    function hideError() {
        errorMsg.classList.add('hidden');
    }

    function appendMessage(sender, text) {
        const msgDiv = document.createElement('div');
        msgDiv.className = `chat-message ${sender === 'user' ? 'user-msg' : 'system-msg'}`;
        
        const icon = document.createElement('i');
        icon.className = sender === 'user' ? 'fa-solid fa-user' : 'fa-solid fa-microchip';
        
        const contentDiv = document.createElement('div');
        contentDiv.className = 'msg-content';
        
        // Handle basic bolding/newlines markdown-like text from LLM response
        let formattedText = escapeHtml(text).replace(/\n/g, '<br>');
        // Simple bold parser
        formattedText = formattedText.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
        contentDiv.innerHTML = formattedText;

        msgDiv.appendChild(icon);
        msgDiv.appendChild(contentDiv);
        
        chatWindow.appendChild(msgDiv);
        scrollToBottom();
    }

    function appendTypingIndicator(id) {
        const msgDiv = document.createElement('div');
        msgDiv.id = id;
        msgDiv.className = 'chat-message system-msg typing-indicator';
        msgDiv.innerHTML = '<i class="fa-solid fa-microchip"></i><div class="msg-content">Analyzing response<span class="pulse-icon">...</span></div>';
        chatWindow.appendChild(msgDiv);
        scrollToBottom();
    }

    function removeElement(id) {
        const el = document.getElementById(id);
        if (el) el.remove();
    }

    function scrollToBottom() {
        chatWindow.scrollTop = chatWindow.scrollHeight;
    }

    function escapeHtml(unsafe) {
        return unsafe
             .replace(/&/g, "&amp;")
             .replace(/</g, "&lt;")
             .replace(/>/g, "&gt;")
             .replace(/"/g, "&quot;")
             .replace(/'/g, "&#039;");
    }
});
