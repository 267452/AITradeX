<template>
  <div class="app-shell">
    <Sidebar />
    <div class="workspace">
      <Header />
      <main class="main-content">
        <slot />
      </main>
    </div>
  </div>
</template>

<script setup>
import Sidebar from './Sidebar.vue'
import Header from './Header.vue'
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: flex;
}

.workspace {
  margin-left: var(--sidebar-width);
  width: calc(100% - var(--sidebar-width));
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.main-content {
  flex: 1;
  padding: 24px 26px;
}
</style>
