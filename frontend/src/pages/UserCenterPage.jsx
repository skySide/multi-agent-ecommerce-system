import React, { useState, useEffect } from 'react'
import { Card, Tabs, List, Avatar, Button, Spin, message, Badge, InputNumber, Popconfirm } from 'antd'
import { UserOutlined, ShoppingCartOutlined, HeartOutlined, HistoryOutlined, DeleteOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import api from '../services/api'

function UserCenterPage() {
  const [userInfo, setUserInfo] = useState(null)
  const [loading, setLoading] = useState(true)
  const [favorites, setFavorites] = useState([])
  const [cartItems, setCartItems] = useState([])
  const [history, setHistory] = useState([])
  const [historyProducts, setHistoryProducts] = useState({})
  const userId = localStorage.getItem('userId') || 'user123'
  const navigate = useNavigate()

  useEffect(() => {
    fetchAll()
  }, [userId])

  const fetchAll = async () => {
    setLoading(true)
    await Promise.allSettled([
      fetchUserInfo(),
      fetchFavorites(),
      fetchCart(),
      fetchHistory(),
    ])
    setLoading(false)
  }

  const fetchUserInfo = async () => {
    try {
      const user = await api.getUser(userId)
      if (user) setUserInfo(user)
    } catch (error) {
      console.error('获取用户信息失败:', error)
      setUserInfo({ userId, username: '用户', email: '', phone: '' })
    }
  }

  const fetchFavorites = async () => {
    try {
      const data = await api.getFavorites(userId)
      setFavorites(data || [])
    } catch (error) {
      console.error('获取收藏失败:', error)
    }
  }

  const fetchCart = async () => {
    try {
      const data = await api.getCart(userId)
      setCartItems(data || [])
    } catch (error) {
      console.error('获取购物车失败:', error)
    }
  }

  const fetchHistory = async () => {
    try {
      const behaviors = await api.getBehaviorsByType(userId, 'view', 20)
      if (behaviors?.length > 0) {
        setHistory(behaviors)
        // 批量获取商品信息
        const productIds = [...new Set(behaviors.map(b => b.productId).filter(Boolean))]
        const productMap = {}
        await Promise.allSettled(
          productIds.map(pid =>
            api.getProduct(pid).then(p => { if (p) productMap[pid] = p }).catch(() => {})
          )
        )
        setHistoryProducts(productMap)
      }
    } catch (error) {
      console.error('获取浏览历史失败:', error)
    }
  }

  const handleRemoveFavorite = async (productId) => {
    try {
      await api.removeFavorite(userId, productId)
      setFavorites(prev => prev.filter(f => f.productId !== productId))
      message.success('已取消收藏')
    } catch (error) {
      message.error('操作失败')
    }
  }

  const handleRemoveFromCart = async (productId) => {
    try {
      await api.removeFromCart(userId, productId)
      setCartItems(prev => prev.filter(c => c.productId !== productId))
      message.success('已移除')
    } catch (error) {
      message.error('操作失败')
    }
  }

  const handleUpdateQuantity = async (productId, quantity) => {
    try {
      await api.updateCartQuantity(userId, productId, quantity)
      setCartItems(prev => prev.map(c => c.productId === productId ? { ...c, quantity } : c))
    } catch (error) {
      message.error('更新失败')
    }
  }

  const cartTotal = cartItems.reduce((sum, item) => sum + (Number(item.price || 0) * (item.quantity || 1)), 0)

  if (loading) {
    return <div style={{ textAlign: 'center', padding: 100 }}><Spin size="large" /></div>
  }

  return (
    <div style={{ maxWidth: 1200, margin: '0 auto', padding: '24px 16px' }}>
      <Card style={{ marginBottom: 24 }}>
        <div style={{ display: 'flex', alignItems: 'center' }}>
          <Avatar size={72} src={userInfo?.avatarUrl} icon={<UserOutlined />} style={{ background: '#1890ff' }} />
          <div style={{ marginLeft: 20 }}>
            <h2 style={{ marginBottom: 4 }}>{userInfo?.username || '用户'}</h2>
            <div style={{ color: '#666', fontSize: 13 }}>
              <span style={{ marginRight: 16 }}>ID: {userInfo?.userId || userId}</span>
              {userInfo?.email && <span style={{ marginRight: 16 }}>邮箱: {userInfo.email}</span>}
              {userInfo?.phone && <span>手机: {userInfo.phone}</span>}
            </div>
            {userInfo?.registerTime && (
              <div style={{ color: '#999', fontSize: 12, marginTop: 4 }}>
                注册时间: {new Date(userInfo.registerTime).toLocaleDateString()}
              </div>
            )}
          </div>
        </div>
      </Card>

      <Card>
        <Tabs defaultActiveKey="cart">
          {/* 购物车 */}
          <Tabs.TabPane tab={<><ShoppingCartOutlined /> 购物车 <Badge count={cartItems.length} /></>} key="cart">
            {cartItems.length > 0 ? (
              <>
                <List
                  dataSource={cartItems}
                  renderItem={(item) => (
                    <List.Item
                      actions={[
                        <InputNumber
                          min={1}
                          max={item.stock || 99}
                          value={item.quantity}
                          onChange={(val) => handleUpdateQuantity(item.productId, val)}
                          size="small"
                          style={{ width: 70 }}
                        />,
                        <Popconfirm title="确认移除？" onConfirm={() => handleRemoveFromCart(item.productId)}>
                          <Button type="text" danger icon={<DeleteOutlined />} size="small" />
                        </Popconfirm>
                      ]}
                    >
                      <List.Item.Meta
                        avatar={
                          <img
                            src={item.mainImage || 'https://via.placeholder.com/60'}
                            alt={item.productName}
                            style={{ width: 60, height: 60, objectFit: 'cover', borderRadius: 4, cursor: 'pointer' }}
                            onClick={() => navigate(`/product/${item.productId}`)}
                            onError={(e) => { e.target.src = 'https://via.placeholder.com/60' }}
                          />
                        }
                        title={
                          <span style={{ cursor: 'pointer' }} onClick={() => navigate(`/product/${item.productId}`)}>
                            {item.productName}
                          </span>
                        }
                        description={item.brand}
                      />
                      <div style={{ textAlign: 'right' }}>
                        <div style={{ color: 'red', fontWeight: 'bold', fontSize: 16 }}>¥{item.price}</div>
                        <div style={{ color: '#999', fontSize: 12 }}>小计: ¥{(Number(item.price) * item.quantity).toFixed(2)}</div>
                      </div>
                    </List.Item>
                  )}
                />
                <div style={{ textAlign: 'right', marginTop: 16, padding: '12px 0', borderTop: '1px solid #f0f0f0' }}>
                  <span style={{ fontSize: 16, marginRight: 16 }}>
                    合计: <span style={{ color: 'red', fontWeight: 'bold', fontSize: 20 }}>¥{cartTotal.toFixed(2)}</span>
                  </span>
                  <Button type="primary" size="large">去结算</Button>
                </div>
              </>
            ) : (
              <div style={{ textAlign: 'center', padding: 50 }}>
                <p style={{ color: '#999' }}>购物车是空的</p>
                <Button type="primary" onClick={() => navigate('/')}>去逛逛</Button>
              </div>
            )}
          </Tabs.TabPane>

          {/* 收藏 */}
          <Tabs.TabPane tab={<><HeartOutlined /> 我的收藏 <Badge count={favorites.length} /></>} key="favorites">
            {favorites.length > 0 ? (
              <List
                grid={{ gutter: 16, xs: 2, sm: 3, md: 4, lg: 4 }}
                dataSource={favorites}
                renderItem={(item) => (
                  <List.Item>
                    <Card
                      size="small"
                      hoverable
                      cover={
                        <img
                          alt={item.productName}
                          src={item.mainImage || 'https://via.placeholder.com/150'}
                          style={{ height: 120, objectFit: 'cover' }}
                          onClick={() => navigate(`/product/${item.productId}`)}
                          onError={(e) => { e.target.src = 'https://via.placeholder.com/150' }}
                        />
                      }
                      actions={[
                        <Button type="primary" size="small" onClick={() => navigate(`/product/${item.productId}`)}>查看</Button>,
                        <Popconfirm title="取消收藏？" onConfirm={() => handleRemoveFavorite(item.productId)}>
                          <Button type="text" danger size="small" icon={<DeleteOutlined />} />
                        </Popconfirm>
                      ]}
                    >
                      <div style={{ fontSize: 12, fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {item.productName}
                      </div>
                      <div style={{ color: 'red', fontWeight: 'bold', marginTop: 4 }}>¥{item.price}</div>
                    </Card>
                  </List.Item>
                )}
              />
            ) : (
              <div style={{ textAlign: 'center', padding: 50 }}>
                <p style={{ color: '#999' }}>暂无收藏</p>
                <Button type="primary" onClick={() => navigate('/')}>去逛逛</Button>
              </div>
            )}
          </Tabs.TabPane>

          {/* 浏览历史 */}
          <Tabs.TabPane tab={<><HistoryOutlined /> 浏览历史</>} key="history">
            {history.length > 0 ? (
              <List
                grid={{ gutter: 16, xs: 2, sm: 3, md: 4, lg: 4 }}
                dataSource={history.filter(b => b.productId && historyProducts[b.productId])}
                renderItem={(behavior) => {
                  const p = historyProducts[behavior.productId]
                  if (!p) return null
                  return (
                    <List.Item>
                      <Card
                        size="small"
                        hoverable
                        cover={
                          <img
                            alt={p.productName}
                            src={p.mainImage || 'https://via.placeholder.com/150'}
                            style={{ height: 120, objectFit: 'cover' }}
                            onClick={() => navigate(`/product/${p.productId}`)}
                            onError={(e) => { e.target.src = 'https://via.placeholder.com/150' }}
                          />
                        }
                        actions={[
                          <Button type="primary" size="small" onClick={() => navigate(`/product/${p.productId}`)}>查看</Button>
                        ]}
                      >
                        <div style={{ fontSize: 12, fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                          {p.productName}
                        </div>
                        <div style={{ color: 'red', fontWeight: 'bold', marginTop: 4 }}>¥{p.price}</div>
                        <div style={{ color: '#bbb', fontSize: 10, marginTop: 2 }}>
                          {behavior.createTime ? new Date(behavior.createTime).toLocaleString() : ''}
                        </div>
                      </Card>
                    </List.Item>
                  )
                }}
              />
            ) : (
              <div style={{ textAlign: 'center', padding: 50 }}>
                <p style={{ color: '#999' }}>暂无浏览记录</p>
                <Button type="primary" onClick={() => navigate('/')}>去逛逛</Button>
              </div>
            )}
          </Tabs.TabPane>
        </Tabs>
      </Card>
    </div>
  )
}

export default UserCenterPage
