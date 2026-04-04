<template>
  <div class="chat-app">
    <!-- Header -->
    <header class="chat-header">
      <div class="header-left">
        <div class="avatar-wrapper">
          <span class="avatar-icon">🤖</span>
          <span class="status-dot"></span>
        </div>
        <div class="header-info">
          <h1 class="app-title">AI 编程小助手</h1>
          <p class="app-subtitle">编程学习 · 面试辅导 · 技术答疑</p>
        </div>
      </div>
      <div class="header-right">
        <div class="session-badge">
          <span class="session-label">会话 ID</span>
          <span class="session-id">{{ memoryId }}</span>
        </div>
        <button class="new-chat-btn" @click="newChat" title="新建会话">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 5v14M5 12h14"/>
          </svg>
          新建会话
        </button>
      </div>
    </header>

    <!-- Messages Area -->
    <main class="chat-messages" ref="messagesContainer">
      <!-- Welcome message -->
      <div v-if="messages.length === 0" class="welcome-screen">
        <div class="welcome-icon">🤖</div>
        <h2 class="welcome-title">你好！我是 AI 编程小助手</h2>
        <p class="welcome-desc">我可以帮助你解答编程学习和求职面试相关的问题，随时向我提问吧！</p>
        <div class="quick-prompts">
          <button
            v-for="prompt in quickPrompts"
            :key="prompt"
            class="quick-btn"
            @click="sendQuickPrompt(prompt)"
          >{{ prompt }}</button>
        </div>
      </div>

      <!-- Message list -->
      <template v-else>
        <div
          v-for="(msg, index) in messages"
          :key="index"
          class="message-row"
          :class="msg.role === 'user' ? 'message-row--user' : 'message-row--ai'"
        >
          <!-- AI Avatar -->
          <div v-if="msg.role === 'ai'" class="msg-avatar msg-avatar--ai">
            <span>🤖</span>
          </div>

          <div class="msg-bubble-wrap">
            <div
              class="msg-bubble"
              :class="msg.role === 'user' ? 'msg-bubble--user' : 'msg-bubble--ai'"
            >
              <div class="msg-content" v-html="renderContent(msg.content)"></div>
              <!-- Typing cursor for streaming -->
              <span v-if="msg.streaming" class="typing-cursor"></span>
            </div>
            <span class="msg-time">{{ msg.time }}</span>
          </div>

          <!-- User Avatar -->
          <div v-if="msg.role === 'user'" class="msg-avatar msg-avatar--user">
            <span>👤</span>
          </div>
        </div>

        <!-- Typing indicator (before stream starts) -->
        <div v-if="isWaiting" class="message-row message-row--ai">
          <div class="msg-avatar msg-avatar--ai"><span>🤖</span></div>
          <div class="msg-bubble-wrap">
            <div class="msg-bubble msg-bubble--ai">
              <div class="typing-indicator">
                <span></span><span></span><span></span>
              </div>
            </div>
          </div>
        </div>
      </template>
    </main>

    <!-- Input Area -->
    <footer class="chat-input-area">
      <div class="input-wrapper">
        <textarea
          ref="inputRef"
          v-model="inputText"
          class="chat-input"
          placeholder="输入你的问题，按 Enter 发送，Shift+Enter 换行..."
          :disabled="isStreaming"
          @keydown="handleKeydown"
          rows="1"
          @input="autoResize"
        ></textarea>
        <button
          class="send-btn"
          :class="{ 'send-btn--active': inputText.trim() && !isStreaming }"
          :disabled="!inputText.trim() || isStreaming"
          @click="sendMessage"
        >
          <svg v-if="!isStreaming" viewBox="0 0 24 24" fill="currentColor">
            <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/>
          </svg>
          <svg v-else viewBox="0 0 24 24" fill="currentColor" class="stop-icon">
            <rect x="6" y="6" width="12" height="12" rx="2"/>
          </svg>
        </button>
      </div>
      <p class="input-hint">AI 生成内容仅供参考，请注意甄别</p>
    </footer>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, onBeforeUnmount } from 'vue'

// --- State ---
const memoryId = ref(generateMemoryId())
const messages = ref([])
const inputText = ref('')
const isStreaming = ref(false)
const isWaiting = ref(false)
const messagesContainer = ref(null)
const inputRef = ref(null)

let currentEventSource = null

const quickPrompts = [
  '如何学习 Vue3？',
  '解释一下 Promise 和 async/await',
  '常见的前端面试题有哪些？',
  'Java 线程池的工作原理？',
]

// --- Helpers ---
function generateMemoryId() {
  return Math.floor(100000 + Math.random() * 900000)
}

function getNowTime() {
  const now = new Date()
  return `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}`
}

function renderContent(text) {
  if (!text) return ''
  // Escape HTML first
  let html = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')

  // Code blocks: ```lang\ncode\n```
  html = html.replace(/```(\w*)\n?([\s\S]*?)```/g, (_, lang, code) => {
    return `<pre class="code-block"><code class="lang-${lang}">${code.trim()}</code></pre>`
  })

  // Inline code
  html = html.replace(/`([^`]+)`/g, '<code class="inline-code">$1</code>')

  // Bold
  html = html.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')

  // Italic
  html = html.replace(/\*(.*?)\*/g, '<em>$1</em>')

  // Newlines
  html = html.replace(/\n/g, '<br/>')

  return html
}

async function scrollToBottom(smooth = true) {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTo({
      top: messagesContainer.value.scrollHeight,
      behavior: smooth ? 'smooth' : 'instant',
    })
  }
}

function autoResize(e) {
  const el = e.target
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 160) + 'px'
}

function resetInputHeight() {
  if (inputRef.value) {
    inputRef.value.style.height = 'auto'
  }
}

// --- Actions ---
function newChat() {
  if (isStreaming.value) {
    stopStream()
  }
  memoryId.value = generateMemoryId()
  messages.value = []
  inputText.value = ''
  resetInputHeight()
}

function sendQuickPrompt(prompt) {
  inputText.value = prompt
  sendMessage()
}

function handleKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    if (inputText.value.trim() && !isStreaming.value) {
      sendMessage()
    }
  }
}

function stopStream() {
  if (currentEventSource) {
    currentEventSource.close()
    currentEventSource = null
  }
  // Mark last AI message as done
  const lastMsg = messages.value[messages.value.length - 1]
  if (lastMsg && lastMsg.role === 'ai') {
    lastMsg.streaming = false
  }
  isStreaming.value = false
  isWaiting.value = false
}

function sendMessage() {
  const text = inputText.value.trim()
  if (!text || isStreaming.value) return

  // Add user message
  messages.value.push({
    role: 'user',
    content: text,
    time: getNowTime(),
    streaming: false,
  })

  inputText.value = ''
  resetInputHeight()
  scrollToBottom()

  isWaiting.value = true
  isStreaming.value = true

  const url = `/api/ai/chat?memoryId=${encodeURIComponent(memoryId.value)}&message=${encodeURIComponent(text)}`

  const eventSource = new EventSource(url)
  currentEventSource = eventSource

  let aiMsgIndex = -1

  eventSource.onmessage = (event) => {
    if (isWaiting.value) {
      // First chunk arrives — add AI message bubble
      isWaiting.value = false
      messages.value.push({
        role: 'ai',
        content: '',
        time: getNowTime(),
        streaming: true,
      })
      aiMsgIndex = messages.value.length - 1
    }

    if (aiMsgIndex >= 0) {
      messages.value[aiMsgIndex].content += event.data
      scrollToBottom(false)
    }
  }

  eventSource.onerror = () => {
    eventSource.close()
    currentEventSource = null
    isWaiting.value = false
    isStreaming.value = false

    if (aiMsgIndex >= 0 && messages.value[aiMsgIndex]) {
      messages.value[aiMsgIndex].streaming = false
      // If no content received, show error message
      if (!messages.value[aiMsgIndex].content) {
        messages.value[aiMsgIndex].content = '请求失败，请检查后端服务是否正常运行。'
      }
    } else if (isWaiting.value) {
      isWaiting.value = false
      messages.value.push({
        role: 'ai',
        content: '请求失败，请检查后端服务是否正常运行。',
        time: getNowTime(),
        streaming: false,
      })
    }
  }

  // SSE stream ends (backend closes connection)
  eventSource.addEventListener('close', () => {
    eventSource.close()
    currentEventSource = null
    if (aiMsgIndex >= 0 && messages.value[aiMsgIndex]) {
      messages.value[aiMsgIndex].streaming = false
    }
    isStreaming.value = false
    isWaiting.value = false
  })
}

onMounted(() => {
  inputRef.value?.focus()
})

onBeforeUnmount(() => {
  stopStream()
})
</script>

<style scoped>
/* ===== Layout ===== */
.chat-app {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--bg-primary);
  overflow: hidden;
}

/* ===== Header ===== */
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
  backdrop-filter: blur(12px);
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.avatar-wrapper {
  position: relative;
  width: 44px;
  height: 44px;
  background: var(--accent-light);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid var(--accent);
}

.avatar-icon {
  font-size: 22px;
  line-height: 1;
}

.status-dot {
  position: absolute;
  bottom: 1px;
  right: 1px;
  width: 10px;
  height: 10px;
  background: #22c55e;
  border-radius: 50%;
  border: 2px solid var(--bg-secondary);
}

.app-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: 0.3px;
}

.app-subtitle {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 2px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.session-badge {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}

.session-label {
  font-size: 10px;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.session-id {
  font-size: 13px;
  font-weight: 600;
  color: var(--accent-hover);
  font-variant-numeric: tabular-nums;
  letter-spacing: 1px;
}

.new-chat-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: var(--accent-light);
  color: var(--accent-hover);
  border: 1px solid rgba(99, 102, 241, 0.3);
  border-radius: var(--radius-sm);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.new-chat-btn svg {
  width: 14px;
  height: 14px;
}

.new-chat-btn:hover {
  background: rgba(99, 102, 241, 0.25);
  border-color: var(--accent);
  color: white;
}

/* ===== Messages ===== */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  scroll-behavior: smooth;
}

/* ===== Welcome ===== */
.welcome-screen {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  padding: 40px 20px;
  text-align: center;
  gap: 12px;
  animation: fadeIn 0.5s ease;
}

.welcome-icon {
  font-size: 64px;
  margin-bottom: 8px;
  filter: drop-shadow(0 0 20px rgba(99, 102, 241, 0.5));
}

.welcome-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary);
}

.welcome-desc {
  font-size: 14px;
  color: var(--text-secondary);
  max-width: 360px;
  line-height: 1.6;
}

.quick-prompts {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
  margin-top: 16px;
}

.quick-btn {
  padding: 8px 16px;
  background: var(--bg-card);
  color: var(--text-secondary);
  border: 1px solid var(--border);
  border-radius: 20px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.quick-btn:hover {
  background: var(--accent-light);
  border-color: var(--accent);
  color: var(--accent-hover);
}

/* ===== Message Row ===== */
.message-row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  animation: slideIn 0.3s ease;
  max-width: 100%;
}

.message-row--user {
  flex-direction: row-reverse;
}

.message-row--ai {
  flex-direction: row;
}

/* ===== Avatar ===== */
.msg-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
  margin-bottom: 18px;
}

.msg-avatar--ai {
  background: var(--accent-light);
  border: 1px solid rgba(99, 102, 241, 0.3);
}

.msg-avatar--user {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid var(--border);
}

/* ===== Bubble ===== */
.msg-bubble-wrap {
  display: flex;
  flex-direction: column;
  max-width: min(72%, 680px);
}

.message-row--user .msg-bubble-wrap {
  align-items: flex-end;
}

.message-row--ai .msg-bubble-wrap {
  align-items: flex-start;
}

.msg-bubble {
  padding: 12px 16px;
  border-radius: var(--radius-bubble);
  line-height: 1.65;
  font-size: 14px;
  word-break: break-word;
  position: relative;
}

.msg-bubble--user {
  background: var(--user-bubble);
  color: #fff;
  border-bottom-right-radius: 4px;
}

.msg-bubble--ai {
  background: var(--ai-bubble);
  color: var(--text-primary);
  border-bottom-left-radius: 4px;
  border: 1px solid var(--border);
}

.msg-time {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 4px;
  padding: 0 4px;
}

/* ===== Message Content Styles ===== */
.msg-content :deep(pre.code-block) {
  background: rgba(0, 0, 0, 0.4);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 12px 14px;
  overflow-x: auto;
  margin: 8px 0;
  font-family: 'Cascadia Code', 'Fira Code', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.5;
}

.msg-content :deep(code.inline-code) {
  background: rgba(0, 0, 0, 0.3);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 4px;
  padding: 1px 6px;
  font-family: 'Cascadia Code', 'Fira Code', 'Consolas', monospace;
  font-size: 12.5px;
  color: #a5f3fc;
}

.msg-bubble--user .msg-content :deep(code.inline-code) {
  background: rgba(255, 255, 255, 0.15);
  border-color: rgba(255, 255, 255, 0.25);
  color: #e0f2fe;
}

/* ===== Typing Cursor ===== */
.typing-cursor {
  display: inline-block;
  width: 2px;
  height: 1em;
  background: var(--accent-hover);
  border-radius: 1px;
  margin-left: 2px;
  vertical-align: text-bottom;
  animation: blink 0.8s step-end infinite;
}

/* ===== Typing Indicator ===== */
.typing-indicator {
  display: flex;
  gap: 5px;
  align-items: center;
  padding: 2px 4px;
}

.typing-indicator span {
  width: 7px;
  height: 7px;
  background: var(--text-muted);
  border-radius: 50%;
  animation: bounce 1.2s ease infinite;
}

.typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator span:nth-child(3) { animation-delay: 0.4s; }

/* ===== Input Area ===== */
.chat-input-area {
  padding: 12px 16px 16px;
  background: var(--bg-secondary);
  border-top: 1px solid var(--border);
  flex-shrink: 0;
}

.input-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  background: var(--bg-input);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 8px 8px 8px 16px;
  transition: border-color 0.2s ease;
  max-width: 900px;
  margin: 0 auto;
}

.input-wrapper:focus-within {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
}

.chat-input {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  color: var(--text-primary);
  font-size: 14px;
  line-height: 1.6;
  resize: none;
  min-height: 28px;
  max-height: 160px;
  font-family: inherit;
  padding: 4px 0;
}

.chat-input::placeholder {
  color: var(--text-muted);
}

.chat-input:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.send-btn {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  border: none;
  background: var(--border);
  color: var(--text-muted);
  cursor: not-allowed;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 0.2s ease;
}

.send-btn svg {
  width: 18px;
  height: 18px;
}

.send-btn--active {
  background: var(--accent);
  color: white;
  cursor: pointer;
}

.send-btn--active:hover {
  background: var(--accent-hover);
  transform: scale(1.05);
}

.stop-icon {
  animation: pulse 1s ease infinite;
}

.input-hint {
  text-align: center;
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 8px;
  max-width: 900px;
  margin-left: auto;
  margin-right: auto;
}

/* ===== Animations ===== */
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes slideIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes blink {
  50% { opacity: 0; }
}

@keyframes bounce {
  0%, 60%, 100% { transform: translateY(0); }
  30% { transform: translateY(-6px); }
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

/* ===== Responsive ===== */
@media (max-width: 640px) {
  .chat-header {
    padding: 10px 14px;
  }

  .session-badge {
    display: none;
  }

  .app-subtitle {
    display: none;
  }

  .chat-messages {
    padding: 16px 10px;
  }

  .msg-bubble-wrap {
    max-width: 85%;
  }
}
</style>
