import React, { useState, useRef, useEffect } from 'react'
import { Card, Input, Button, List, Avatar, Spin, Tag, message } from 'antd'
import { MessageOutlined, CloseOutlined, SendOutlined, RobotOutlined, UserOutlined, ShoppingOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import api from '../services/api'

const SUGGESTED_QUESTIONS = [
  '最近优惠较大的商品',
  '给我推荐一款手机',
  '适合学生党的笔记本',
  '有哪些新品上市',
  '退换货政策是什么',
]

function ChatWidget() {
  const [visible, setVisible] = useState(false)
  const [messages, setMessages] = useState([
    { type: 'bot', content: '您好！我是智能购物助手，有什么可以帮您的吗？', products: [] }
  ])
  const [inputValue, setInputValue] = useState('')
  const [loading, setLoading] = useState(false)
  const [sessionId, setSessionId] = useState(null)
  const [userId] = useState('user123')
  const messagesEndRef = useRef(null)
  const navigate = useNavigate()

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const handleSend = async (text = inputValue) => {
    if (!text.trim()) return

    const userMsg = text.trim()
    setMessages(prev => [...prev, { type: 'user', content: userMsg }])
    setInputValue('')
    setLoading(true)

    try {
      const response = await api.request('/api/v1/conversation/chat', {
        method: 'POST',
        body: JSON.stringify({ userId, message: userMsg, sessionId })
      })

      // api.js 解包后 response 就是 ConversationResponse
      if (response) {
        if (response.sessionId && !sessionId) {
          setSessionId(response.sessionId)
        }
        setMessages(prev => [...prev, {
          type: 'bot',
          content: response.message || '抱歉，我没有理解您的问题。',
          products: response.recommendedProducts || [],
          intent: response.intent
        }])
      }
    } catch (error) {
      console.error('对话请求失败:', error)
      setMessages(prev => [...prev, {
        type: 'bot',
        content: '抱歉，服务暂时不可用，请稍后再试。',
        products: []
      }])
    } finally {
      setLoading(false)
    }
  }

  const handleProductClick = (productId) => {
    setVisible(false)
    navigate(`/product/${productId}`)
  }

  const handleKeyPress = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  return (
    <>
      {/* 悬浮按钮 */}
      {!visible && (
        <Button
          type="primary"
          shape="circle"
          size="large"
          icon={<MessageOutlined />}
          onClick={() => setVisible(true)}
          style={{
            position: 'fixed',
            bottom: 30,
            right: 30,
            width: 56,
            height: 56,
            zIndex: 1000,
            boxShadow: '0 4px 12px rgba(0,0,0,0.15)'
          }}
        />
      )}

      {/* 对话窗口 */}
      {visible && (
        <Card
          style={{
            position: 'fixed',
            bottom: 30,
            right: 30,
            width: 400,
            height: 560,
            zIndex: 1000,
            display: 'flex',
            flexDirection: 'column',
            boxShadow: '0 8px 24px rgba(0,0,0,0.15)'
          }}
          bodyStyle={{ padding: 0, height: '100%', display: 'flex', flexDirection: 'column' }}
          title={
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <span><RobotOutlined style={{ marginRight: 8 }} />智能购物助手</span>
              <Button type="text" size="small" icon={<CloseOutlined />} onClick={() => setVisible(false)} />
            </div>
          }
        >
          {/* 消息区域 */}
          <div style={{ flex: 1, overflowY: 'auto', padding: 16 }}>
            <List
              dataSource={messages}
              renderItem={(msg, index) => (
                <List.Item style={{ border: 'none', padding: '8px 0' }}>
                  <div style={{
                    display: 'flex',
                    flexDirection: msg.type === 'user' ? 'row-reverse' : 'row',
                    alignItems: 'flex-start',
                    width: '100%'
                  }}>
                    <Avatar
                      icon={msg.type === 'user' ? <UserOutlined /> : <RobotOutlined />}
                      style={{
                        backgroundColor: msg.type === 'user' ? '#1890ff' : '#52c41a',
                        marginRight: msg.type === 'user' ? 0 : 8,
                        marginLeft: msg.type === 'user' ? 8 : 0,
                        flexShrink: 0
                      }}
                    />
                    <div style={{
                      maxWidth: '80%',
                      backgroundColor: msg.type === 'user' ? '#e6f7ff' : '#f6ffed',
                      padding: '10px 14px',
                      borderRadius: 12,
                      wordBreak: 'break-word'
                    }}>
                      <div style={{ marginBottom: msg.products?.length > 0 ? 10 : 0 }}>
                        {msg.content}
                      </div>
                      {/* 推荐商品卡片 */}
                      {msg.products && msg.products.length > 0 && (
                        <div>
                          {msg.intent && (
                            <Tag color="blue" style={{ marginBottom: 8 }}>
                              {msg.intent === 'recommend' ? '智能推荐' : msg.intent}
                            </Tag>
                          )}
                          {msg.products.map(product => (
                            <Card
                              key={product.productId}
                              size="small"
                              hoverable
                              style={{ marginBottom: 8, cursor: 'pointer' }}
                              onClick={() => handleProductClick(product.productId)}
                            >
                              <div style={{ display: 'flex', alignItems: 'center' }}>
                                <img
                                  src={product.mainImage || 'https://via.placeholder.com/60'}
                                  alt={product.productName}
                                  style={{ width: 60, height: 60, objectFit: 'cover', borderRadius: 4, marginRight: 10 }}
                                />
                                <div style={{ flex: 1 }}>
                                  <div style={{ fontWeight: 'bold', fontSize: 13 }}>{product.productName}</div>
                                  <div style={{ color: 'red', fontSize: 14, fontWeight: 'bold' }}>
                                    ¥{product.price}
                                  </div>
                                  <div style={{ color: '#999', fontSize: 11 }}>
                                    {product.brand} | {product.category}
                                  </div>
                                </div>
                                <ShoppingOutlined style={{ color: '#1890ff' }} />
                              </div>
                            </Card>
                          ))}
                        </div>
                      )}
                    </div>
                  </div>
                </List.Item>
              )}
            />
            {loading && (
              <div style={{ textAlign: 'center', padding: 10 }}>
                <Spin size="small" /><span style={{ marginLeft: 8, color: '#999' }}>思考中...</span>
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>

          {/* 预设问题 */}
          {messages.length <= 1 && (
            <div style={{ padding: '0 16px 8px' }}>
              <div style={{ fontSize: 12, color: '#999', marginBottom: 6 }}>您可以问我：</div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                {SUGGESTED_QUESTIONS.map(q => (
                  <Tag
                    key={q}
                    color="blue"
                    style={{ cursor: 'pointer' }}
                    onClick={() => handleSend(q)}
                  >
                    {q}
                  </Tag>
                ))}
              </div>
            </div>
          )}

          {/* 输入区域 */}
          <div style={{ padding: 12, borderTop: '1px solid #f0f0f0', display: 'flex', gap: 8 }}>
            <Input
              value={inputValue}
              onChange={(e) => setInputValue(e.target.value)}
              onKeyPress={handleKeyPress}
              placeholder="输入您的问题..."
              disabled={loading}
            />
            <Button
              type="primary"
              icon={<SendOutlined />}
              onClick={() => handleSend()}
              loading={loading}
            />
          </div>
        </Card>
      )}
    </>
  )
}

export default ChatWidget
