import React, { useState, useEffect } from 'react'
import { Card, Row, Col, Button, Input, message, Spin } from 'antd'
import { SearchOutlined, HeartOutlined, ShoppingCartOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import api from '../services/api'

const { Search } = Input

function HomePage() {
  const [recommendations, setRecommendations] = useState([])
  const [loading, setLoading] = useState(true)
  const [userId] = useState('user123')
  const navigate = useNavigate()

  useEffect(() => {
    fetchRecommendations()
  }, [userId])

  const fetchRecommendations = async () => {
    setLoading(true)
    try {
      const response = await api.recommend(userId, 'homepage', 6)
      if (response.data && response.data.products) {
        setRecommendations(response.data.products)
      } else {
        const hotResponse = await api.getHotProducts(6)
        if (hotResponse.data) {
          setRecommendations(hotResponse.data)
        }
      }
    } catch (error) {
      console.error('获取推荐失败:', error)
      message.warning('获取推荐失败，显示热门商品')
      try {
        const hotResponse = await api.getHotProducts(6)
        if (hotResponse.data) {
          setRecommendations(hotResponse.data)
        }
      } catch (hotError) {
        console.error('获取热门商品失败:', hotError)
      }
    } finally {
      setLoading(false)
    }
  }

  const handleSearch = (value) => {
    if (value.trim()) {
      navigate(`/search?keyword=${encodeURIComponent(value)}`)
    }
  }

  const handleProductClick = (productId) => {
    navigate(`/product/${productId}`)
  }

  const handleRecordBehavior = async (productId, behaviorType) => {
    try {
      await api.recordBehavior(userId, behaviorType, productId)
    } catch (error) {
      console.error('记录行为失败:', error)
    }
  }

  return (
    <div style={{ maxWidth: 1200, margin: '0 auto' }}>
      <div style={{ marginBottom: 30, textAlign: 'center' }}>
        <h1 style={{ marginBottom: 20 }}>个性化推荐</h1>
        <Search
          placeholder="搜索商品"
          allowClear
          enterButton={<SearchOutlined />}
          size="large"
          onSearch={handleSearch}
          style={{ maxWidth: 600, margin: '0 auto' }}
        />
      </div>

      <div className="recommendation-section">
        <h2 style={{ marginBottom: 20 }}>为您推荐</h2>
        {loading ? (
          <div style={{ textAlign: 'center', padding: 100 }}>
            <Spin size="large" />
          </div>
        ) : recommendations.length > 0 ? (
          <Row gutter={[16, 16]}>
            {recommendations.map((product) => (
              <Col xs={24} sm={12} md={8} lg={6} key={product.productId}>
                <Card
                  className="product-card"
                  cover={
                    <img
                      alt={product.productName}
                      src={product.mainImage}
                      style={{ height: 200, objectFit: 'cover' }}
                      onClick={() => {
                        handleProductClick(product.productId)
                        handleRecordBehavior(product.productId, 'view')
                      }}
                    />
                  }
                  actions={[
                    <HeartOutlined 
                      key="favorite" 
                      onClick={() => handleRecordBehavior(product.productId, 'favorite')}
                    />,
                    <ShoppingCartOutlined 
                      key="cart"
                      onClick={() => handleRecordBehavior(product.productId, 'cart')}
                    />,
                    <Button 
                      key="buy" 
                      type="primary" 
                      onClick={() => handleProductClick(product.productId)}
                    >
                      查看详情
                    </Button>
                  ]}
                >
                  <h3 style={{ marginBottom: 10, fontSize: 16 }}>{product.productName}</h3>
                  <div style={{ marginBottom: 10 }}>
                    <span style={{ color: 'red', fontSize: 18, fontWeight: 'bold' }}>
                      ¥{product.price}
                    </span>
                    {product.originalPrice && product.originalPrice > product.price && (
                      <span style={{ color: '#999', fontSize: 14, marginLeft: 10, textDecoration: 'line-through' }}>
                        ¥{product.originalPrice}
                      </span>
                    )}
                  </div>
                  <div style={{ color: '#999', fontSize: 12 }}>
                    销量: {product.salesCount || 0} | 评分: {product.rating || 5.0}
                  </div>
                </Card>
              </Col>
            ))}
          </Row>
        ) : (
          <div style={{ textAlign: 'center', padding: 50 }}>
            <p>暂无推荐商品</p>
            <Button type="primary" onClick={fetchRecommendations}>
              刷新
            </Button>
          </div>
        )}
      </div>

      <div className="personalized-section">
        <h2 style={{ marginBottom: 20, marginTop: 40 }}>热门商品</h2>
        <Row gutter={[16, 16]}>
          {recommendations.slice(0, 4).map((product) => (
            <Col xs={24} sm={12} md={6} key={product.productId}>
              <Card
                className="product-card"
                cover={
                  <img
                    alt={product.productName}
                    src={product.mainImage}
                    style={{ height: 150, objectFit: 'cover' }}
                    onClick={() => handleProductClick(product.productId)}
                  />
                }
                actions={[
                  <Button 
                    key="buy" 
                    type="primary" 
                    size="small"
                    onClick={() => handleProductClick(product.productId)}
                  >
                    查看详情
                  </Button>
                ]}
              >
                <h3 style={{ marginBottom: 10, fontSize: 14 }}>{product.productName}</h3>
                <div style={{ color: 'red', fontSize: 16, fontWeight: 'bold' }}>
                  ¥{product.price}
                </div>
              </Card>
            </Col>
          ))}
        </Row>
      </div>
    </div>
  )
}

export default HomePage