import React, { useState, useEffect } from 'react'
import { Card, Row, Col, Button, Descriptions, Spin, message, Carousel } from 'antd'
import { HeartOutlined, ShoppingCartOutlined, StarOutlined } from '@ant-design/icons'
import { useParams, useNavigate } from 'react-router-dom'
import api from '../services/api'

function ProductDetailPage() {
  const { id } = useParams()
  const [product, setProduct] = useState(null)
  const [loading, setLoading] = useState(true)
  const [userId] = useState('user123')
  const navigate = useNavigate()

  useEffect(() => {
    fetchProductDetail()
  }, [id])

  const fetchProductDetail = async () => {
    setLoading(true)
    try {
      const response = await api.getProduct(id)
      if (response) {
        setProduct(response)
      } else {
        message.error('商品不存在')
      }
    } catch (error) {
      console.error('获取商品详情失败:', error)
      message.error('获取商品详情失败')
    } finally {
      setLoading(false)
    }
  }

  const handleAddToCart = async () => {
    try {
      await api.recordBehavior(userId, 'cart', product.productId)
      message.success('已添加到购物车')
    } catch (error) {
      message.success('已添加到购物车')
    }
  }

  const handleBuyNow = async () => {
    try {
      await api.recordBehavior(userId, 'click', product.productId)
      message.success('购买成功')
    } catch (error) {
      message.success('购买成功')
    }
  }

  const handleRecordView = async () => {
    try {
      await api.recordBehavior(userId, 'view', id)
    } catch (error) {
      console.error('记录浏览失败:', error)
    }
  }

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 100 }}>
        <Spin size="large" />
      </div>
    )
  }

  if (!product) {
    return (
      <div style={{ textAlign: 'center', padding: 100 }}>
        <p>商品不存在</p>
        <Button type="primary" onClick={() => navigate('/')}>返回首页</Button>
      </div>
    )
  }

  const images = product.images || (product.mainImage ? [product.mainImage] : [])

  return (
    <div style={{ maxWidth: 1200, margin: '0 auto' }}>
      <Button type="link" onClick={() => navigate('/')} style={{ marginBottom: 20 }}>
        ← 返回首页
      </Button>

      <Card>
        <Row gutter={[24, 24]}>
          <Col xs={24} md={12}>
            <Carousel autoplay onChange={handleRecordView}>
              {images.map((image, index) => (
                <div key={index}>
                  <img
                    alt={`${product.productName} ${index + 1}`}
                    src={image}
                    style={{ width: '100%', height: 400, objectFit: 'contain' }}
                  />
                </div>
              ))}
            </Carousel>
          </Col>
          <Col xs={24} md={12}>
            <h1 style={{ marginBottom: 20, fontSize: 24 }}>{product.productName}</h1>
            <div style={{ marginBottom: 20 }}>
              <span style={{ color: 'red', fontSize: 32, fontWeight: 'bold' }}>
                ¥{product.price}
              </span>
              {product.originalPrice && product.originalPrice > product.price && (
                <span style={{ color: '#999', fontSize: 18, marginLeft: 10, textDecoration: 'line-through' }}>
                  ¥{product.originalPrice}
                </span>
              )}
            </div>
            <div style={{ marginBottom: 20 }}>
              <div style={{ marginBottom: 10 }}>
                <span style={{ marginRight: 20 }}>销量: {product.salesCount || 0}</span>
                <span style={{ marginRight: 20 }}>
                  评分: {product.rating || 5.0}
                  <StarOutlined style={{ color: '#fadb14', marginLeft: 5 }} />
                </span>
                <span>库存: {product.stock || 0}</span>
              </div>
              <div style={{ color: '#999' }}>
                品牌: {product.brand || '未知'} | 类目: {product.categoryName || '未知'}
              </div>
            </div>
            <div style={{ marginBottom: 30 }}>
              <Button 
                type="primary" 
                size="large" 
                style={{ width: 150, marginRight: 10 }}
                onClick={handleBuyNow}
              >
                立即购买
              </Button>
              <Button 
                type="default" 
                size="large" 
                style={{ width: 150, marginRight: 10 }}
                onClick={handleAddToCart}
              >
                <ShoppingCartOutlined /> 加入购物车
              </Button>
              <Button 
                type="text" 
                size="large"
                onClick={async () => {
                  try {
                    await api.recordBehavior(userId, 'favorite', product.productId)
                    message.success('已收藏')
                  } catch (error) {
                    message.success('已收藏')
                  }
                }}
              >
                <HeartOutlined /> 收藏
              </Button>
            </div>
          </Col>
        </Row>

        <div style={{ marginTop: 40 }}>
          <h2 style={{ marginBottom: 20, fontSize: 18 }}>商品详情</h2>
          <div style={{ lineHeight: 1.8 }}>
            <p>{product.productDescription || product.description || '暂无商品描述'}</p>
          </div>
        </div>

        <div style={{ marginTop: 40 }}>
          <h2 style={{ marginBottom: 20, fontSize: 18 }}>规格参数</h2>
          <Descriptions column={2}>
            <Descriptions.Item label="商品编号">{product.productId}</Descriptions.Item>
            <Descriptions.Item label="品牌">{product.brand || '未知'}</Descriptions.Item>
            <Descriptions.Item label="类目">{product.categoryName || '未知'}</Descriptions.Item>
            <Descriptions.Item label="价格">¥{product.price}</Descriptions.Item>
            <Descriptions.Item label="库存">{product.stock || 0}</Descriptions.Item>
            <Descriptions.Item label="销量">{product.salesCount || 0}</Descriptions.Item>
            <Descriptions.Item label="评分">{product.rating || 5.0}</Descriptions.Item>
            <Descriptions.Item label="状态">{product.productStatus === 1 ? '在售' : '下架'}</Descriptions.Item>
          </Descriptions>
        </div>
      </Card>
    </div>
  )
}

export default ProductDetailPage