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

  // ===== 商品 =====
  async getHotProducts(limit = 10) {
    return this.request(`${API_BASE_URL}/v1/products/hot?limit=${limit}`)
  }

  async searchProducts(keyword, limit = 10) {
    return this.request(`${API_BASE_URL}/v1/products/search?keyword=${encodeURIComponent(keyword)}&limit=${limit}`)
  }

  async getProduct(productId) {
    return this.request(`${API_BASE_URL}/v1/products/${productId}`)
  }

  // ===== 推荐 =====
  async recommend(userId, scene = 'homepage', numItems = 10) {
    return this.request(`${API_BASE_URL}/v1/recommend`, {
      method: 'POST',
      body: JSON.stringify({ userId, scene, numItems }),
    })
  }

  // ===== 用户 =====
  async getUser(userId) {
    return this.request(`${API_BASE_URL}/v1/users/${userId}`)
  }

  async login(username, password) {
    return this.request(`${API_BASE_URL}/v1/users/login`, {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    })
  }

  async register(username, password, email) {
    return this.request(`${API_BASE_URL}/v1/users/register`, {
      method: 'POST',
      body: JSON.stringify({ username, password, email }),
    })
  }

  // ===== 行为 =====
  async recordBehavior(userId, behaviorType, productId = null, searchKeyword = null, referrer = null) {
    return this.request(`${API_BASE_URL}/v1/behaviors/record`, {
      method: 'POST',
      body: JSON.stringify({ userId, behaviorType, productId, searchKeyword, referrer }),
    })
  }

  async getRecentBehaviors(userId, limit = 20) {
    return this.request(`${API_BASE_URL}/v1/behaviors/${userId}/recent?limit=${limit}`)
  }

  async getBehaviorsByType(userId, behaviorType, limit = 20) {
    return this.request(`${API_BASE_URL}/v1/behaviors/${userId}/type/${behaviorType}?limit=${limit}`)
  }

  // ===== 收藏 =====
  async checkFavorited(userId, productId) {
    return this.request(`${API_BASE_URL}/v1/favorites/check?userId=${userId}&productId=${productId}`)
  }

  async addFavorite(userId, productId) {
    return this.request(`${API_BASE_URL}/v1/favorites/add`, {
      method: 'POST',
      body: JSON.stringify({ userId, productId }),
    })
  }

  async removeFavorite(userId, productId) {
    return this.request(`${API_BASE_URL}/v1/favorites/remove`, {
      method: 'DELETE',
      body: JSON.stringify({ userId, productId }),
    })
  }

  async getFavorites(userId) {
    return this.request(`${API_BASE_URL}/v1/favorites/${userId}`)
  }

  // ===== 购物车 =====
  async checkInCart(userId, productId) {
    return this.request(`${API_BASE_URL}/v1/cart/check?userId=${userId}&productId=${productId}`)
  }

  async addToCart(userId, productId) {
    return this.request(`${API_BASE_URL}/v1/cart/add`, {
      method: 'POST',
      body: JSON.stringify({ userId, productId }),
    })
  }

  async removeFromCart(userId, productId) {
    return this.request(`${API_BASE_URL}/v1/cart/remove`, {
      method: 'DELETE',
      body: JSON.stringify({ userId, productId }),
    })
  }

  async updateCartQuantity(userId, productId, quantity) {
    return this.request(`${API_BASE_URL}/v1/cart/quantity`, {
      method: 'PUT',
      body: JSON.stringify({ userId, productId, quantity }),
    })
  }

  async getCart(userId) {
    return this.request(`${API_BASE_URL}/v1/cart/${userId}`)
  }

  // ===== 对话 =====
  async chat(userId, message, sessionId = null) {
    return this.request(`${API_BASE_URL}/v1/conversation/chat`, {
      method: 'POST',
      body: JSON.stringify({ userId, message, sessionId }),
    })
  }

  async createSession(userId) {
    return this.request(`${API_BASE_URL}/v1/conversation/session?userId=${userId}`, { method: 'POST' })
  }

  // ===== 实验 =====
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
