<template>
  <div class="chat-app">
    <!-- Fullscreen background video -->
    <video class="bg-video" autoplay muted loop playsinline>
      <source src="https://d8j0ntlcm91z4.cloudfront.net/user_38xzZboKViGWJOttwIXH07lWA1P/hf_20260217_030345_246c0224-10a4-422c-b324-070b7c0eceda.mp4" type="video/mp4" />
    </video>

    <!-- Hover target for the top bar -->
    <div class="top-hover-zone" aria-hidden="true"></div>

    <!-- Header -->
    <header class="chat-header">
      <div class="header-left">
        <div class="avatar-wrapper">
          <!-- AI spark icon -->
          <svg class="avatar-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 2l2.09 6.41L20.5 10l-6.41 2.09L12 18.5l-2.09-6.41L3.5 10l6.41-2.09z"/>
            <path d="M5 3l.9 2.6L8.5 6.5l-2.6.9L5 10l-.9-2.6L1.5 6.5l2.6-.9z" opacity=".5"/>
          </svg>
          <span class="status-dot"></span>
        </div>
        <div class="header-info">
          <h1 class="app-title">AI 编程小助手</h1>
          <p class="app-subtitle">编程学习 · 面试辅导 · 技术答疑</p>
        </div>
      </div>
      <div class="header-right">
        <!-- Mode Toggle -->
        <div class="mode-toggle">
          <button
            class="mode-btn"
            :class="{ 'mode-btn--active': mode === 'memory' }"
            @click="switchMode('memory')"
            title="记忆模式：多轮对话，带会话记忆"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
            </svg>
            记忆模式
          </button>
          <button
            class="mode-btn"
            :class="{ 'mode-btn--active': mode === 'route' }"
            @click="switchMode('route')"
            title="路由模式：自动识别内容类型并分发处理"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="3"/>
              <path d="M12 2v3M12 19v3M4.22 4.22l2.12 2.12M17.66 17.66l2.12 2.12M2 12h3M19 12h3M4.22 19.78l2.12-2.12M17.66 6.34l2.12-2.12"/>
            </svg>
            路由模式
          </button>
        </div>

        <div class="session-badge">
          <span class="session-label">会话 ID</span>
          <span class="session-id">{{ mode === 'memory' ? memoryId : routeConversationId }}</span>
        </div>
        <button class="new-chat-btn" @click="newChat" title="新建会话">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
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
        <div class="welcome-mode-badge" :class="mode === 'memory' ? 'badge--memory' : 'badge--route'">
          <svg v-if="mode === 'memory'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
          </svg>
          <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="3"/>
            <path d="M12 2v3M12 19v3M4.22 4.22l2.12 2.12M17.66 17.66l2.12 2.12M2 12h3M19 12h3M4.22 19.78l2.12-2.12M17.66 6.34l2.12-2.12"/>
          </svg>
          {{ mode === 'memory' ? '记忆模式' : '路由模式' }}
        </div>
        <h2 class="welcome-title">{{ mode === 'memory' ? '你好！我是 AI 编程小助手' : '内容路由处理器' }}</h2>
        <p class="welcome-desc">{{ mode === 'memory'
          ? '多轮对话模式，我会记住我们的聊天记录。随时向我提问编程学习或面试相关的问题！'
          : '路由模式会自动识别你输入的内容类型（文本、代码、链接等），并交由对应的处理器处理。'
        }}</p>
        <div class="quick-prompts">
          <button
            v-for="prompt in currentQuickPrompts"
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
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 2l2.09 6.41L20.5 10l-6.41 2.09L12 18.5l-2.09-6.41L3.5 10l6.41-2.09z"/>
            </svg>
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
            <svg viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 12c2.7 0 4.8-2.1 4.8-4.8S14.7 2.4 12 2.4 7.2 4.5 7.2 7.2 9.3 12 12 12zm0 2.4c-3.2 0-9.6 1.6-9.6 4.8v2.4h19.2v-2.4c0-3.2-6.4-4.8-9.6-4.8z"/>
            </svg>
          </div>
        </div>

        <!-- Typing indicator (before stream starts) -->
        <div v-if="isWaiting" class="message-row message-row--ai">
          <div class="msg-avatar msg-avatar--ai">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 2l2.09 6.41L20.5 10l-6.41 2.09L12 18.5l-2.09-6.41L3.5 10l6.41-2.09z"/>
            </svg>
          </div>
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
          :placeholder="mode === 'memory' ? '输入你的问题，按 Enter 发送，Shift+Enter 换行...' : '输入任意内容，AI 将自动识别类型并处理，按 Enter 发送...'"
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
          <svg v-if="!isStreaming" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="22" y1="2" x2="11" y2="13"/>
            <polygon points="22 2 15 22 11 13 2 9 22 2"/>
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
import { ref, computed, nextTick, onMounted, onBeforeUnmount } from 'vue'

// --- State ---
const memoryId = ref(generateMemoryId())
const routeConversationId = ref(generateConversationId())
const messages = ref([])
const inputText = ref('')
const isStreaming = ref(false)
const isWaiting = ref(false)
const messagesContainer = ref(null)
const inputRef = ref(null)
const mode = ref('memory') // 'memory' | 'route'

let currentEventSource = null

const memoryQuickPrompts = [
  '如何学习 Vue3？',
  '解释一下 Promise 和 async/await',
  '常见的前端面试题有哪些？',
  'Java 线程池的工作原理？',
]

const routeQuickPrompts = [
  '帮我总结一下什么是微服务架构',
  'Spring Boot 自动装配原理是什么？',
  '解释一下 TCP 三次握手的过程',
  '什么是 RESTful API 设计规范？',
]

const currentQuickPrompts = computed(() =>
  mode.value === 'memory' ? memoryQuickPrompts : routeQuickPrompts
)

// --- Helpers ---
function generateMemoryId() {
  return Math.floor(100000 + Math.random() * 900000)
}

function generateConversationId() {
  return 'r-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 8)
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
function switchMode(newMode) {
  if (newMode === mode.value) return
  if (isStreaming.value) stopStream()
  mode.value = newMode
  messages.value = []
  inputText.value = ''
  resetInputHeight()
  if (newMode === 'memory') {
    memoryId.value = generateMemoryId()
  } else {
    routeConversationId.value = generateConversationId()
  }
}

function newChat() {
  if (isStreaming.value) {
    stopStream()
  }
  if (mode.value === 'memory') {
    memoryId.value = generateMemoryId()
  } else {
    routeConversationId.value = generateConversationId()
  }
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

  const url = mode.value === 'memory'
    ? `/api/ai/chat?memoryId=${encodeURIComponent(memoryId.value)}&message=${encodeURIComponent(text)}`
    : `/api/ai/route?content=${encodeURIComponent(text)}&conversationId=${encodeURIComponent(routeConversationId.value)}`

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
/* ===== Background Video ===== */
.bg-video {
  position: fixed;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  z-index: 0;
}

/* ===== Layout ===== */
.chat-app {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: transparent;
  overflow: hidden;
  font-family: 'General Sans', 'Segoe UI', system-ui, -apple-system, sans-serif;
}

/* ===== Header ===== */
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 28px;
  background: rgba(0, 0, 0, 0.15);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  flex-shrink: 0;
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 20;
  transform: translateY(-100%);
  opacity: 0;
  pointer-events: none;
  transition: transform 0.24s ease, opacity 0.24s ease;
}

.top-hover-zone {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 20px;
  z-index: 19;
  background: transparent;
}

.top-hover-zone:hover + .chat-header,
.chat-header:hover {
  transform: translateY(0);
  opacity: 1;
  pointer-events: auto;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.avatar-wrapper {
  position: relative;
  width: 42px;
  height: 42px;
  background: rgba(255, 255, 255, 0.07);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--border-bright);
}

.avatar-svg {
  width: 22px;
  height: 22px;
  color: #ffffff;
}

.status-dot {
  position: absolute;
  bottom: -3px;
  right: -3px;
  width: 10px;
  height: 10px;
  background: #4ade80;
  border-radius: 50%;
  border: 2px solid #000;
  box-shadow: 0 0 6px rgba(74, 222, 128, 0.6);
}

.app-title {
  font-size: 15px;
  font-weight: 600;
  color: #ffffff;
  letter-spacing: 0.2px;
}

.app-subtitle {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 2px;
  letter-spacing: 0.2px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
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
  letter-spacing: 0.8px;
  font-weight: 500;
}

.session-id {
  font-size: 13px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.85);
  font-variant-numeric: tabular-nums;
  letter-spacing: 1.5px;
}

.new-chat-btn {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 8px 16px;
  background: rgba(255, 255, 255, 0.07);
  color: rgba(255, 255, 255, 0.8);
  border: 1px solid var(--border-bright);
  border-radius: var(--radius-sm);
  font-size: 13px;
  font-weight: 500;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.2s ease;
  letter-spacing: 0.2px;
}

.new-chat-btn svg {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
}

.new-chat-btn:hover {
  background: rgba(255, 255, 255, 0.14);
  border-color: rgba(255, 255, 255, 0.4);
  color: #ffffff;
  box-shadow: 0 0 16px rgba(255, 255, 255, 0.06);
}

/* ===== Mode Toggle ===== */
.mode-toggle {
  display: flex;
  align-items: center;
  background: rgba(0, 0, 0, 0.25);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  padding: 3px;
  gap: 2px;
}

.mode-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 13px;
  background: transparent;
  color: rgba(255, 255, 255, 0.45);
  border: none;
  border-radius: 7px;
  font-size: 12px;
  font-weight: 500;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.2s ease;
  letter-spacing: 0.2px;
  white-space: nowrap;
}

.mode-btn svg {
  width: 13px;
  height: 13px;
  flex-shrink: 0;
}

.mode-btn--active {
  background: rgba(255, 255, 255, 0.12);
  color: #ffffff;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.3);
}

.mode-btn:not(.mode-btn--active):hover {
  color: rgba(255, 255, 255, 0.75);
  background: rgba(255, 255, 255, 0.06);
}

/* ===== Welcome Mode Badge ===== */
.welcome-mode-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 14px;
  border-radius: 100px;
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.3px;
  margin-bottom: 4px;
}

.welcome-mode-badge svg {
  width: 13px;
  height: 13px;
}

.badge--memory {
  background: rgba(74, 222, 128, 0.1);
  border: 1px solid rgba(74, 222, 128, 0.25);
  color: rgba(74, 222, 128, 0.9);
}

.badge--route {
  background: rgba(96, 165, 250, 0.1);
  border: 1px solid rgba(96, 165, 250, 0.25);
  color: rgba(96, 165, 250, 0.9);
}

/* ===== Messages ===== */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 28px 20px;
  display: flex;
  flex-direction: column;
  gap: 18px;
  scroll-behavior: smooth;
  position: relative;
  z-index: 2;
  background: transparent;
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
  gap: 14px;
  animation: fadeIn 0.6s ease;
}

.welcome-title {
  font-size: 22px;
  font-weight: 600;
  color: #ffffff;
  letter-spacing: -0.2px;
}

.welcome-desc {
  font-size: 14px;
  color: var(--text-secondary);
  max-width: 380px;
  line-height: 1.7;
}

.quick-prompts {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
  margin-top: 8px;
}

.quick-btn {
  padding: 9px 18px;
  background: rgba(0, 0, 0, 0.2);
  color: rgba(255, 255, 255, 0.75);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 100px;
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.2s ease;
  backdrop-filter: blur(4px);
  letter-spacing: 0.1px;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.5);
}

.quick-btn:hover {
  background: rgba(255, 255, 255, 0.12);
  border-color: var(--border-bright);
  color: #ffffff;
  box-shadow: 0 0 12px rgba(255, 255, 255, 0.05);
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
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-bottom: 20px;
  border: 1px solid var(--border);
}

.msg-avatar svg {
  width: 17px;
  height: 17px;
}

.msg-avatar--ai {
  background: rgba(255, 255, 255, 0.07);
  border-color: var(--border-bright);
  color: rgba(255, 255, 255, 0.9);
}

.msg-avatar--user {
  background: rgba(255, 255, 255, 0.06);
  border-color: var(--border);
  color: rgba(255, 255, 255, 0.7);
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
  line-height: 1.7;
  font-size: 14px;
  word-break: break-word;
  position: relative;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
}

.msg-bubble--user {
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-bottom-right-radius: 5px;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.6);
}

.msg-bubble--ai {
  background: rgba(0, 0, 0, 0.25);
  color: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-bottom-left-radius: 5px;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.6);
}

.msg-time {
  font-size: 10px;
  color: var(--text-muted);
  margin-top: 5px;
  padding: 0 4px;
  letter-spacing: 0.3px;
}

/* ===== Message Content Styles ===== */
.msg-content :deep(pre.code-block) {
  background: rgba(0, 0, 0, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  padding: 12px 14px;
  overflow-x: auto;
  margin: 8px 0;
  font-family: 'Cascadia Code', 'Fira Code', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.55;
}

.msg-content :deep(code.inline-code) {
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 5px;
  padding: 1px 6px;
  font-family: 'Cascadia Code', 'Fira Code', 'Consolas', monospace;
  font-size: 12.5px;
  color: rgba(255, 255, 255, 0.85);
}

.msg-bubble--user .msg-content :deep(code.inline-code) {
  background: rgba(255, 255, 255, 0.12);
  border-color: rgba(255, 255, 255, 0.2);
  color: #ffffff;
}

/* ===== Typing Cursor ===== */
.typing-cursor {
  display: inline-block;
  width: 2px;
  height: 1em;
  background: rgba(255, 255, 255, 0.7);
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
  width: 6px;
  height: 6px;
  background: rgba(255, 255, 255, 0.4);
  border-radius: 50%;
  animation: bounce 1.2s ease infinite;
}

.typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator span:nth-child(3) { animation-delay: 0.4s; }

/* ===== Input Area ===== */
.chat-input-area {
  padding: 14px 20px 18px;
  background: rgba(0, 0, 0, 0.15);
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  flex-shrink: 0;
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  position: relative;
  z-index: 2;
}

.input-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  background: rgba(0, 0, 0, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: var(--radius);
  padding: 10px 10px 10px 18px;
  transition: border-color 0.25s ease, box-shadow 0.25s ease;
  max-width: 900px;
  margin: 0 auto;
  backdrop-filter: blur(8px);
}

.input-wrapper:focus-within {
  border-color: rgba(255, 255, 255, 0.3);
  box-shadow: 0 0 0 3px rgba(255, 255, 255, 0.04), 0 0 20px rgba(255, 255, 255, 0.04);
}

.chat-input {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  color: #ffffff;
  font-size: 14px;
  line-height: 1.65;
  resize: none;
  min-height: 28px;
  max-height: 160px;
  font-family: inherit;
  padding: 4px 0;
  letter-spacing: 0.1px;
}

.chat-input::placeholder {
  color: var(--text-muted);
}

.chat-input:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.send-btn {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.05);
  color: var(--text-muted);
  cursor: not-allowed;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 0.2s ease;
}

.send-btn svg {
  width: 17px;
  height: 17px;
}

.send-btn--active {
  background: rgba(255, 255, 255, 0.12);
  border-color: var(--border-bright);
  color: rgba(255, 255, 255, 0.9);
  cursor: pointer;
}

.send-btn--active:hover {
  background: rgba(255, 255, 255, 0.2);
  border-color: rgba(255, 255, 255, 0.45);
  color: #ffffff;
  box-shadow: 0 0 16px rgba(255, 255, 255, 0.08);
  transform: scale(1.04);
}

.stop-icon {
  animation: pulse 1s ease infinite;
}

.input-hint {
  text-align: center;
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 9px;
  max-width: 900px;
  margin-left: auto;
  margin-right: auto;
  letter-spacing: 0.2px;
}

/* ===== Animations ===== */
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(12px); }
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
  30% { transform: translateY(-5px); }
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.55; }
}

/* ===== Responsive ===== */
@media (max-width: 640px) {
  .chat-header {
    padding: 10px 16px;
  }

  .session-badge {
    display: none;
  }

  .app-subtitle {
    display: none;
  }

  .mode-btn span,
  .mode-btn {
    font-size: 11px;
    padding: 5px 9px;
    gap: 4px;
  }

  .chat-messages {
    padding: 16px 12px;
  }

  .msg-bubble-wrap {
    max-width: 86%;
  }
}
</style>
