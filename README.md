# 新能源充电站管理平台

基于微信小程序的新能源充电站管理平台，为新能源汽车用户提供便捷的充电服务，同时为管理员提供高效的运营管理工具。

## 技术栈

| 层级 | 技术 |
|------|------|
| 用户端 | 微信原生小程序框架（WXML/WXSS/JS） |
| 管理端 | Vue3 + Element-Plus + ECharts + 高德地图API |
| 服务端 | Java17 + SpringBoot 3.x + MyBatis-Plus + Knife4J + DeepSeek AI |
| 数据库 | MySQL 8.0 |

## 项目结构

```
charging-station/
├── charging-station-server/     # 后端服务端（SpringBoot）
│   ├── src/main/java/com/charging/
│   │   ├── ChargingApplication.java      # 启动类
│   │   ├── config/                       # 配置类（CORS/MyBatis/Knife4J/WebMvc）
│   │   ├── controller/
│   │   │   ├── admin/                    # 管理端接口（8个Controller）
│   │   │   └── miniapp/                  # 小程序端接口（8个Controller）
│   │   ├── service/                      # 业务逻辑层（11个Service）
│   │   ├── mapper/                       # 数据访问层（11个Mapper）
│   │   ├── entity/                       # 实体类（11个实体）
│   │   ├── dto/                          # 数据传输对象
│   │   ├── common/                       # 公共模块（结果/异常/枚举）
│   │   ├── utils/                        # 工具类（JWT/AI）
│   │   └── interceptor/                  # 拦截器（JWT认证）
│   ├── src/main/resources/
│   │   ├── application.yml               # 主配置文件
│   │   └── sql/charging_station.sql      # 数据库脚本
│   └── pom.xml                           # Maven依赖
│
├── charging-station-admin/      # 管理端前端（Vue3）
│   ├── src/
│   │   ├── api/                          # 接口请求模块
│   │   ├── layouts/AdminLayout.vue       # 后台主布局
│   │   ├── router/index.js               # 路由配置
│   │   ├── store/user.js                 # Pinia状态管理
│   │   ├── utils/request.js              # axios封装
│   │   ├── views/                        # 页面视图
│   │   │   ├── login/                    # 登录页
│   │   │   ├── station/                  # 网点管理（列表+地图）
│   │   │   ├── charger/                  # 充电桩管理
│   │   │   ├── user/                     # 用户管理
│   │   │   ├── order/                    # 订单管理+数据统计
│   │   │   ├── feedback/                 # 故障反馈管理
│   │   │   ├── notice/                   # 公告管理
│   │   │   └── system/                   # 系统管理（AI配置+登录日志）
│   │   ├── App.vue
│   │   └── main.js
│   ├── vite.config.js
│   └── package.json
│
└── charging-station-miniapp/    # 用户端（微信小程序）
    ├── pages/
    │   ├── index/                # 首页（附近充电站+扫码）
    │   ├── station/              # 站点详情
    │   ├── charging/             # 扫码充电/充电中
    │   ├── order/                # 订单列表+详情
    │   ├── recharge/             # 账户充值
    │   ├── ai/                   # AI客服聊天
    │   ├── feedback/             # 故障反馈
    │   ├── notice/               # 公告列表+详情
    │   ├── profile/              # 个人中心
    │   └── login/                # 登录/注册
    ├── utils/request.js          # 请求封装
    ├── api/index.js              # API接口
    ├── app.js / app.json / app.wxss
    └── project.config.json
```

## 快速开始

### 1. 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0
- Node.js 18+
- 微信开发者工具

### 2. 数据库配置

```bash
# 登录 MySQL
mysql -u root -p

# 执行数据库脚本
source charging-station-server/src/main/resources/sql/charging_station.sql
```

### 3. 后端启动

```bash
cd charging-station-server

# 复制配置模板并填入本地数据库密码等敏感信息
# 将 src/main/resources/application-dev.yml.example 复制为 application-dev.yml
# 并修改其中的数据库密码、JWT 密钥、AI API Key
# （application-dev.yml 已被 .gitignore 忽略，不会提交到仓库）

# 编译并启动
mvn clean compile
mvn spring-boot:run
```

- 服务地址: http://localhost:8080/api
- 接口文档: http://localhost:8080/api/doc.html
- 默认管理员: admin / admin123

### 4. 管理端启动

```bash
cd charging-station-admin

# 安装依赖
npm install

# 开发模式启动
npm run dev
```

- 访问地址: http://localhost:3000
- API 代理: /api -> http://localhost:8080

### 5. 小程序启动

1. 打开微信开发者工具
2. 导入项目，选择 `charging-station-miniapp` 目录
3. 填入你的小程序 AppID
4. 修改 `app.js` 中的 `baseUrl` 为后端地址
5. 编译运行

## 功能模块

### 用户端（微信小程序）

| 模块 | 功能 |
|------|------|
| 登录/注册 | 手机号登录、微信一键登录、账号注册 |
| 网点查看 | 附近充电站、地图导航、站点详情 |
| 扫码充电 | 扫码启动充电、实时充电状态 |
| AI客服 | 智能问答、常见问题咨询 |
| 订单管理 | 充电订单、历史记录 |
| 账户充值 | 在线充值、余额查看 |
| 个人中心 | 信息管理、车辆绑定 |
| 故障反馈 | 提交故障、查看进度 |
| 公告查看 | 系统公告、活动通知 |

### 管理端（Web后台）

| 模块 | 功能 |
|------|------|
| 登录 | 管理员账号登录 |
| 网点管理 | 充电站CRUD、高德地图标注 |
| 充电桩管理 | 充电桩信息、状态监控、功率设置 |
| 用户管理 | 用户列表、状态管理 |
| 订单管理 | 订单查询、收入报表、ECharts统计 |
| 故障反馈 | 故障查看、处理分配 |
| 公告管理 | 发布、编辑、删除 |
| 系统管理 | AI客服配置、登录日志 |

## API 接口

### 管理端接口

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | /api/admin/login | 管理员登录 |
| GET | /api/admin/station/list | 站点列表 |
| POST | /api/admin/station | 新增站点 |
| PUT | /api/admin/station | 编辑站点 |
| DELETE | /api/admin/station/{id} | 删除站点 |
| GET | /api/admin/charger/list | 充电桩列表 |
| GET | /api/admin/user/list | 用户列表 |
| GET | /api/admin/order/list | 订单列表 |
| GET | /api/admin/order/statistics | 订单统计 |
| GET | /api/admin/feedback/list | 反馈列表 |
| GET | /api/admin/notice/list | 公告列表 |
| GET | /api/admin/login-log/list | 登录日志 |
| GET | /api/admin/system/ai-config | AI配置 |

### 小程序端接口

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | /api/miniapp/user/login | 用户登录 |
| POST | /api/miniapp/user/register | 用户注册 |
| GET | /api/miniapp/station/list | 充电站列表 |
| GET | /api/miniapp/station/{id} | 站点详情 |
| GET | /api/miniapp/station/nearby | 附近站点 |
| GET | /api/miniapp/charger/{id} | 充电桩详情 |
| POST | /api/miniapp/order/start | 开始充电 |
| POST | /api/miniapp/order/stop | 停止充电 |
| GET | /api/miniapp/order/list | 我的订单 |
| POST | /api/miniapp/recharge | 账户充值 |
| POST | /api/miniapp/feedback | 提交反馈 |
| GET | /api/miniapp/notice/list | 公告列表 |
| POST | /api/miniapp/ai/chat | AI对话 |

## 数据库表

| 表名 | 说明 |
|------|------|
| t_user | 用户表 |
| t_admin | 管理员表 |
| t_station | 充电站表 |
| t_charger | 充电桩表 |
| t_gun | 充电枪表 |
| t_order | 订单表 |
| t_recharge_record | 充值记录表 |
| t_feedback | 故障反馈表 |
| t_notice | 公告表 |
| t_login_log | 登录日志表 |
| t_ai_chat_log | AI对话记录表 |

## 部署上线

### 后端部署

```bash
mvn clean package -DskipTests
java -jar target/charging-station-server-1.0.0.jar --spring.profiles.active=prod
```

### 管理端部署

```bash
npm run build
# 将 dist 目录部署到 Nginx
```

### 小程序发布

1. 修改 `app.js` 中的 `baseUrl` 为正式服务器地址
2. 微信开发者工具点击「上传」
3. 微信公众平台提交审核
4. 审核通过后发布
