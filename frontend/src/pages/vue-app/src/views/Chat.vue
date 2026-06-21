<template>
  <div class="chat-view">
    <div class="chat-container">
      <div class="chat-messages" ref="messagesContainer">
        <div v-for="(msg, index) in messages" :key="index"
             :class="['chat-message', msg.role]">
          <div class="chat-avatar">
            {{ msg.role === 'user' ? '👤' : '🤖' }}
          </div>
          <div class="chat-content">
            <div class="chat-text" v-html="formatMessage(msg.content)"></div>
          </div>
        </div>
        <div v-if="loading" class="chat-message assistant loading">
          <div class="chat-avatar">🤖</div>
          <div class="chat-content">
            <div class="chat-text">思考中...</div>
          </div>
        </div>
      </div>

      <div class="chat-input-row">
        <textarea
          v-model="inputMessage"
          @keydown.enter.exact.prevent="sendMessage"
          placeholder="输入消息..."
          rows="2"
        ></textarea>
        <button class="btn btn-primary" @click="sendMessage" :disabled="loading">
          发送
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { apiRequest } from '@/api/index.js'

const messages = ref([])
const inputMessage = ref('')
const loading = ref(false)
const messagesContainer = ref(null)

async function sendMessage() {
  if (!inputMessage.value.trim() || loading.value) return

  const userMessage = inputMessage.value.trim()
  messages.value.push({ role: 'user', content: userMessage })
  inputMessage.value = ''
  loading.value = true

  try {
    const response = await apiRequest('/ai/simple-chat', {
      method: 'POST',
      body: { message: userMessage }
    })

    messages.value.push({
      role: 'assistant',
      content: response.message || response.response || '收到'
    })
  } catch (error) {
    messages.value.push({
      role: 'assistant',
      content: `错误: ${error.message}`
    })
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

function formatMessage(text) {
  if (!text) return ''
  return text.replace(/\n/g, '<br>')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
}
</script>

<style scoped>
.chat-view {
  height: 100%;
  padding: 20px;
}

.chat-container {
  height: calc(100vh - 140px);
  display: flex;
  flex-direction: column;
  background: var(--bg-panel, #111d31);
  border-radius: 16px;
  border: 1px solid var(--line-dark, rgba(120, 182, 210, 0.2));
  overflow: hidden;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.chat-message {
  display: flex;
  gap: 12px;
  max-width: 80%;
}

.chat-message.user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.chat-message.assistant {
  align-self: flex-start;
}

.chat-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--brand-soft, rgba(35, 184, 166, 0.12));
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.chat-content {
  padding: 12px 16px;
  border-radius: 12px;
  line-height: 1.6;
}

.chat-message.user .chat-content {
  background: var(--brand, #23b8a6);
  color: white;
}

.chat-message.assistant .chat-content {
  background: var(--bg-panel-soft, #15263f);
  color: var(--text-light, #d7e6f7);
}

.chat-message.loading .chat-content {
  opacity: 0.7;
}

.chat-input-row {
  display: flex;
  gap: 12px;
  padding: 16px 20px;
  background: rgba(15, 30, 51, 0.5);
  border-top: 1px solid var(--line-dark, rgba(120, 182, 210, 0.2));
}

.chat-input-row textarea {
  flex: 1;
  background: var(--bg-panel, #0d1525);
  border: 1px solid var(--line-dark, rgba(120, 182, 210, 0.2));
  border-radius: 8px;
  padding: 10px 14px;
  color: var(--text-light, #d7e6f7);
  font-family: inherit;
  font-size: 14px;
  resize: none;
}

.chat-input-row textarea:focus {
  outline: none;
  border-color: var(--brand, #23b8a6);
}

.btn {
  padding: 10px 24px;
  border-radius: 8px;
  border: none;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-primary {
  background: var(--brand, #23b8a6);
  color: white;
}

.btn-primary:hover {
  background: var(--brand-strong, #1197a7);
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
