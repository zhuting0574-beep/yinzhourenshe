# 鄞领π UX 高保真可点击原型

本目录是依据《微信小程序及管理端功能说明书 V1.0》和参考 UI 截图制作的独立原型切片，不依赖框架、不包含业务接口，也不会修改其他工程目录。它用于在正式开发前确认视觉、页面层级、交互路径和状态设计。

## 本地运行

在本目录启动静态服务器即可：

```bash
cd ux-prototype
python3 -m http.server 8080
```

打开 `http://localhost:8080`。也可以直接双击 `index.html`，但静态服务器更接近微信开发者工具和管理端的加载方式。

## 页面与交互

- **微信小程序**：首页、活动列表、活动详情、每日签到、问卷填写、积分兑换、兑换记录、个人中心。左侧目录、手机底部导航、卡片均可点击切换。
- **管理端**：数据概览、用户管理、活动管理、问卷管理、商品与库存、兑换订单、积分明细、展示内容。左侧菜单可切换工作台页面。
- 首页展示登录后置顶问卷弹窗；“我要报名”“立即签到”“提交问卷”按钮会切换到已完成状态；订单、活动、问卷和库存页包含说明书要求的主要业务状态。

## 视觉切片规范

- 主色：`#1769E0`（行动按钮、导航高亮）；强调色：`#EF649E`（积分兑换入口、问卷强调）；浅色背景：`#F1FAFF`；正文：`#17304F`。
- 字体：系统无衬线字体，优先苹方 / PingFang SC；页面标题 20–22px，正文 12–14px，辅助文字 10–11px。
- 小程序画布：375×760px，顶部状态栏 29px，底部导航 61px；页面卡片圆角 12–14px，操作按钮圆角 22px。
- 管理端：238px 侧边栏，70px 顶栏；内容卡片圆角 10–11px，表格行高约 42px；使用蓝色、绿色、橙色、灰色状态标签表达发布、可用、待处理、结束状态。

## 组件 / 工程映射

- 小程序：`hero`（活动横幅）→ `quick-grid`（活动/兑换/签到入口）→ `activity-card`、`product-card`、`order-card`、`question`；底部 `tabbar` 对应 `pages/home`、`pages/activities`、`pages/products`、`pages/profile`。
- 管理端：`admin-sidebar`、`admin-topbar`、`stat`、`panel`、`data-table`、`tag`、`event-card`。后续 Vue + Element Plus 应分别映射为 `ElMenu`、`ElCard`、`ElTable`、`ElTag`、`ElForm` 和 `ElDialog/ElDrawer`。

## 页面状态与真实数据依赖

原型中的数字和文案仅用于视觉切片，不代表接口 mock。正式工程需由后端真实 API 替换：

- 首页：`GET /api/mini/home`（横幅、置顶问卷、关于我们）
- 活动：`GET /api/mini/activities`、`GET /api/mini/activities/{id}`、报名和签到接口
- 签到：`GET/POST /api/mini/check-ins`（北京时间自然日和 500 米校验由后端保证）
- 问卷：`GET /api/mini/questionnaires/{id}`、`POST /api/mini/questionnaires/{id}/submit`
- 商品和订单：`GET /api/mini/products`、`POST /api/mini/orders`、`GET /api/mini/orders`
- 个人中心：`GET /api/mini/profile`、积分明细接口
- 管理端页面：分别接入 `/api/admin/users`、`activities`、`questionnaires`、`products`、`orders`、`points`、`content`。

## 参考资源

`assets/reference-ui.jpg` 是用户提供的 UI 拼图原图，保留用于后续开发对照。原型未直接依赖外部图片服务，避免离线开发时资源失效。
