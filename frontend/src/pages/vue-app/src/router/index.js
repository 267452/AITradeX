import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '../components/layout/AppLayout.vue'

const routes = [
  {
    path: '/',
    component: AppLayout,
    children: [
      { path: '', name: 'Overview', component: () => import('../views/Overview.vue') },
      { path: 'agents', name: 'Agents', component: () => import('../views/Agents.vue') },
      { path: 'models', name: 'Models', component: () => import('../views/Models.vue') },
      { path: 'skills', name: 'Skills', component: () => import('../views/Skills.vue') },
      { path: 'mcp', name: 'MCP', component: () => import('../views/McpTools.vue') },
      { path: 'knowledge', name: 'Knowledge', component: () => import('../views/Knowledge.vue') },
      { path: 'chat', name: 'Chat', component: () => import('../views/Chat.vue') }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
