import React, { useState, useEffect } from 'react'
import { Input, Card, Row, Col, Spin, message } from 'antd'
import { SearchOutlined, HeartFilled, HeartOutlined, ShoppingCartOutlined, CheckCircleOutlined } from '@ant-design/icons'
import { useNavigate, useSearchParams } from 'react-router-dom'
import api from '../services/api'

const { Search } = Input

function ProductCard({ product, userId }) {
  const [favorited, setFavorited] = useState(false)
  const [inCart, setInCart] = useState(false)
  const navigate = useNavigate()

  useEffect(() => {
    if (!userId || !product.productId) return
    api.checkFavorited(userId, product.productId).then(r => setFavorited(r?.favorited || false)).catch(() => {})
    api.checkInCart(userId, product.productId).then(r => setInCart(r?.inCart || false)).catch(() => {})
  }, [userId, product.productId])

  const handleFavorite = async (e) => {
    e.stopPropagation()
    try {
      if (favorited) {
        await api.removeFavorite(userId, product.productId)
        setFavorited(false)
      } else {
        await api.addFavorite(userId, product.productId)
        setFavorited(true)
      }
    } catch (err) {
      message.error('操作失败')
    }
  }

  const handleCart = async (e) => {
    e.stopPropagation()
    try {
      if (inCart) {
        await api.removeFromCart(userId, product.productId)
        setInCart(false)
      } else {
        await api.addToCart(userId, product.productId)
        setInCart(true)
        message.success('已加入购物车')
      }
    } catch (err) {
      message.error('操作失败')
    }
  }

  return (
    <Card
      hoverable
      cover={
        <img
          alt={product.productName}
          src={product.mainImage || 'https://via.placeholder.com/300x200?text=暂无图片'}
          style={{ height: 200, objectFit: 'cover', cursor: 'pointer' }}
          onClick={() => navigate(`/product/${product.productId}`)}
          onError={(e) => { e.target.src = 'https://via.placeholder.com/300x200?text=暂无图片' }}
        />
      }
      actions={[
        favorited
          ? <HeartFilled key="fav" style={{ color: 'red', fontSize: 18 }} onClick={handleFavorite} />
          : <HeartOutlined key="fav" style={{ fontSize: 18 }} onClick={handleFavorite} />,
        inCart
          ? <CheckCircleOutlined key="cart" style={{ color: '#52c41a', fontSize: 18 }} onClick={handleCart} />
          : <ShoppingCartOutlined key="cart" style={{ fontSize: 18 }} onClick={handleCart} />,
      ]}
      onClick={() => navigate(`/product/${product.productId}`)}
    >
      <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 6, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
        {product.productName}
      </div>
      <div style={{ marginBottom: 4 }}>
        <span style={{ color: 'red', fontSize: 18, fontWeight: 'bold' }}>¥{product.price}</span>
        {product.originalPrice && Number(product.originalPrice) > Number(product.price) && (
          <span style={{ color: '#999', fontSize: 13, marginLeft: 8, textDecoration: 'line-through' }}>
            ¥{product.originalPrice}
          </span>
        )}
      </div>
      <div style={{ color: '#999', fontSize: 12 }}>
        {product.brand && <span style={{ marginRight: 8 }}>{product.brand}</span>}
        销量: {product.salesCount || 0} | 评分: {Number(product.rating || 5).toFixed(1)}
      </div>
    </Card>
  )
}

function SearchPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [keyword, setKeyword] = useState(searchParams.get('keyword') || '')
  const [searchResults, setSearchResults] = useState([])
  const [loading, setLoading] = useState(false)
  const userId = localStorage.getItem('userId') || 'user123'

  useEffect(() => {
    const kw = searchParams.get('keyword') || ''
    setKeyword(kw)
    if (kw) doSearch(kw)
  }, [searchParams])

  const doSearch = async (kw) => {
    if (!kw.trim()) return
    setLoading(true)
    try {
      api.recordBehavior(userId, 'search', null, kw).catch(() => {})
      const results = await api.searchProducts(kw, 20)
      setSearchResults(Array.isArray(results) ? results : [])
    } catch (error) {
      console.error('搜索失败:', error)
      message.error('搜索失败，请稍后重试')
      setSearchResults([])
    } finally {
      setLoading(false)
    }
  }

  const handleSearch = (value) => {
    if (value.trim()) {
      setSearchParams({ keyword: value })
    }
  }

  return (
    <div style={{ maxWidth: 1200, margin: '0 auto', padding: '24px 16px' }}>
      <div style={{ marginBottom: 24 }}>
        <Search
          placeholder="搜索商品名称、品牌..."
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          allowClear
          enterButton={<SearchOutlined />}
          size="large"
          onSearch={handleSearch}
          style={{ maxWidth: 600 }}
        />
      </div>

      {keyword && (
        <>
          <h2 style={{ marginBottom: 16 }}>
            "{keyword}" 的搜索结果
            {!loading && <span style={{ fontSize: 14, color: '#999', fontWeight: 400, marginLeft: 8 }}>共 {searchResults.length} 件</span>}
          </h2>
          {loading ? (
            <div style={{ textAlign: 'center', padding: 100 }}><Spin size="large" /></div>
          ) : searchResults.length > 0 ? (
            <Row gutter={[16, 16]}>
              {searchResults.map((product) => (
                <Col xs={24} sm={12} md={8} lg={6} key={product.productId}>
                  <ProductCard product={product} userId={userId} />
                </Col>
              ))}
            </Row>
          ) : (
            <div style={{ textAlign: 'center', padding: 80, color: '#999' }}>
              <p>没有找到 "{keyword}" 相关商品</p>
            </div>
          )}
        </>
      )}
    </div>
  )
}

export default SearchPage
