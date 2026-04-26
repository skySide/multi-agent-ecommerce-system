import React, { useState, useEffect } from 'react'
import { Card, Tabs, List, Avatar, Button, Spin, message } from 'antd'
import { UserOutlined, ShoppingCartOutlined, HeartOutlined, HistoryOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import api from '../services/api'

const { TabPane } = Tabs

function UserCenterPage() {
  const [userInfo, setUserInfo] = useState(null)
  const [loading, setLoading] = useState(true)
  const [orders, setOrders] = useState([])
  const [favorites, setFavorites] = useState([])
  const [history, setHistory] = useState([])
  const [userId] = useState('user123')
  const navigate = useNavigate()

  useEffect(() => {
    fetchUserInfo()
    fetchUserHistory()
  }, [userId])

  const fetchUserInfo = async () => {
    setLoading(true)
    try {
      const response = await api.getUser(userId)
      if (response.data) {
        setUserInfo(response.data)
      }
    } catch (error) {
      console.error('获取用户信息失败:', error)
      setUserInfo({
        userId: userId,
        username: '游客用户',
        email: 'guest@example.com',
        phone: '138****8888'
      })
    } finally {
      setLoading(false)
    }
  }

  const fetchUserHistory = async () => {
    try {
      const response = await api.request(`/api/v1/behaviors/${userId}/recent?limit=10`)
      if (response.data) {
        const formattedHistory = response.data.map(behavior => ({
          productId: behavior.productId || 'unknown',
          productName: '商品 ' + (behavior.productId || '').substring(0, 8),
          price: 0,
          viewTime: behavior.createTime
        }))
        setHistory(formattedHistory)
      }
    } catch (error) {
      console.error('获取浏览历史失败:', error)
    }
  }

  const handleProductClick = (productId) => {
    navigate(`/product/${productId}`)
  }

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 100 }}>
        <Spin size="large" />
      </div>
    )
  }

  return (
    <div style={{ maxWidth: 1200, margin: '0 auto' }}>
      <Card>
        <div style={{ display: 'flex', alignItems: 'center', marginBottom: 30 }}>
          <Avatar size={80} src={userInfo?.avatarUrl} icon={<UserOutlined />} />
          <div style={{ marginLeft: 20 }}>
            <h1 style={{ marginBottom: 10 }}>{userInfo?.username || '用户'}</h1>
            <div style={{ color: '#999' }}>
              <p>用户ID: {userInfo?.userId || userId}</p>
              {userInfo?.email && <p>邮箱: {userInfo.email}</p>}
              {userInfo?.phone && <p>手机: {userInfo.phone}</p>}
              {userInfo?.registerTime && <p>注册时间: {userInfo.registerTime}</p>}
            </div>
          </div>
        </div>

        <Tabs defaultActiveKey="history">
          <TabPane tab={<><ShoppingCartOutlined /> 我的订单</>} key="orders">
            <List
              dataSource={orders}
              locale={{ emptyText: '暂无订单' }}
              renderItem={(order) => (
                <List.Item>
                  <List.Item.Meta
                    title={order.productName}
                    description={`订单号: ${order.orderId} | 时间: ${order.orderTime} | 状态: ${order.status}`}
                  />
                  <div style={{ textAlign: 'right' }}>
                    <div style={{ marginBottom: 10 }}>¥{order.price} x {order.quantity}</div>
                    <Button type="link">查看详情</Button>
                  </div>
                </List.Item>
              )}
            />
          </TabPane>
          <TabPane tab={<><HeartOutlined /> 我的收藏</>} key="favorites">
            {favorites.length > 0 ? (
              <List
                grid={{ gutter: 16, column: 4 }}
                dataSource={favorites}
                renderItem={(item) => (
                  <List.Item>
                    <Card
                      size="small"
                      cover={
                        <img
                          alt={item.productName}
                          src={item.mainImage}
                          style={{ height: 100, objectFit: 'cover' }}
                        />
                      }
                      actions={[
                        <Button 
                          type="primary" 
                          size="small"
                          onClick={() => handleProductClick(item.productId)}
                        >
                          查看
                        </Button>
                      ]}
                    >
                      <h3 style={{ marginBottom: 5, fontSize: 12 }}>{item.productName}</h3>
                      <div style={{ color: 'red', fontSize: 14, fontWeight: 'bold' }}>
                        ¥{item.price}
                      </div>
                    </Card>
                  </List.Item>
                )}
              />
            ) : (
              <div style={{ textAlign: 'center', padding: 50 }}>
                <p>暂无收藏商品</p>
              </div>
            )}
          </TabPane>
          <TabPane tab={<><HistoryOutlined /> 浏览历史</>} key="history">
            {history.length > 0 ? (
              <List
                grid={{ gutter: 16, column: 4 }}
                dataSource={history}
                renderItem={(item) => (
                  <List.Item>
                    <Card
                      size="small"
                      cover={
                        <img
                          alt={item.productName}
                          src={item.mainImage || 'https://via.placeholder.com/150'}
                          style={{ height: 100, objectFit: 'cover' }}
                        />
                      }
                      actions={[
                        <Button 
                          type="primary" 
                          size="small"
                          onClick={() => handleProductClick(item.productId)}
                        >
                          查看
                        </Button>
                      ]}
                    >
                      <h3 style={{ marginBottom: 5, fontSize: 12 }}>{item.productName}</h3>
                      <div style={{ color: 'red', fontSize: 14, fontWeight: 'bold' }}>
                        ¥{item.price}
                      </div>
                      {item.viewTime && (
                        <div style={{ color: '#999', fontSize: 10, marginTop: 5 }}>
                          {new Date(item.viewTime).toLocaleString()}
                        </div>
                      )}
                    </Card>
                  </List.Item>
                )}
              />
            ) : (
              <div style={{ textAlign: 'center', padding: 50 }}>
                <p>暂无浏览记录</p>
              </div>
            )}
          </TabPane>
        </Tabs>
      </Card>
    </div>
  )
}

export default UserCenterPage