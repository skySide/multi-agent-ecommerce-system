import React, { useState, useEffect } from 'react'
import { Input, Button, Card, Row, Col, Spin, message } from 'antd'
import { SearchOutlined, HeartOutlined, ShoppingCartOutlined } from '@ant-design/icons'
import { useNavigate, useSearchParams } from 'react-router-dom'
import api from '../services/api'

const { Search } = Input

function SearchPage() {
  const [searchParams] = useSearchParams()
  const [keyword, setKeyword] = useState(searchParams.get('keyword') || '')
  const [searchResults, setSearchResults] = useState([])
  const [loading, setLoading] = useState(false)
  const [userId] = useState('user123')
  const navigate = useNavigate()

  useEffect(() => {
    if (keyword) {
      searchProducts()
    }
  }, [keyword])

  const searchProducts = async () => {
    if (!keyword.trim()) {
      return
    }

    setLoading(true)
    try {
      const response = await api.searchProducts(keyword, 20)
      if (response.data) {
        setSearchResults(response.data)
      } else {
        setSearchResults([])
      }
    } catch (error) {
      console.error('搜索失败:', error)
      message.error('搜索失败，请稍后重试')
      setSearchResults([])
    } finally {
      setLoading(false)
    }
  }

  const handleSearch = (value) => {
    setKeyword(value)
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
      <div style={{ marginBottom: 30 }}>
        <Search
          placeholder="搜索商品"
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
        <div className="search-result">
          <h2 style={{ marginBottom: 20 }}>搜索结果: {keyword}</h2>
          {loading ? (
            <div style={{ textAlign: 'center', padding: 100 }}>
              <Spin size="large" />
            </div>
          ) : searchResults.length > 0 ? (
            <Row gutter={[16, 16]}>
              {searchResults.map((product) => (
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
            <div style={{ textAlign: 'center', padding: 100 }}>
              <p>没有找到相关商品</p>
              <Button type="primary" onClick={searchProducts}>
                重新搜索
              </Button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

export default SearchPage