const API_BASE = '/api'

export function apiRequest(endpoint, options = {}) {
  const token = localStorage.getItem('token')
  const init = {
    method: options.method || 'GET',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { 'Authorization': `Bearer ${token}` } : {})
    }
  }
  if (options.body) {
    init.body = JSON.stringify(options.body)
  }
  return fetch(`${API_BASE}${endpoint}`, init)
    .then(r => r.json())
    .then(data => {
      if (data.code !== 200) {
        throw new Error(data.msg || '请求失败')
      }
      return data.data
    })
}
