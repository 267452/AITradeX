<template>
  <aside class="sidebar">
    <div class="sidebar-brand">
      <div class="brand-mark">AI</div>
      <div class="brand-copy">
        <h1>AITradeX</h1>
        <p>智能交易系统</p>
      </div>
    </div>
    <nav class="sidebar-scroll">
      <div class="nav-group">
        <div class="nav-title">主菜单</div>
        <router-link to="/" class="nav-item" :class="{ active: $route.path === '/' }">
          <span class="nav-icon">📊</span>
          <span class="nav-text">交易指挥台</span>
        </router-link>
        <router-link to="/chat" class="nav-item" :class="{ active: $route.path === '/chat' }">
          <span class="nav-icon">💬</span>
          <span class="nav-text">AI对话</span>
        </router-link>
      </div>
      <div class="nav-group">
        <div class="nav-title">系统管理</div>
        <router-link to="/agents" class="nav-item" :class="{ active: $route.path === '/agents' }">
          <span class="nav-icon">🤖</span>
          <span class="nav-text">智能体管理</span>
        </router-link>
        <router-link to="/models" class="nav-item" :class="{ active: $route.path === '/models' }">
          <span class="nav-icon">🧠</span>
          <span class="nav-text">模型管理</span>
        </router-link>
        <router-link to="/skills" class="nav-item" :class="{ active: $route.path === '/skills' }">
          <span class="nav-icon">⚡</span>
          <span class="nav-text">技能管理</span>
        </router-link>
        <router-link to="/mcp" class="nav-item" :class="{ active: $route.path === '/mcp' }">
          <span class="nav-icon">🔧</span>
          <span class="nav-text">MCP工具管理</span>
        </router-link>
        <router-link to="/knowledge" class="nav-item" :class="{ active: $route.path === '/knowledge' }">
          <span class="nav-icon">📚</span>
          <span class="nav-text">知识库管理</span>
        </router-link>
      </div>
    </nav>
    <div class="sidebar-foot">
      AITradeX v1.0<br>
      © 2024 版权所有
    </div>
  </aside>
</template>

<script setup>
</script>

<style scoped>
.sidebar {
  width: var(--sidebar-width);
  background:
    linear-gradient(180deg, rgba(13, 28, 47, 0.98), rgba(8, 20, 36, 0.98)),
    var(--bg-sidebar);
  border-right: 1px solid var(--line-dark);
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  z-index: 20;
  display: flex;
  flex-direction: column;
  box-shadow: 16px 0 40px rgba(3, 9, 18, 0.32);
}

.sidebar-brand {
  height: 84px;
  padding: 20px 18px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid rgba(140, 186, 223, 0.14);
  flex-shrink: 0;
}

.brand-mark {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: linear-gradient(135deg, #22c8ae, #1889b7);
  color: #f2fffb;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.6px;
  box-shadow: 0 10px 22px rgba(21, 157, 174, 0.45);
}

.brand-copy h1 {
  margin: 0;
  color: #f2f8ff;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.3px;
}

.brand-copy p {
  margin: 3px 0 0;
  color: rgba(192, 217, 240, 0.78);
  font-size: 12px;
}

.sidebar-scroll {
  overflow-y: auto;
  flex: 1;
  padding: 16px 0 20px;
}

.nav-group + .nav-group {
  margin-top: 12px;
}

.nav-title {
  padding: 0 18px;
  margin: 8px 0;
  font-size: 11px;
  letter-spacing: 0.7px;
  color: rgba(183, 210, 236, 0.54);
}

.nav-item {
  margin: 4px 10px;
  padding: 11px 12px;
  border-radius: 11px;
  border: 1px solid transparent;
  color: rgba(217, 235, 252, 0.78);
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.nav-item:hover {
  background: rgba(33, 60, 95, 0.58);
  border-color: rgba(133, 192, 224, 0.24);
  color: #f0f8ff;
}

.nav-item.active {
  background: linear-gradient(135deg, rgba(35, 184, 166, 0.22), rgba(24, 137, 183, 0.22));
  border-color: rgba(61, 194, 187, 0.48);
  color: #f1fffe;
  box-shadow: 0 10px 24px rgba(16, 125, 137, 0.22);
}

.nav-icon {
  width: 22px;
  height: 22px;
  flex-shrink: 0;
  border-radius: 7px;
  background: rgba(195, 228, 255, 0.16);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}

.nav-item.active .nav-icon {
  background: rgba(201, 252, 243, 0.24);
}

.nav-text {
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
}

.sidebar-foot {
  border-top: 1px solid rgba(140, 186, 223, 0.14);
  padding: 12px 18px 16px;
  color: rgba(182, 208, 233, 0.68);
  font-size: 11px;
  line-height: 1.5;
  flex-shrink: 0;
}
</style>
