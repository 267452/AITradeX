import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)
  const systemStatus = ref('running')
  const notifications = ref([])

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function setSystemStatus(status) {
    systemStatus.value = status
  }

  function addNotification(notification) {
    notifications.value.push({
      id: Date.now(),
      ...notification
    })
  }

  function removeNotification(id) {
    const index = notifications.value.findIndex(n => n.id === id)
    if (index > -1) {
      notifications.value.splice(index, 1)
    }
  }

  return {
    sidebarCollapsed,
    systemStatus,
    notifications,
    toggleSidebar,
    setSystemStatus,
    addNotification,
    removeNotification
  }
})
