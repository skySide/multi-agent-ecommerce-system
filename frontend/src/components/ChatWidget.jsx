import React, { useState, useRef, useEffect, useCallback } from 'react'
import { Input, Button, Avatar, Spin, Tag, Card, message, Modal, Checkbox, List, Badge, Divider } from 'antd'
import { MessageOutlined, CloseOutlined, SendOutlined, RobotOutlined, UserOutlined, ShoppingOutlined, LikeOutlined, LikeFilled, DislikeOutlined, DislikeFilled, StopOutlined, HistoryOutlined, LeftOutlined, RightOutlined, PlusOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import api from '../services/api'

const SUGGESTED_QUESTIONS = [
  '给我推荐几款手机',
  '适合学生党的笔记本电脑',
  '有哪些新品上市',
  '退换货政策是什么',
  '优惠券怎么使用',
  '最近有什么促销活动',
]

const DISLIKE_REASONS = [
  { label: '回答不准确', value: 'inaccurate' },
  { label: '答非所问', value: 'irrelevant' },
  { label: '信息不完整', value: 'incomplete' },
  { label: '回答太笼统', value: 'too_generic' },
  { label: '信息过时', value: 'outdated' },
  { label: '其他', value: 'other' },
]

const LIKE_REASONS = [
  { label: '有帮助', value: 'helpful' },
  { label: '节省了时间', value: 'saved_time' },
  { label: '其他', value: 'other' },
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

  // 反馈弹窗状态
  const [feedbackModalVisible, setFeedbackModalVisible] = useState(false)
  const [currentFeedbackIndex, setCurrentFeedbackIndex] = useState(null)
  const [currentRating, setCurrentRating] = useState(0)
  const [selectedReasons, setSelectedReasons] = useState([])
  const [feedbackComment, setFeedbackComment] = useState('')

  // 展开/收起状态
  const [expanded, setExpanded] = useState(false)

  // 历史会话数据
  const [sessions, setSessions] = useState([])

  // 未登录：不渲染对话按钮和窗口
  if (!isLoggedIn) return null
  const navigate = useNavigate()

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  // 组件卸载时上报会话突然结束
  useEffect(() => {
    return () => {
      if (sessionId && messages.length > 1) {
        api.abandonSession(sessionId, userId).catch(() => {})
      }
    }
  }, [sessionId, messages.length, userId])

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

  const handleCancel = async () => {
    if (!sessionId) {
      return
    }
    try {
      await api.cancelGeneration(sessionId)
      message.info('已停止生成')
    } catch (error) {
      console.error('取消生成失败:', error)
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

  // 处理反馈 - 打开弹窗
  const handleFeedback = (index, rating) => {
    const msg = messages[index]
    if (msg.type !== 'bot' || msg.rating !== 0) {
      return
    }
    setCurrentFeedbackIndex(index)
    setCurrentRating(rating)
    setSelectedReasons([])
    setFeedbackComment('')
    setFeedbackModalVisible(true)
  }

  // 提交反馈（含详细原因）
  const handleSubmitFeedback = async () => {
    const index = currentFeedbackIndex
    const rating = currentRating
    const msg = messages[index]
    if (!msg || msg.type !== 'bot' || msg.rating !== 0) {
      setFeedbackModalVisible(false)
      return
    }

    const reasonStr = selectedReasons.join(',')
    const comment = feedbackComment.trim() || undefined

    try {
      await api.submitFeedback(userId, sessionId, index, lastUserMessage, msg.content, rating, reasonStr, comment)

      setMessages(prev => prev.map((m, i) =>
        i === index ? { ...m, rating } : m
      ))

      message.success(rating === 1 ? '感谢您的认可！' : '感谢您的反馈，我们会持续改进！')
    } catch (error) {
      console.error('反馈提交失败:', error)
      message.error('反馈提交失败')
    } finally {
      setFeedbackModalVisible(false)
    }
  }

  // 跳过详细反馈（仅保留基础 rating）
  const handleSkipFeedback = async () => {
    const index = currentFeedbackIndex
    const rating = currentRating
    const msg = messages[index]
    if (!msg || msg.type !== 'bot' || msg.rating !== 0) {
      setFeedbackModalVisible(false)
      return
    }

    try {
      await api.submitFeedback(userId, sessionId, index, lastUserMessage, msg.content, rating)
      setMessages(prev => prev.map((m, i) =>
        i === index ? { ...m, rating } : m
      ))
      message.success(rating === 1 ? '感谢您的认可！' : '感谢您的反馈，我们会持续改进！')
    } catch (error) {
      console.error('反馈提交失败:', error)
    } finally {
      setFeedbackModalVisible(false)
    }
  }

  // 切换展开/收起，展开时加载会话列表
  const handleToggleExpand = async () => {
    const nextExpanded = !expanded
    setExpanded(nextExpanded)
    if (nextExpanded) {
      try {
        const data = await api.listSessions(userId)
        setSessions(data || [])
      } catch (error) {
        console.error('加载会话列表失败:', error)
      }
    }
  }

  // 切换到历史会话
  const handleLoadSession = async (histSessionId) => {
    try {
      const history = await api.getSessionHistory(histSessionId)
      if (history && history.length > 0) {
        const loadedMessages = [{ type: 'bot', content: '您好！我是智能购物助手，可以帮您推荐商品、解答售后问题。有什么可以帮您的吗？', products: [], rating: 0 }]
        for (const entry of history) {
          if (entry.startsWith('用户: ')) {
            loadedMessages.push({ type: 'user', content: entry.substring(4) })
          } else if (entry.startsWith('助手: ')) {
            loadedMessages.push({ type: 'bot', content: entry.substring(4), products: [], rating: 0 })
          }
        }
        setMessages(loadedMessages)
        setSessionId(histSessionId)
        setExpanded(false)
        message.success('已切换到历史会话')
      }
    } catch (error) {
      console.error('加载历史会话失败:', error)
      message.error('加载历史会话失败')
    }
  }

  // 新增会话
  const handleNewSession = () => {
    setMessages([{ type: 'bot', content: '您好！我是智能购物助手，可以帮您推荐商品、解答售后问题。有什么可以帮您的吗？', products: [], rating: 0 }])
    setSessionId(null)
    setExpanded(false)
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
          width: expanded ? 900 : 400, height: expanded ? 600 : 580, zIndex: 1000,
          display: 'flex', flexDirection: 'column',
          background: '#fff', borderRadius: 12,
          boxShadow: '0 8px 32px rgba(0,0,0,0.18)',
          overflow: 'hidden',
          transition: 'width 0.25s ease, height 0.25s ease'
        }}>
          {/* 标题栏 */}
          <div style={{
            padding: '12px 16px', background: '#1890ff', color: '#fff',
            display: 'flex', alignItems: 'center', justifyContent: 'space-between'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Button type="text" size="small"
                icon={expanded ? <LeftOutlined /> : <RightOutlined />}
                onClick={handleToggleExpand}
                style={{ color: '#fff' }} title={expanded ? '收起' : '展开'} />
              <span style={{ fontWeight: 600 }}><RobotOutlined style={{ marginRight: 8 }} />智能购物助手</span>
            </div>
            <Button type="text" size="small" icon={<CloseOutlined />} onClick={() => setVisible(false)}
              style={{ color: '#fff' }} />
          </div>

          {/* 展开模式：左侧菜单栏 + 右侧对话区域 */}
          <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
            {expanded && (
              <div style={{
                width: 220, borderRight: '1px solid #f0f0f0',
                display: 'flex', flexDirection: 'column',
                background: '#fafafa'
              }}>
                {/* 新增会话 */}
                <div style={{ padding: '12px' }}>
                  <Button type="primary" block icon={<PlusOutlined />}
                    onClick={handleNewSession}>
                    新增会话
                  </Button>
                </div>
                <Divider style={{ margin: 0 }} />
                {/* 历史会话列表 */}
                <div style={{ flex: 1, overflowY: 'auto', padding: '0' }}>
                  <div style={{ padding: '8px 12px', fontSize: 12, color: '#999', fontWeight: 500 }}>
                    <HistoryOutlined style={{ marginRight: 6 }} />历史会话
                  </div>
                  {sessions.length === 0 ? (
                    <div style={{ color: '#999', textAlign: 'center', padding: 20, fontSize: 12 }}>
                      暂无历史会话
                    </div>
                  ) : (
                    <List
                      dataSource={sessions}
                      size="small"
                      split={false}
                      renderItem={(item) => (
                        <List.Item
                          style={{
                            cursor: 'pointer', padding: '10px 12px',
                            background: item.sessionId === sessionId ? '#e6f7ff' : 'transparent',
                            borderBottom: '1px solid #f5f5f5'
                          }}
                          onClick={() => handleLoadSession(item.sessionId)}
                        >
                          <List.Item.Meta
                            title={
                              <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                                {item.sessionId === sessionId && <Badge status="processing" />}
                                <span style={{ fontSize: 12, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: 160 }}>
                                  {item.summary || '会话 ' + (item.sessionId || '').substring(0, 8)}
                                </span>
                              </div>
                            }
                            description={
                              <div style={{ fontSize: 11, color: '#999' }}>
                                <span>{item.roundCount || 0} 轮</span>
                                <span style={{ marginLeft: 8 }}>
                                  {item.createTime ? new Date(item.createTime).toLocaleDateString() : ''}
                                </span>
                              </div>
                            }
                          />
                        </List.Item>
                      )}
                    />
                  )}
                </div>
              </div>
            )}
            {/* 右侧对话区域 */}
            <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
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
                    {msg.type === 'bot' ? (
                      <div className="markdown-content">
                        <ReactMarkdown remarkPlugins={[remarkGfm]}>
                          {msg.content}
                        </ReactMarkdown>
                      </div>
                    ) : (
                      msg.content
                    )}
                  </div>

                  {/* 反馈按钮 - 仅对AI回复显示 */}
                  {msg.type === 'bot' && index > 0 && (
                    <div style={{ marginTop: 4, display: 'flex', gap: 8 }}>
                      <Button
                        type="text"
                        size="small"
                        icon={msg.rating === 1 ? <LikeFilled style={{ color: '#52c41a' }} /> : <LikeOutlined />}
                        onClick={() => handleFeedback(index, 1)}
                        disabled={msg.rating !== 0}
                        style={{ fontSize: 12, color: msg.rating === 1 ? '#52c41a' : '#999' }}
                      >
                        有用
                      </Button>
                      <Button
                        type="text"
                        size="small"
                        icon={msg.rating === -1 ? <DislikeFilled style={{ color: '#ff4d4f' }} /> : <DislikeOutlined />}
                        onClick={() => handleFeedback(index, -1)}
                        disabled={msg.rating !== 0}
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
            {loading ? (
              <Button
                type="primary"
                shape="circle"
                danger
                icon={<StopOutlined />}
                onClick={handleCancel}
              />
            ) : (
              <Button
                type="primary"
                shape="circle"
                icon={<SendOutlined />}
                onClick={() => handleSend()}
              />
            )}
          </div>
            </div>
          </div>
        </div>
      )}

      {/* 反馈弹窗 */}
      <Modal
        title={currentRating === 1 ? '感谢您的认可！' : '感谢您的反馈！'}
        open={feedbackModalVisible}
        onCancel={() => setFeedbackModalVisible(false)}
        footer={[
          <Button key="skip" onClick={handleSkipFeedback}>跳过</Button>,
          <Button key="submit" type="primary" onClick={handleSubmitFeedback}>提交</Button>,
        ]}
        destroyOnClose
      >
        <div style={{ marginBottom: 16 }}>
          <div style={{ marginBottom: 8, fontWeight: 500 }}>
            {currentRating === -1 ? '您认为回答存在哪些问题？（可多选）' : '您觉得回答好在哪里？（可多选）'}
          </div>
          <Checkbox.Group
            options={currentRating === -1 ? DISLIKE_REASONS : LIKE_REASONS}
            value={selectedReasons}
            onChange={(values) => setSelectedReasons(values)}
            style={{ display: 'flex', flexDirection: 'column', gap: 8 }}
          />
        </div>
        <div>
          <div style={{ marginBottom: 8, fontWeight: 500 }}>补充说明（选填）：</div>
          <Input.TextArea
            value={feedbackComment}
            onChange={(e) => setFeedbackComment(e.target.value)}
            placeholder="请输入补充说明..."
            rows={3}
            maxLength={500}
            showCount
          />
        </div>
      </Modal>
    </>
  )
}

export default ChatWidget
