/* ═══════════════════════════════════════════════════════════════════════
   MedBot — Vue.js 3 Application (Options API via CDN)
   Enterprise Medical AI Assistant with Translation & TTS
   ═══════════════════════════════════════════════════════════════════════ */

const { createApp, ref, computed, watch, nextTick, onMounted } = Vue;

const app = createApp({

  data() {
    return {
      // ─── Chat State ──────────────────────────────────────────────
      messages: [],
      userInput: '',
      isLoading: false,
      isStreaming: false,
      currentSessionId: null,
      sessions: [],

      // ─── Language State ──────────────────────────────────────────
      languages: [],
      selectedLanguage: 'en',

      // ─── UI State ────────────────────────────────────────────────
      theme: localStorage.getItem('medbot-theme') || 'light',
      sidebarOpen: false,
      showEmergency: false,

      // ─── Voice State ─────────────────────────────────────────────
      isRecording: false,
      recognition: null,
      currentSpeaking: null,   // messageIndex of currently speaking message
      ttsSpeed: 1.0,

      // ─── Translation State ────────────────────────────────────────
      translatedMessages: {},  // { messageIndex: { text, lang } }
    };
  },

  computed: {
    selectedLangObj() {
      return this.languages.find(l => l.code === this.selectedLanguage) || { name: 'English', bcp47Code: 'en-US' };
    },
    themeIcon() {
      return this.theme === 'dark' ? '☀️' : '🌙';
    }
  },

  watch: {
    theme(newTheme) {
      document.documentElement.setAttribute('data-theme', newTheme);
      localStorage.setItem('medbot-theme', newTheme);
    }
  },

  mounted() {
    // Apply saved theme
    document.documentElement.setAttribute('data-theme', this.theme);

    // Load languages
    this.loadLanguages();

    // Load sessions
    this.loadSessions();

    // Setup speech recognition
    this.setupSpeechRecognition();

    // Auto-resize textarea
    this.$refs.inputArea?.addEventListener('input', this.autoResize);
  },

  methods: {

    // ═══════════════════════════════════════════════════════════════
    // LANGUAGE MANAGEMENT
    // ═══════════════════════════════════════════════════════════════

    async loadLanguages() {
      try {
        const res = await fetch('/api/languages');
        if (res.ok) {
          this.languages = await res.json();
        }
      } catch (e) {
        console.error('Failed to load languages:', e);
        // Fallback minimal language list
        this.languages = [
          { code: 'en', name: 'English', nativeName: 'English', bcp47Code: 'en-US', ttsSupported: true },
          { code: 'hi', name: 'Hindi', nativeName: 'हिन्दी', bcp47Code: 'hi-IN', ttsSupported: true }
        ];
      }
    },

    // ═══════════════════════════════════════════════════════════════
    // CHAT — Main messaging logic
    // ═══════════════════════════════════════════════════════════════

    async sendMessage() {
      const text = this.userInput.trim();
      if (!text || this.isLoading) return;

      // Add user message to UI
      this.messages.push({ role: 'user', content: text, language: this.selectedLanguage });
      this.userInput = '';
      this.resetTextarea();
      this.isLoading = true;
      this.scrollToBottom();

      try {
        const res = await fetch('/api/chat', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            prompt: text,
            sessionId: this.currentSessionId,
            language: this.selectedLanguage,
            outputMode: 'text'
          })
        });

        if (!res.ok) {
          const err = await res.json().catch(() => ({ message: 'Unknown error' }));
          throw new Error(err.message || `HTTP ${res.status}`);
        }

        const data = await res.json();
        this.currentSessionId = data.sessionId;

        // Add bot message
        this.messages.push({
          role: 'bot',
          content: data.text,
          language: data.language || this.selectedLanguage,
          languageDisplay: data.languageDisplay || 'English',
          ttsAvailable: data.ttsAvailable !== false
        });

        this.loadSessions();

      } catch (e) {
        console.error('Chat error:', e);
        this.messages.push({
          role: 'bot',
          content: '❌ **Error:** ' + (e.message || 'Failed to get a response. Please try again.'),
          language: 'en',
          ttsAvailable: false
        });
      } finally {
        this.isLoading = false;
        this.scrollToBottom();
      }
    },

    // ═══════════════════════════════════════════════════════════════
    // SSE STREAMING — Real-time typewriter response
    // ═══════════════════════════════════════════════════════════════

    async sendMessageStreaming() {
      const text = this.userInput.trim();
      if (!text || this.isLoading) return;

      this.messages.push({ role: 'user', content: text, language: this.selectedLanguage });
      this.userInput = '';
      this.resetTextarea();
      this.isLoading = true;
      this.isStreaming = true;

      // Add a placeholder bot message that we'll fill progressively
      const botMsgIndex = this.messages.length;
      this.messages.push({
        role: 'bot',
        content: '',
        language: this.selectedLanguage,
        ttsAvailable: true,
        streaming: true
      });
      this.scrollToBottom();

      try {
        const params = new URLSearchParams({
          prompt: text,
          language: this.selectedLanguage,
          sessionId: this.currentSessionId || ''
        });

        const eventSource = new EventSource(`/api/chat/stream?${params}`);

        eventSource.addEventListener('session', (e) => {
          const data = JSON.parse(e.data);
          this.currentSessionId = data.sessionId;
        });

        eventSource.addEventListener('chunk', (e) => {
          const data = JSON.parse(e.data);
          this.messages[botMsgIndex].content = data.accumulated;
          this.scrollToBottom();
        });

        eventSource.addEventListener('done', (e) => {
          const data = JSON.parse(e.data);
          this.messages[botMsgIndex].streaming = false;
          this.messages[botMsgIndex].language = data.language;
          this.messages[botMsgIndex].languageDisplay = data.languageDisplay;
          this.messages[botMsgIndex].ttsAvailable = data.ttsAvailable;
          this.currentSessionId = data.sessionId;
          this.isLoading = false;
          this.isStreaming = false;
          this.loadSessions();
          eventSource.close();
        });

        eventSource.addEventListener('error', (e) => {
          if (e.data) {
            const data = JSON.parse(e.data);
            this.messages[botMsgIndex].content = '❌ ' + data.message;
          }
          this.messages[botMsgIndex].streaming = false;
          this.isLoading = false;
          this.isStreaming = false;
          eventSource.close();
        });

        eventSource.onerror = () => {
          this.messages[botMsgIndex].streaming = false;
          this.isLoading = false;
          this.isStreaming = false;
          eventSource.close();
        };

      } catch (e) {
        console.error('Streaming error:', e);
        this.messages[botMsgIndex].content = '❌ Streaming failed. Please try again.';
        this.messages[botMsgIndex].streaming = false;
        this.isLoading = false;
        this.isStreaming = false;
      }
    },

    // ═══════════════════════════════════════════════════════════════
    // SESSION MANAGEMENT
    // ═══════════════════════════════════════════════════════════════

    async loadSessions() {
      try {
        const res = await fetch('/api/chat/sessions');
        if (res.ok) this.sessions = await res.json();
      } catch (e) { console.error('Failed to load sessions:', e); }
    },

    async loadSession(sessionId) {
      try {
        this.currentSessionId = sessionId;
        const res = await fetch(`/api/chat/sessions/${sessionId}`);
        if (res.ok) {
          const history = await res.json();
          this.messages = history.map(m => ({
            role: m.role === 'user' ? 'user' : 'bot',
            content: m.content,
            language: m.language || 'en',
            ttsAvailable: true
          }));
        }
        this.sidebarOpen = false;
        this.scrollToBottom();
      } catch (e) { console.error('Failed to load session:', e); }
    },

    newChat() {
      this.messages = [];
      this.currentSessionId = null;
      this.translatedMessages = {};
      this.sidebarOpen = false;
    },

    async deleteSession(sessionId, evt) {
      evt.stopPropagation();
      try {
        await fetch(`/api/chat/sessions/${sessionId}`, { method: 'DELETE' });
        this.sessions = this.sessions.filter(s => s.id !== sessionId);
        if (this.currentSessionId === sessionId) this.newChat();
      } catch (e) { console.error('Failed to delete session:', e); }
    },

    // ═══════════════════════════════════════════════════════════════
    // TRANSLATION — Translate any bot message
    // ═══════════════════════════════════════════════════════════════

    async translateMessage(index) {
      const msg = this.messages[index];
      if (!msg || msg.role !== 'bot') return;

      // If already translated, toggle off
      if (this.translatedMessages[index]) {
        delete this.translatedMessages[index];
        this.translatedMessages = { ...this.translatedMessages }; // trigger reactivity
        return;
      }

      try {
        const res = await fetch('/api/translate', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            text: msg.content,
            targetLanguage: this.selectedLanguage,
            sourceLanguage: msg.language || 'en'
          })
        });

        if (res.ok) {
          const data = await res.json();
          this.translatedMessages = {
            ...this.translatedMessages,
            [index]: {
              text: data.translatedText,
              lang: this.selectedLangObj.name
            }
          };
        }
      } catch (e) { console.error('Translation failed:', e); }
    },

    // ═══════════════════════════════════════════════════════════════
    // TEXT-TO-SPEECH — "Listen to this" via Web Speech API
    // ═══════════════════════════════════════════════════════════════

    speakMessage(index) {
      const msg = this.messages[index];
      if (!msg) return;

      // If already speaking this message, stop
      if (this.currentSpeaking === index) {
        window.speechSynthesis.cancel();
        this.currentSpeaking = null;
        return;
      }

      // Cancel any current speech
      window.speechSynthesis.cancel();

      // Determine text and language
      let textToSpeak = msg.content;
      let langCode = msg.language || 'en';

      // If there's a translation, speak the translation instead
      if (this.translatedMessages[index]) {
        textToSpeak = this.translatedMessages[index].text;
        langCode = this.selectedLanguage;
      }

      // Strip markdown for cleaner speech
      textToSpeak = this.stripMarkdown(textToSpeak);

      // Find the BCP-47 code for this language
      const langObj = this.languages.find(l => l.code === langCode);
      const bcp47 = langObj ? langObj.bcp47Code : 'en-US';

      const utterance = new SpeechSynthesisUtterance(textToSpeak);
      utterance.lang = bcp47;
      utterance.rate = this.ttsSpeed;
      utterance.pitch = 1.0;
      utterance.volume = 1.0;

      // Try to find a voice that matches the language
      const voices = speechSynthesis.getVoices();
      const matchingVoice = voices.find(v => v.lang.startsWith(langCode)) ||
                            voices.find(v => v.lang.startsWith(bcp47.split('-')[0]));
      if (matchingVoice) utterance.voice = matchingVoice;

      utterance.onend = () => { this.currentSpeaking = null; };
      utterance.onerror = () => { this.currentSpeaking = null; };

      this.currentSpeaking = index;
      window.speechSynthesis.speak(utterance);
    },

    stopSpeaking() {
      window.speechSynthesis.cancel();
      this.currentSpeaking = null;
    },

    setTtsSpeed(speed) {
      this.ttsSpeed = speed;
      // If currently speaking, restart with new speed
      if (this.currentSpeaking !== null) {
        const idx = this.currentSpeaking;
        this.stopSpeaking();
        this.$nextTick(() => this.speakMessage(idx));
      }
    },

    // ═══════════════════════════════════════════════════════════════
    // VOICE INPUT — Speech Recognition
    // ═══════════════════════════════════════════════════════════════

    setupSpeechRecognition() {
      const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
      if (!SR) return;

      this.recognition = new SR();
      this.recognition.continuous = false;
      this.recognition.interimResults = true;

      this.recognition.onstart = () => { this.isRecording = true; };
      this.recognition.onend = () => {
        this.isRecording = false;
        if (this.userInput.trim()) {
          this.sendMessage();
        }
      };
      this.recognition.onerror = (e) => {
        console.error('Speech recognition error:', e.error);
        this.isRecording = false;
      };
      this.recognition.onresult = (e) => {
        let transcript = '';
        for (let i = 0; i < e.results.length; i++) {
          transcript += e.results[i][0].transcript;
        }
        this.userInput = transcript;
      };
    },

    toggleRecording() {
      if (!this.recognition) {
        alert('Speech recognition is not supported in your browser. Please use Chrome.');
        return;
      }

      if (this.isRecording) {
        this.recognition.stop();
      } else {
        // Set recognition language based on selected language
        this.recognition.lang = this.selectedLangObj.bcp47Code || 'en-US';
        this.recognition.start();
      }
    },

    // ═══════════════════════════════════════════════════════════════
    // CLIPBOARD — Copy message to clipboard
    // ═══════════════════════════════════════════════════════════════

    async copyMessage(index) {
      const msg = this.messages[index];
      if (!msg) return;

      try {
        await navigator.clipboard.writeText(msg.content);
        // Brief visual feedback (button text momentarily changes)
        const btn = document.querySelector(`[data-copy="${index}"]`);
        if (btn) {
          const original = btn.textContent;
          btn.textContent = '✅ Copied!';
          setTimeout(() => { btn.textContent = original; }, 1500);
        }
      } catch (e) {
        console.error('Copy failed:', e);
      }
    },

    // ═══════════════════════════════════════════════════════════════
    // QUICK ACTIONS — Pre-filled prompts
    // ═══════════════════════════════════════════════════════════════

    quickAction(prompt) {
      this.userInput = prompt;
      this.sendMessage();
    },

    // ═══════════════════════════════════════════════════════════════
    // UTILITIES
    // ═══════════════════════════════════════════════════════════════

    renderMarkdown(text) {
      if (!text) return '';
      try {
        return marked.parse(text);
      } catch (e) {
        return text.replace(/\n/g, '<br>');
      }
    },

    stripMarkdown(text) {
      if (!text) return '';
      return text
        .replace(/```[\s\S]*?```/g, '')
        .replace(/`([^`]+)`/g, '$1')
        .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
        .replace(/#{1,6}\s*/g, '')
        .replace(/\*\*(.+?)\*\*/g, '$1')
        .replace(/\*(.+?)\*/g, '$1')
        .replace(/<[^>]+>/g, '')
        .replace(/[-*+]\s+/g, '')
        .replace(/\n{2,}/g, '\n')
        .trim();
    },

    scrollToBottom() {
      this.$nextTick(() => {
        const el = this.$refs.chatArea;
        if (el) el.scrollTop = el.scrollHeight;
      });
    },

    toggleTheme() {
      this.theme = this.theme === 'dark' ? 'light' : 'dark';
    },

    autoResize(e) {
      const el = e.target;
      el.style.height = 'auto';
      el.style.height = Math.min(el.scrollHeight, 120) + 'px';
    },

    resetTextarea() {
      this.$nextTick(() => {
        const el = this.$refs.inputArea;
        if (el) el.style.height = 'auto';
      });
    },

    handleKeyDown(e) {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        this.sendMessage();
      }
    },

    escapeHtml(text) {
      const div = document.createElement('div');
      div.textContent = text;
      return div.innerHTML;
    }
  }
});

app.mount('#app');

// Preload voices
window.speechSynthesis?.onvoiceschanged = () => { speechSynthesis.getVoices(); };
