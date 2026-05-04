import React, { useState, useEffect } from 'react'
import { Card, Row, Col, Button, Input, message, Spin } from 'antd'
import { SearchOutlined, HeartFilled, HeartOutlined, ShoppingCartOutlined, CheckCircleOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import api from '../services/api'

const { Search } = Input

function ProductCard({ product, userId, onDataChange }) {
  const [favorited, setFavorited] = useState(product.favorited || false)
  const [inCart, setInCart] = useState(product.inCart || false)
  const navigate = useNavigate()
  const isLoggedIn = !!userId

  useEffect(() => {
    setFavorited(product.favorited || false)
    setInCart(product.inCart || false)
  }, [product.favorited, product.inCart])

  const requireLogin = () => {
    message.warning('请先登录')
  }

  const handleFavorite = async (e) => {
    e.stopPropagation()
    if (!isLoggedIn) { requireLogin(); return }
    try {
      if (favorited) {
        await api.removeFavorite(userId, product.productId)
        setFavorited(false)
        message.success('已取消收藏')
      } else {
        await api.addFavorite(userId, product.productId)
        setFavorited(true)
        message.success('已收藏')
      }
      onDataChange?.()
    } catch (err) {
      message.error('操作失败')
    }
  }

  const handleCart = async (e) => {
    e.stopPropagation()
    if (!isLoggedIn) { requireLogin(); return }
    try {
      if (inCart) {
        await api.removeFromCart(userId, product.productId)
        setInCart(false)
        message.success('已从购物车移除')
      } else {
        await api.addToCart(userId, product.productId)
        setInCart(true)
        message.success('已加入购物车')
      }
      onDataChange?.()
    } catch (err) {
      message.error('操作失败')
    }
  }

  const handleDetail = () => {
    if (!isLoggedIn) { requireLogin(); return }
    navigate(`/product/${product.productId}`)
  }

  const p = product
  const rating = p.rating != null ? Number(p.rating) : 5.0

  return (
    <Card
      hoverable
      cover={
        <img
          alt={p.productName || '商品'}
          src={p.mainImage || 'https://via.placeholder.com/300x200?text=暂无图片'}
          style={{ height: 200, objectFit: 'cover', cursor: isLoggedIn ? 'pointer' : 'not-allowed' }}
          onClick={handleDetail}
          onError={(e) => { e.target.src = 'https://via.placeholder.com/300x200?text=暂无图片' }}
        />
      }
      actions={[
        favorited
          ? <HeartFilled key="fav" style={{ color: 'red', fontSize: 18, opacity: isLoggedIn ? 1 : 0.4 }} onClick={handleFavorite} />
          : <HeartOutlined key="fav" style={{ fontSize: 18, opacity: isLoggedIn ? 1 : 0.4 }} onClick={handleFavorite} />,
        inCart
          ? <CheckCircleOutlined key="cart" style={{ color: '#52c41a', fontSize: 18, opacity: isLoggedIn ? 1 : 0.4 }} onClick={handleCart} />
          : <ShoppingCartOutlined key="cart" style={{ fontSize: 18, opacity: isLoggedIn ? 1 : 0.4 }} onClick={handleCart} />,
        <Button key="detail" type="primary" size="small" onClick={handleDetail}>查看详情</Button>,
      ]}
    >
      <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 6, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
        {p.productName || p.name || '未知商品'}
      </div>
      <div style={{ marginBottom: 6 }}>
        <span style={{ color: 'red', fontSize: 18, fontWeight: 'bold' }}>
          ¥{p.price != null ? (typeof p.price === 'number' ? p.price : p.price) : '--'}
        </span>
        {p.originalPrice && Number(p.originalPrice) > Number(p.price) && (
          <span style={{ color: '#999', fontSize: 13, marginLeft: 8, textDecoration: 'line-through' }}>
            ¥{p.originalPrice}
          </span>
        )}
      </div>
      <div style={{ color: '#999', fontSize: 12 }}>
        {p.brand && <span style={{ marginRight: 8 }}>{p.brand}</span>}
        销量: {p.salesCount || 0} | 评分: {rating.toFixed(1)}
      </div>
    </Card>
  )
}

function HomePage() {
  const [recommendations, setRecommendations] = useState([])
  const [loading, setLoading] = useState(true)
  const userId = localStorage.getItem('userId') || null
  const navigate = useNavigate()

  const fetchRecommendations = async () => {
    setLoading(true)
    try {
      const response = await api.recommend(userId || '', 'homepage', 8)
      const products = response?.productVOList || []
      setRecommendations(products)
    } catch (error) {
      console.error('推荐接口失败:', error)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchRecommendations()
  }, [userId])

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
              <ProductCard product={product} userId={userId} onDataChange={fetchRecommendations} />
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
