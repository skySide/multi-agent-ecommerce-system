import React from 'react'
import { Layout, Menu, ConfigProvider } from 'antd'
import { Link, Routes, Route } from 'react-router-dom'
import HomePage from './pages/HomePage'
import SearchPage from './pages/SearchPage'
import ProductDetailPage from './pages/ProductDetailPage'
import UserCenterPage from './pages/UserCenterPage'

const { Header, Content, Footer } = Layout

function App() {
  return (
    <ConfigProvider
      theme={{
        token: {
          colorPrimary: '#1890ff',
        },
      }}
    >
      <Layout>
        <Header style={{ display: 'flex', alignItems: 'center' }}>
          <div className="logo" style={{ color: 'white', fontSize: '18px', fontWeight: 'bold', marginRight: '30px' }}>
            多Agent电商推荐系统
          </div>
          <Menu
            theme="dark"
            mode="horizontal"
            defaultSelectedKeys={['home']}
            style={{ flex: 1, minWidth: 0 }}
          >
            <Menu.Item key="home">
              <Link to="/">首页推荐</Link>
            </Menu.Item>
            <Menu.Item key="search">
              <Link to="/search">商品搜索</Link>
            </Menu.Item>
            <Menu.Item key="user">
              <Link to="/user">个人中心</Link>
            </Menu.Item>
          </Menu>
        </Header>
        <Content>
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/search" element={<SearchPage />} />
            <Route path="/product/:id" element={<ProductDetailPage />} />
            <Route path="/user" element={<UserCenterPage />} />
          </Routes>
        </Content>
        <Footer style={{ textAlign: 'center' }}>
          多Agent电商推荐系统 ©{new Date().getFullYear()} Created by React + Ant Design
        </Footer>
      </Layout>
    </ConfigProvider>
  )
}

export default App