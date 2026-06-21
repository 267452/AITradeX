<template>
  <header class="top-header">
    <div class="header-main" :class="{ 'is-primary-path': isPrimaryPath }">
      <div class="header-path">
        <span class="header-path-root">AITradeX</span>
        <span class="header-path-current">{{ currentTitle }}</span>
      </div>
      <h2 class="header-title">{{ currentTitle }}</h2>
      <p class="header-desc">{{ currentDesc }}</p>
    </div>
    <div class="header-actions">
      <div class="chip">
        <span class="dot"></span>
        <span class="chip-label">系统状态</span>
        <span>运行中</span>
      </div>
    </div>
  </header>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const routeMeta = {
  '/': { title: '交易指挥台', desc: '实时监控交易状态和系统概览' },
  '/agents': { title: '智能体管理', desc: '管理AI交易智能体' },
  '/models': { title: '模型管理', desc: '配置和管理AI模型' },
  '/skills': { title: '技能管理', desc: '管理智能体技能' },
  '/mcp': { title: 'MCP工具管理', desc: '管理MCP工具和资源' },
  '/knowledge': { title: '知识库管理', desc: '管理交易知识库' },
  '/chat': { title: 'AI对话', desc: '与AI助手进行对话' }
}

const isPrimaryPath = computed(() => route.path === '/')

const currentTitle = computed(() => routeMeta[route.path]?.title || '页面')
const currentDesc = computed(() => routeMeta[route.path]?.desc || '')
</script>

<style scoped>
.top-header {
  height: var(--header-height);
  padding: 14px 26px;
  border-bottom: 1px solid rgba(137, 182, 217, 0.2);
  background: linear-gradient(180deg, rgba(247, 252, 255, 0.97), rgba(241, 248, 254, 0.97));
  backdrop-filter: blur(8px);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
  position: sticky;
  top: 0;
  z-index: 15;
}

.header-main {
  min-width: 280px;
}

.header-path {
  margin: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  line-height: 1.2;
}

.header-path-root {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid #d8e5ef;
  background: rgba(255, 255, 255, 0.92);
  color: #6a839b;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.34px;
  white-space: nowrap;
}

.header-path-current {
  color: #20435f;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.12px;
}

.header-main.is-primary-path .header-path {
  display: grid;
  gap: 8px;
}

.header-main.is-primary-path .header-path-root {
  height: auto;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: #6f899f;
  font-size: 11px;
  letter-spacing: 0.44px;
}

.header-main.is-primary-path .header-path-current {
  color: #12304b;
  font-size: clamp(24px, 2.2vw, 30px);
  line-height: 1.08;
  font-weight: 700;
  letter-spacing: -0.45px;
}

.header-title {
  margin: 5px 0 0;
  color: #12304b;
  font-size: 23px;
  line-height: 1.25;
}

.header-desc {
  margin: 4px 0 0;
  color: #58718c;
  font-size: 12px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.chip {
  height: 36px;
  border: 1px solid #d3e4f1;
  border-radius: 10px;
  padding: 0 12px;
  background: #fcfeff;
  color: #2f4e6c;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.chip .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  box-shadow: 0 0 0 4px rgba(35, 178, 111, 0.18);
  background: var(--good);
}

.chip.warning .dot {
  background: var(--warn);
  box-shadow: 0 0 0 4px rgba(235, 159, 49, 0.2);
}

.chip.danger .dot {
  background: var(--bad);
  box-shadow: 0 0 0 4px rgba(223, 90, 106, 0.2);
}

.chip-label {
  color: #70869e;
  font-weight: 500;
}
</style>
