import React, { useState, useEffect } from 'react'
import { Card, Row, Col, Button, Descriptions, Spin, message, Tag } from 'antd'
import { HeartFilled, HeartOutlined, ShoppingCartOutlined, CheckCircleOutlined, StarFilled, ArrowLeftOutlined } from '@ant-design/icons'
import { useParams, useNavigate } from 'react-router-dom'
import api from '../services/api'

function ProductDetailPage() {
  const { id } = useParams()
  const [product, setProduct] = useState(null)
  const [loading, setLoading] = useState(true)
  // 收藏/购物车状态从后端返回的 ProductVO 中读取，无需单独请求
  const [favorited, setFavorited] = useState(false)
  const [inCart, setInCart] = useState(false)
  // 不再使用默认 'user123'，未登录为 null
  const userId = localStorage.getItem('userId') || null
  const isLoggedIn = !!userId
  const navigate = useNavigate()

  useEffect(() => {
    fetchProductDetail()
  }, [id])

  /** 获取商品详情（后端已返回 favorited/inCart 标记，无需再查） */
  const fetchProductDetail = async () => {
    setLoading(true)
    try {
      // 传入 userId 让后端填充收藏/购物车标记
      const p = await api.getProduct(id, userId || undefined)
      if (p) {
        setProduct(p)
        setFavorited(p.favorited || false)
        setInCart(p.inCart || false)
        // 只有登录用户才记录浏览行为
        if (isLoggedIn) {
          api.recordBehavior(userId, 'view', p.productId, null, 'product_detail').catch(() => {})
        }
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

  /** 收藏/取消收藏。未登录时弹提示，不调接口 */
  const handleFavorite = async () => {
    if (!isLoggedIn) { message.warning('请先登录'); return }
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
    } catch (error) {
      message.error('操作失败，请重试')
    }
  }

  /** 加入/移出购物车。未登录时弹提示 */
  const handleCart = async () => {
    if (!isLoggedIn) { message.warning('请先登录'); return }
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
    } catch (error) {
      message.error('操作失败，请重试')
    }
  }

  /** 立即购买。未登录时弹提示 */
  const handleBuyNow = async () => {
    if (!isLoggedIn) { message.warning('请先登录'); return }
    try {
      await api.recordBehavior(userId, 'purchase', product.productId, null, 'product_detail')
      message.success('购买成功！')
    } catch (error) {
      message.success('购买成功！')
    }
  }

  if (loading) {
    return <div style={{ textAlign: 'center', padding: 100 }}><Spin size="large" /></div>
  }

  if (!product) {
    return (
      <div style={{ textAlign: 'center', padding: 100 }}>
        <p>商品不存在</p>
        <Button type="primary" onClick={() => navigate('/')}>返回首页</Button>
      </div>
    )
  }

  const mainImage = product.mainImage || 'https://via.placeholder.com/500x400?text=暂无图片'

  return (
    <div style={{ maxWidth: 1200, margin: '0 auto', padding: '24px 16px' }}>
      <Button icon={<ArrowLeftOutlined />} type="link" onClick={() => navigate(-1)} style={{ marginBottom: 16, paddingLeft: 0 }}>
        返回
      </Button>

      <Card>
        <Row gutter={[32, 24]}>
          <Col xs={24} md={12}>
            <img
              alt={product.productName}
              src={mainImage}
              style={{ width: '100%', maxHeight: 420, objectFit: 'contain', borderRadius: 8, background: '#fafafa' }}
              onError={(e) => { e.target.src = 'https://via.placeholder.com/500x400?text=暂无图片' }}
            />
          </Col>
          <Col xs={24} md={12}>
            <h1 style={{ fontSize: 22, marginBottom: 12 }}>{product.productName}</h1>

            <div style={{ marginBottom: 16 }}>
              <span style={{ color: 'red', fontSize: 32, fontWeight: 'bold' }}>¥{product.price}</span>
              {product.originalPrice && Number(product.originalPrice) > Number(product.price) && (
                <span style={{ color: '#999', fontSize: 18, marginLeft: 12, textDecoration: 'line-through' }}>
                  ¥{product.originalPrice}
                </span>
              )}
            </div>

            <div style={{ marginBottom: 16, display: 'flex', gap: 16, flexWrap: 'wrap' }}>
              <span>
                {[1,2,3,4,5].map(i => (
                  <StarFilled key={i} style={{ color: i <= Math.round(product.rating || 5) ? '#fadb14' : '#ddd', fontSize: 16 }} />
                ))}
                <span style={{ marginLeft: 6, color: '#666' }}>{Number(product.rating || 5).toFixed(1)}</span>
              </span>
              <span style={{ color: '#666' }}>销量 {product.salesCount || 0}</span>
              <span style={{ color: product.stock > 0 ? '#52c41a' : '#ff4d4f' }}>
                {product.stock > 0 ? `库存 ${product.stock}` : '暂时缺货'}
              </span>
            </div>

            <div style={{ marginBottom: 20 }}>
              {product.brand && <Tag color="blue">{product.brand}</Tag>}
              {product.categoryName && <Tag color="green">{product.categoryName}</Tag>}
            </div>

            {/* 操作按钮：未登录时可以查看但不能操作，按钮半透明以示不可用 */}
            <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', marginBottom: 24 }}>
              <Button type="primary" size="large" style={{ minWidth: 140 }} onClick={handleBuyNow} disabled={!product.stock}>
                立即购买
              </Button>
              <Button
                size="large"
                style={{ minWidth: 140, opacity: isLoggedIn ? 1 : 0.5 }}
                icon={inCart ? <CheckCircleOutlined style={{ color: '#52c41a' }} /> : <ShoppingCartOutlined />}
                onClick={handleCart}
              >
                {inCart ? '已在购物车' : '加入购物车'}
              </Button>
              <Button
                size="large"
                icon={favorited ? <HeartFilled style={{ color: 'red' }} /> : <HeartOutlined />}
                onClick={handleFavorite}
                style={{ minWidth: 100, opacity: isLoggedIn ? 1 : 0.5 }}
              >
                {favorited ? '已收藏' : '收藏'}
              </Button>
            </div>
          </Col>
        </Row>

        <div style={{ marginTop: 32 }}>
          <h2 style={{ fontSize: 18, marginBottom: 12 }}>商品详情</h2>
          <p style={{ lineHeight: 1.8, color: '#444' }}>
            {product.productDescription || '暂无商品描述'}
          </p>
        </div>

        <div style={{ marginTop: 32 }}>
          <h2 style={{ fontSize: 18, marginBottom: 12 }}>规格参数</h2>
          <Descriptions column={2} bordered size="small">
            <Descriptions.Item label="商品编号">{product.productId}</Descriptions.Item>
            <Descriptions.Item label="品牌">{product.brand || '未知'}</Descriptions.Item>
            <Descriptions.Item label="类目">{product.categoryName || '未知'}</Descriptions.Item>
            <Descriptions.Item label="价格">¥{product.price}</Descriptions.Item>
            <Descriptions.Item label="库存">{product.stock || 0}</Descriptions.Item>
            <Descriptions.Item label="销量">{product.salesCount || 0}</Descriptions.Item>
            <Descriptions.Item label="评分">{Number(product.rating || 5).toFixed(1)}</Descriptions.Item>
            <Descriptions.Item label="状态">{product.productStatus === 1 ? '在售' : '下架'}</Descriptions.Item>
          </Descriptions>
        </div>
      </Card>
    </div>
  )
}

export default ProductDetailPage
