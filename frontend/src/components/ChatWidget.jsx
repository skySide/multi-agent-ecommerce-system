import React, { useState, useRef, useEffect } from 'react'
import { Input, Button, Avatar, Spin, Tag, Card, message } from 'antd'
import { MessageOutlined, CloseOutlined, SendOutlined, RobotOutlined, UserOutlined, ShoppingOutlined, LikeOutlined, LikeFilled, DislikeOutlined, DislikeFilled } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import api from '../services/api'

const SUGGESTED_QUESTIONS = [
  '给我推荐几款手机',
  '适合学生党的笔记本电脑',
  '有哪些新品上市',
  '退换货政策是什么',
  '优惠券怎么使用',
  '最近有什么促销活动',
]

function ChatWidget() {
  const [visible, setVisible] = useState(false)
  const [messages, setMessages] = useState([
    { type: 'bot', content: '您好！我是智能购物助手，可以帮您推荐商品、解答售后问题。有什么可以帮您的吗？', products: [], rating: 0 }
  ])
  const [inputValue, setInputValue] = useState('')
  const [loading, setLoading] = useState(false)
  const [sessionId, setSessionId] = useState(null)
  const [lastUserMessage, setLastUserMessage] = useState('')
  // 未登录时不展示对话入口，直接 return 不渲染任何东西
  const userId = localStorage.getItem('userId') || null
  const isLoggedIn = !!userId
  const messagesEndRef = useRef(null)

  // 未登录：不渲染对话按钮和窗口
  if (!isLoggedIn) return null
  const navigate = useNavigate()

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const handleSend = async (text = inputValue) => {
    const userMsg = (text || '').trim()
    if (!userMsg) return

    setLastUserMessage(userMsg)
    setMessages(prev => [...prev, { type: 'user', content: userMsg }])
    setInputValue('')
    setLoading(true)

    try {
      const response = await api.chat(userId, userMsg, sessionId)

      if (response) {
        if (response.sessionId && !sessionId) {
          setSessionId(response.sessionId)
        }
        setMessages(prev => [...prev, {
          type: 'bot',
          content: response.message || '抱歉，我没有理解您的问题。',
          products: response.recommendedProducts || [],
          intent: response.intent,
          rating: 0
        }])
      }
    } catch (error) {
      console.error('对话请求失败:', error)
      setMessages(prev => [...prev, {
        type: 'bot',
        content: '抱歉，服务暂时不可用，请稍后再试。',
        products: [],
        rating: 0
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

  // 处理反馈
  const handleFeedback = async (index, rating) => {
    const msg = messages[index]
    if (msg.type !== 'bot' || msg.rating !== 0) {
      return // 已评价过
    }

    try {
      await api.submitFeedback(userId, sessionId, index, lastUserMessage, msg.content, rating)
      
      // 更新本地状态
      setMessages(prev => prev.map((m, i) => 
        i === index ? { ...m, rating } : m
      ))
      
      message.success(rating === 1 ? '感谢您的认可！' : '感谢您的反馈，我们会持续改进！')
    } catch (error) {
      console.error('反馈提交失败:', error)
      message.error('反馈提交失败')
    }
  }

  const showSuggestions = messages.length <= 1

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
            position: 'fixed', bottom: 30, right: 30,
            width: 56, height: 56, zIndex: 1000,
            boxShadow: '0 4px 12px rgba(0,0,0,0.2)'
          }}
        />
      )}

      {/* 对话窗口 */}
      {visible && (
        <div style={{
          position: 'fixed', bottom: 30, right: 30,
          width: 400, height: 580, zIndex: 1000,
          display: 'flex', flexDirection: 'column',
          background: '#fff', borderRadius: 12,
          boxShadow: '0 8px 32px rgba(0,0,0,0.18)',
          overflow: 'hidden'
        }}>
          {/* 标题栏 */}
          <div style={{
            padding: '12px 16px', background: '#1890ff', color: '#fff',
            display: 'flex', alignItems: 'center', justifyContent: 'space-between'
          }}>
            <span style={{ fontWeight: 600 }}><RobotOutlined style={{ marginRight: 8 }} />智能购物助手</span>
            <Button type="text" size="small" icon={<CloseOutlined />} onClick={() => setVisible(false)}
              style={{ color: '#fff' }} />
          </div>

          {/* 消息区域 */}
          <div style={{ flex: 1, overflowY: 'auto', padding: 12 }}>
            {messages.map((msg, index) => (
              <div key={index} style={{
                display: 'flex',
                flexDirection: msg.type === 'user' ? 'row-reverse' : 'row',
                alignItems: 'flex-start',
                marginBottom: 12
              }}>
                <Avatar
                  icon={msg.type === 'user' ? <UserOutlined /> : <RobotOutlined />}
                  size={32}
                  style={{
                    background: msg.type === 'user' ? '#1890ff' : '#52c41a',
                    flexShrink: 0,
                    marginLeft: msg.type === 'user' ? 8 : 0,
                    marginRight: msg.type === 'user' ? 0 : 8,
                  }}
                />
                <div style={{ maxWidth: '78%' }}>
                  <div style={{
                    background: msg.type === 'user' ? '#e6f7ff' : '#f6ffed',
                    padding: '8px 12px', borderRadius: 10,
                    fontSize: 13, lineHeight: 1.6, wordBreak: 'break-word'
                  }}>
                    {msg.content}
                  </div>
                  
                  {/* 反馈按钮 - 仅对AI回复显示 */}
                  {msg.type === 'bot' && index > 0 && (
                    <div style={{ marginTop: 4, display: 'flex', gap: 8 }}>
                      <Button 
                        type="text" 
                        size="small"
                        icon={msg.rating === 1 ? <LikeFilled style={{ color: '#52c41a' }} /> : <LikeOutlined />}
                        onClick={() => handleFeedback(index, 1)}
                        style={{ fontSize: 12, color: msg.rating === 1 ? '#52c41a' : '#999' }}
                      >
                        有用
                      </Button>
                      <Button 
                        type="text" 
                        size="small"
                        icon={msg.rating === -1 ? <DislikeFilled style={{ color: '#ff4d4f' }} /> : <DislikeOutlined />}
                        onClick={() => handleFeedback(index, -1)}
                        style={{ fontSize: 12, color: msg.rating === -1 ? '#ff4d4f' : '#999' }}
                      >
                        没帮助
                      </Button>
                    </div>
                  )}
                  
                  {/* 推荐商品 */}
                  {msg.products?.length > 0 && (
                    <div style={{ marginTop: 8 }}>
                      {msg.intent && (
                        <Tag color="blue" style={{ marginBottom: 6, fontSize: 11 }}>
                          {msg.intent === 'recommend' ? '智能推荐' : msg.intent === 'knowledge_query' ? '知识问答' : msg.intent}
                        </Tag>
                      )}
                      {msg.products.map(product => (
                        <Card
                          key={product.productId}
                          size="small"
                          hoverable
                          style={{ marginBottom: 6, cursor: 'pointer' }}
                          bodyStyle={{ padding: '8px 10px' }}
                          onClick={() => handleProductClick(product.productId)}
                        >
                          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                            <img
                              src={product.mainImage || 'https://via.placeholder.com/50'}
                              alt={product.productName || product.name}
                              style={{ width: 50, height: 50, objectFit: 'cover', borderRadius: 4, flexShrink: 0 }}
                              onError={(e) => { e.target.src = 'https://via.placeholder.com/50' }}
                            />
                            <div style={{ flex: 1, minWidth: 0 }}>
                              <div style={{ fontWeight: 600, fontSize: 12, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                {product.productName || product.name}
                              </div>
                              <div style={{ color: 'red', fontSize: 13, fontWeight: 'bold' }}>¥{product.price}</div>
                              <div style={{ color: '#999', fontSize: 11 }}>{product.brand}</div>
                            </div>
                            <ShoppingOutlined style={{ color: '#1890ff', flexShrink: 0 }} />
                          </div>
                        </Card>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            ))}
            {loading && (
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '4px 0' }}>
                <Avatar icon={<RobotOutlined />} size={32} style={{ background: '#52c41a' }} />
                <div style={{ background: '#f6ffed', padding: '8px 12px', borderRadius: 10 }}>
                  <Spin size="small" /><span style={{ marginLeft: 8, color: '#999', fontSize: 12 }}>思考中...</span>
                </div>
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>

          {/* 预设问题 */}
          {showSuggestions && (
            <div style={{ padding: '0 12px 8px', borderTop: '1px solid #f5f5f5' }}>
              <div style={{ fontSize: 11, color: '#999', margin: '8px 0 6px' }}>您可以问我：</div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 5 }}>
                {SUGGESTED_QUESTIONS.map(q => (
                  <Tag
                    key={q}
                    color="blue"
                    style={{ cursor: 'pointer', fontSize: 11, marginBottom: 2 }}
                    onClick={() => handleSend(q)}
                  >
                    {q}
                  </Tag>
                ))}
              </div>
            </div>
          )}

          {/* 输入区域 */}
          <div style={{ padding: '10px 12px', borderTop: '1px solid #f0f0f0', display: 'flex', gap: 8 }}>
            <Input
              value={inputValue}
              onChange={(e) => setInputValue(e.target.value)}
              onKeyPress={handleKeyPress}
              placeholder="输入您的问题..."
              disabled={loading}
              style={{ borderRadius: 20 }}
            />
            <Button
              type="primary"
              shape="circle"
              icon={<SendOutlined />}
              onClick={() => handleSend()}
              loading={loading}
            />
          </div>
        </div>
      )}
    </>
  )
}

export default ChatWidget
