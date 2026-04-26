const API_BASE_URL = '/api'

class ApiService {
  async request(url, options = {}) {
    try {
      const token = localStorage.getItem('token')
      const response = await fetch(url, {
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
          ...options.headers,
        },
        ...options,
      })

      const data = await response.json()

      if (data.code !== 200) {
        throw new Error(data.message || '请求失败')
      }

      return data.data
    } catch (error) {
      console.error('API请求错误:', error)
      throw error
    }
  }

  async getHealth() {
    return this.request(`${API_BASE_URL}/v1/health`)
  }

  async getHotProducts(limit = 10) {
    return this.request(`${API_BASE_URL}/v1/products/hot?limit=${limit}`)
  }

  async searchProducts(keyword, limit = 10) {
    return this.request(`${API_BASE_URL}/v1/products/search?keyword=${encodeURIComponent(keyword)}&limit=${limit}`)
  }

  async getProduct(productId) {
    return this.request(`${API_BASE_URL}/v1/products/${productId}`)
  }

  async recommend(userId, scene = 'homepage', numItems = 10) {
    return this.request(`${API_BASE_URL}/v1/recommend`, {
      method: 'POST',
      body: JSON.stringify({ userId, scene, numItems }),
    })
  }

  async getUser(userId) {
    return this.request(`${API_BASE_URL}/v1/users/${userId}`)
  }

  async createUser(userData) {
    return this.request(`${API_BASE_URL}/v1/users`, {
      method: 'POST',
      body: JSON.stringify(userData),
    })
  }

  async recordBehavior(userId, behaviorType, productId = null, searchKeyword = null) {
    return this.request(`${API_BASE_URL}/v1/behaviors/record`, {
      method: 'POST',
      body: JSON.stringify({ userId, productId, behaviorType, searchKeyword }),
    })
  }

  async getExperiments() {
    return this.request(`${API_BASE_URL}/v1/experiments`)
  }

  async assignExperiment(userId, experimentId = null) {
    const params = new URLSearchParams({ userId })
    if (experimentId) params.append('experimentId', experimentId)
    return this.request(`${API_BASE_URL}/v1/experiments/assign?${params}`)
  }
}

export default new ApiService()