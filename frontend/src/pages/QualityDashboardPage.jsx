import React, { useState, useEffect } from 'react'
import { Card, Row, Col, Statistic, Table, Tag, Spin, Select, Progress, Empty } from 'antd'
import { LikeOutlined, DislikeOutlined, WarningOutlined, TeamOutlined, FrownOutlined, RiseOutlined } from '@ant-design/icons'
import api from '../services/api'

const AGENT_LABELS = {
  recommend: '商品推荐',
  product_query: '商品查询',
  knowledge_query: '知识问答',
  compare: '商品对比',
  chitchat: '闲聊',
}

const AGENT_COLORS = {
  recommend: '#1890ff',
  product_query: '#52c41a',
  knowledge_query: '#722ed1',
  compare: '#fa8c16',
  chitchat: '#13c2c2',
}

function QualityDashboardPage() {
  const [loading, setLoading] = useState(true)
  const [overview, setOverview] = useState(null)
  const [agentStats, setAgentStats] = useState([])
  const [selectedAgent, setSelectedAgent] = useState('recommend')
  const [agentTrend, setAgentTrend] = useState([])

  useEffect(() => {
    fetchData()
  }, [])

  useEffect(() => {
    fetchAgentTrend(selectedAgent)
  }, [selectedAgent])

  const fetchData = async () => {
    setLoading(true)
    try {
      const [overviewData, statsData] = await Promise.all([
        api.getQualityOverview(7),
        api.getAgentStats(),
      ])
      setOverview(overviewData)
      setAgentStats(statsData || [])
    } catch (error) {
      console.error('加载质量数据失败:', error)
    } finally {
      setLoading(false)
    }
  }

  const fetchAgentTrend = async (agentName) => {
    try {
      const data = await api.getAgentTrend(agentName, 30)
      setAgentTrend(data || [])
    } catch (error) {
      console.error('加载Agent趋势失败:', error)
    }
  }

  if (loading) {
    return <div style={{ textAlign: 'center', padding: 100 }}><Spin size="large" tip="加载质量数据..." /></div>
  }

  // 各Agent满意度表格
  const agentColumns = [
    { title: 'Agent', dataIndex: 'agentName', key: 'agentName',
      render: (name) => <Tag color={AGENT_COLORS[name]}>{AGENT_LABELS[name] || name}</Tag>
    },
    { title: '满意度', dataIndex: 'satisfactionRate', key: 'satisfactionRate',
      render: (rate) => <span style={{ fontWeight: 600 }}>{rate != null ? rate + '%' : '-'}</span>,
      sorter: (a, b) => (a.satisfactionRate || 0) - (b.satisfactionRate || 0),
    },
    { title: '总反馈', dataIndex: 'totalFeedback', key: 'totalFeedback' },
    { title: '点赞', dataIndex: 'likeCount', key: 'likeCount',
      render: (v) => <span style={{ color: '#52c41a' }}>{v || 0}</span> },
    { title: '点踩', dataIndex: 'dislikeCount', key: 'dislikeCount',
      render: (v) => <span style={{ color: '#ff4d4f' }}>{v || 0}</span> },
    { title: '突然结束', dataIndex: 'abruptEndCount', key: 'abruptEndCount',
      render: (v) => <WarningOutlined style={{ color: v > 0 ? '#faad14' : '#999', marginRight: 4 }} /> + (v || 0) },
    { title: '转人工', dataIndex: 'transferToHumanCount', key: 'transferToHumanCount',
      render: (v) => <TeamOutlined style={{ color: v > 0 ? '#1890ff' : '#999', marginRight: 4 }} /> + (v || 0) },
    { title: '会话数', dataIndex: 'totalSessions', key: 'totalSessions' },
    { title: '平均轮数', dataIndex: 'avgRounds', key: 'avgRounds' },
  ]

  // 趋势表格
  const trendColumns = [
    { title: '日期', dataIndex: 'analysisDate', key: 'analysisDate' },
    { title: '满意度', dataIndex: 'satisfactionRate', key: 'satisfactionRate',
      render: (rate) => <span style={{ fontWeight: 600 }}>{rate != null ? rate + '%' : '-'}</span>
    },
    { title: '总反馈', dataIndex: 'totalFeedback', key: 'totalFeedback' },
    { title: '点赞', dataIndex: 'likeCount', key: 'likeCount',
      render: (v) => <span style={{ color: '#52c41a' }}>{v || 0}</span> },
    { title: '点踩', dataIndex: 'dislikeCount', key: 'dislikeCount',
      render: (v) => <span style={{ color: '#ff4d4f' }}>{v || 0}</span> },
    { title: '突然结束', dataIndex: 'abruptEndCount', key: 'abruptEndCount' },
    { title: '转人工', dataIndex: 'transferToHumanCount', key: 'transferToHumanCount' },
  ]

  // 差评原因列
  const topDislikeAgent = [...(agentStats || [])]
    .filter(a => a.topDislikeReasons)
    .sort((a, b) => (b.dislikeCount || 0) - (a.dislikeCount || 0))[0]
  let dislikeReasons = []
  if (topDislikeAgent && topDislikeAgent.topDislikeReasons) {
    try {
      dislikeReasons = JSON.parse(topDislikeAgent.topDislikeReasons)
    } catch (e) {}
  }

  const reasonLabels = {
    inaccurate: '回答不准确', irrelevant: '答非所问', incomplete: '信息不完整',
    too_generic: '回答太笼统', outdated: '信息过时', other: '其他',
  }

  return (
    <div style={{ maxWidth: 1200, margin: '0 auto', padding: '24px 16px' }}>
      <h1 style={{ marginBottom: 24 }}>智能会话质量看板</h1>

      {/* 概览卡片 */}
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={4}>
          <Card>
            <Statistic title="满意率" value={overview?.satisfactionRate || 0}
              suffix="%" precision={1}
              valueStyle={{ color: (overview?.satisfactionRate || 0) >= 80 ? '#52c41a' : '#ff4d4f' }}
              prefix={<LikeOutlined />} />
          </Card>
        </Col>
        <Col span={4}>
          <Card>
            <Statistic title="总反馈数" value={overview?.totalFeedback || 0}
              valueStyle={{ color: '#1890ff' }} />
          </Card>
        </Col>
        <Col span={4}>
          <Card>
            <Statistic title="点踩数" value={overview?.totalDislike || 0}
              valueStyle={{ color: '#ff4d4f' }}
              prefix={<DislikeOutlined />} />
          </Card>
        </Col>
        <Col span={4}>
          <Card>
            <Statistic title="突然结束" value={overview?.abruptEndCount || 0}
              valueStyle={{ color: '#faad14' }}
              prefix={<FrownOutlined />} />
          </Card>
        </Col>
        <Col span={4}>
          <Card>
            <Statistic title="转人工率" value={overview?.transferRate || 0}
              suffix="%" precision={1}
              valueStyle={{ color: '#1890ff' }}
              prefix={<TeamOutlined />} />
          </Card>
        </Col>
        <Col span={4}>
          <Card>
            <Statistic title="总会话数" value={overview?.totalSessions || 0}
              valueStyle={{ color: '#722ed1' }}
              prefix={<RiseOutlined />} />
          </Card>
        </Col>
      </Row>

      <Row gutter={16} style={{ marginBottom: 24 }}>
        {/* 各Agent满意度对比 */}
        <Col span={14}>
          <Card title="各 Agent 满意度对比（昨日）" style={{ height: '100%' }}>
            {agentStats.length === 0 ? (
              <Empty description="暂无数据，请等待离线分析任务执行" />
            ) : (
              <div>
                {agentStats.map(agent => (
                  <div key={agent.agentName} style={{ marginBottom: 16 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                      <Tag color={AGENT_COLORS[agent.agentName]}>{AGENT_LABELS[agent.agentName] || agent.agentName}</Tag>
                      <span style={{ fontSize: 13 }}>
                        <span style={{ color: '#52c41a' }}>赞 {agent.likeCount || 0}</span>
                        <span style={{ margin: '0 8px', color: '#d9d9d9' }}>|</span>
                        <span style={{ color: '#ff4d4f' }}>踩 {agent.dislikeCount || 0}</span>
                        <span style={{ marginLeft: 8, fontWeight: 600 }}>
                          {agent.satisfactionRate != null ? agent.satisfactionRate + '%' : '-'}
                        </span>
                      </span>
                    </div>
                    <Progress
                      percent={agent.satisfactionRate || 0}
                      strokeColor={AGENT_COLORS[agent.agentName]}
                      showInfo={false}
                      size="small"
                    />
                  </div>
                ))}
              </div>
            )}
          </Card>
        </Col>

        {/* 差评原因分布 */}
        <Col span={10}>
          <Card title={`差评原因 TOP5${topDislikeAgent ? '（' + (AGENT_LABELS[topDislikeAgent.agentName] || topDislikeAgent.agentName) + '）' : ''}`}>
            {dislikeReasons.length === 0 ? (
              <Empty description="暂无差评数据" />
            ) : (
              <div>
                {dislikeReasons.map((item, idx) => {
                  const maxCount = dislikeReasons[0]?.count || 1
                  const percent = Math.round((item.count / maxCount) * 100)
                  return (
                    <div key={idx} style={{ marginBottom: 12 }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, marginBottom: 2 }}>
                        <span>{reasonLabels[item.reason] || item.reason}</span>
                        <span style={{ color: '#999' }}>{item.count} 次</span>
                      </div>
                      <Progress percent={percent} showInfo={false} size="small"
                        strokeColor={idx === 0 ? '#ff4d4f' : idx < 3 ? '#fa8c16' : '#d9d9d9'} />
                    </div>
                  )
                })}
              </div>
            )}
          </Card>
        </Col>
      </Row>

      {/* 各Agent详细指标表格 */}
      <Card title="各 Agent 质量指标明细" style={{ marginBottom: 24 }}>
        <Table
          dataSource={agentStats}
          columns={agentColumns}
          rowKey="agentName"
          pagination={false}
          size="small"
        />
      </Card>

      {/* 单个Agent近30天趋势 */}
      <Card title={
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <span>Agent 趋势分析</span>
          <Select value={selectedAgent} onChange={setSelectedAgent} style={{ width: 140 }} size="small">
            {Object.entries(AGENT_LABELS).map(([key, label]) => (
              <Select.Option key={key} value={key}>{label}</Select.Option>
            ))}
          </Select>
        </div>
      }>
        {agentTrend.length === 0 ? (
          <Empty description="暂无趋势数据，请等待离线分析任务积累" />
        ) : (
          <div>
            {/* 满意度趋势简图 */}
            <div style={{ display: 'flex', alignItems: 'flex-end', gap: 4, height: 120, marginBottom: 24, padding: '0 8px' }}>
              {agentTrend.map((item, idx) => (
                <div key={idx} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                  <span style={{ fontSize: 10, color: '#999', marginBottom: 2 }}>
                    {item.satisfactionRate != null ? item.satisfactionRate + '%' : ''}
                  </span>
                  <div style={{
                    width: '100%', maxWidth: 30,
                    height: Math.max(4, (item.satisfactionRate || 0) * 0.8),
                    background: (item.satisfactionRate || 0) >= 80 ? '#52c41a'
                      : (item.satisfactionRate || 0) >= 60 ? '#faad14' : '#ff4d4f',
                    borderRadius: '2px 2px 0 0',
                    minHeight: 2,
                  }} />
                  <span style={{ fontSize: 9, color: '#bbb', marginTop: 4, transform: 'rotate(-30deg)', whiteSpace: 'nowrap' }}>
                    {item.analysisDate ? item.analysisDate.substring(5) : ''}
                  </span>
                </div>
              ))}
            </div>
            <Table
              dataSource={agentTrend.reverse()}
              columns={trendColumns}
              rowKey="analysisDate"
              pagination={{ pageSize: 10 }}
              size="small"
            />
          </div>
        )}
      </Card>
    </div>
  )
}

export default QualityDashboardPage
