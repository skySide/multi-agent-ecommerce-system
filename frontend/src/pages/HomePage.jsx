import React, { useState, useEffect } from 'react'
import { Card, Row, Col, Button, Input, message, Spin, Badge } from 'antd'
import { SearchOutlined, HeartFilled, HeartOutlined, ShoppingCartOutlined, CheckCircleOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import api from '../services/api'

const { Search } = Input

// 统一商品字段（兼容 model.Product 和 entity.Product 两种格式）
function normalizeProduct(p) {
  return {
    productId: p.productId,
    productName: p.productName || p.name || '未知商品',
    price: p.price,
    originalPrice: p.originalPrice,
    mainImage: p.mainImage || 'https://via.placeholder.com/300x200?text=暂无图片',
    brand: p.brand || '',
    categoryName: p.categoryName || p.category || '',
    salesCount: p.salesCount || 0,
    rating: p.rating || p.score || 5.0,
    stock: p.stock || 0,
  }
}

function ProductCard({ product, userId, onFavoriteChange, onCartChange }) {
  const [favorited, setFavorited] = useState(false)
  const [inCart, setInCart] = useState(false)
  const navigate = useNavigate()
  const p = normalizeProduct(product)

  useEffect(() => {
    if (!userId || !p.productId) return
    api.checkFavorited(userId, p.productId).then(r => setFavorited(r?.favorited || false)).catch(() => {})
    api.checkInCart(userId, p.productId).then(r => setInCart(r?.inCart || false)).catch(() => {})
  }, [userId, p.productId])

  const handleFavorite = async (e) => {
    e.stopPropagation()
    try {
      if (favorited) {
        await api.removeFavorite(userId, p.productId)
        setFavorited(false)
        message.success('已取消收藏')
      } else {
        await api.addFavorite(userId, p.productId)
        setFavorited(true)
        message.success('已收藏')
      }
      onFavoriteChange?.()
    } catch (err) {
      message.error('操作失败')
    }
  }

  const handleCart = async (e) => {
    e.stopPropagation()
    try {
      if (inCart) {
        await api.removeFromCart(userId, p.productId)
        setInCart(false)
        message.success('已从购物车移除')
      } else {
        await api.addToCart(userId, p.productId)
        setInCart(true)
        message.success('已加入购物车')
      }
      onCartChange?.()
    } catch (err) {
      message.error('操作失败')
    }
  }

  return (
    <Card
      hoverable
      cover={
        <img
          alt={p.productName}
          src={p.mainImage}
          style={{ height: 200, objectFit: 'cover', cursor: 'pointer' }}
          onClick={() => navigate(`/product/${p.productId}`)}
          onError={(e) => { e.target.src = 'https://via.placeholder.com/300x200?text=暂无图片' }}
        />
      }
      actions={[
        favorited
          ? <HeartFilled key="favorite" style={{ color: 'red', fontSize: 18 }} onClick={handleFavorite} />
          : <HeartOutlined key="favorite" style={{ fontSize: 18 }} onClick={handleFavorite} />,
        inCart
          ? <CheckCircleOutlined key="cart" style={{ color: '#52c41a', fontSize: 18 }} onClick={handleCart} />
          : <ShoppingCartOutlined key="cart" style={{ fontSize: 18 }} onClick={handleCart} />,
        <Button key="detail" type="primary" size="small" onClick={() => navigate(`/product/${p.productId}`)}>
          查看详情
        </Button>
      ]}
    >
      <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 6, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
        {p.productName}
      </div>
      <div style={{ marginBottom: 6 }}>
        <span style={{ color: 'red', fontSize: 18, fontWeight: 'bold' }}>¥{p.price}</span>
        {p.originalPrice && Number(p.originalPrice) > Number(p.price) && (
          <span style={{ color: '#999', fontSize: 13, marginLeft: 8, textDecoration: 'line-through' }}>
            ¥{p.originalPrice}
          </span>
        )}
      </div>
      <div style={{ color: '#999', fontSize: 12 }}>
        {p.brand && <span style={{ marginRight: 8 }}>{p.brand}</span>}
        销量: {p.salesCount} | 评分: {Number(p.rating).toFixed(1)}
      </div>
    </Card>
  )
}

function HomePage() {
  const [recommendations, setRecommendations] = useState([])
  const [loading, setLoading] = useState(true)
  const userId = localStorage.getItem('userId') || 'user123'
  const navigate = useNavigate()

  useEffect(() => {
    fetchRecommendations()
  }, [userId])

  const fetchRecommendations = async () => {
    setLoading(true)
    try {
      const response = await api.recommend(userId, 'homepage', 8)
      if (response?.products?.length > 0) {
        setRecommendations(response.products)
        return
      }
    } catch (error) {
      console.error('推荐接口失败，降级到热门商品:', error)
    }
    // 降级：热门商品
    try {
      const hotProducts = await api.getHotProducts(8)
      if (hotProducts?.length > 0) {
        setRecommendations(hotProducts)
      }
    } catch (err) {
      console.error('获取热门商品失败:', err)
      message.warning('暂时无法加载商品')
    } finally {
      setLoading(false)
    }
    setLoading(false)
  }

  const handleSearch = (value) => {
    if (value.trim()) {
      navigate(`/search?keyword=${encodeURIComponent(value)}`)
    }
  }

  return (
    <div style={{ maxWidth: 1200, margin: '0 auto', padding: '24px 16px' }}>
      <div style={{ marginBottom: 32, textAlign: 'center' }}>
        <h1 style={{ marginBottom: 16 }}>个性化推荐</h1>
        <Search
          placeholder="搜索商品名称、品牌..."
          allowClear
          enterButton={<SearchOutlined />}
          size="large"
          onSearch={handleSearch}
          style={{ maxWidth: 600 }}
        />
      </div>

      <h2 style={{ marginBottom: 16 }}>为您推荐</h2>
      {loading ? (
        <div style={{ textAlign: 'center', padding: 100 }}>
          <Spin size="large" tip="AI正在为您生成个性化推荐..." />
        </div>
      ) : recommendations.length > 0 ? (
        <Row gutter={[16, 16]}>
          {recommendations.map((product) => (
            <Col xs={24} sm={12} md={8} lg={6} key={product.productId}>
              <ProductCard product={product} userId={userId} />
            </Col>
          ))}
        </Row>
      ) : (
        <div style={{ textAlign: 'center', padding: 50 }}>
          <p>暂无推荐商品</p>
          <Button type="primary" onClick={fetchRecommendations}>刷新</Button>
        </div>
      )}
    </div>
  )
}

export default HomePage
