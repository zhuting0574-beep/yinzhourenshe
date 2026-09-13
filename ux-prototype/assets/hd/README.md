# 高清 UI 切片与重绘说明

这些资源由用户提供的 UI 拼图按独立手机屏幕裁切，并按微信小程序 `@3x` 资源尺寸输出：

- 逻辑尺寸：264 x 574 px
- 高清尺寸：792 x 1722 px
- 命名：`页面名@3x.jpg`

页面资源：`home`、`activities`、`products`、`profile`、`home-modal`、`activity-detail`、`product-detail`、`checkin`。

注意：源截图本身为 264 x 574 px，`hd/` 下 JPG 是给开发和验收对照用的高清参考切片，并非最终页面底图。

## 页面落地规则

- 原型页面在 `../app.js` 的 `drawnPage()` 中以结构化 HTML 组织，在 `../../styles.css` 中使用 CSS 绘制布局、卡片、按钮和状态。
- 图标使用 `app.js` 的 `icon()` 内嵌 SVG 生成器，微信小程序落地时可拆为 `icon` 组件或替换为同名 SVG/PNG 资源。
- 逻辑画布按 264 x 574 px 设计，开发时使用 `rpx`：264 px 对应 750rpx 设计宽度，固定底部 TabBar 高度约 57 px（约 162rpx）。
- 页面主色：品牌蓝 `#1769E0`、强调粉 `#F05D9C`、活动橙 `#F36A25`、浅蓝背景 `#F2FAFF`；圆角主要为 8-10 px。
- `promo-banner`、`activity-cover`、`product-visual`、`brand-illustration` 等区域是可直接拆分为小程序组件的矢量绘制区域，不依赖模糊截图。

## 组件映射

| 原型区域 | 建议小程序组件 | 数据依赖 |
| --- | --- | --- |
| 首页横幅 | `promo-banner` | 首页展示内容/活动 |
| 三个快捷入口 | `quick-entry` | 固定路由 |
| 活动卡片 | `activity-card` | 活动列表接口 |
| 商品卡片 | `product-card` | 商品与库存接口 |
| 积分条 | `points-summary` | 用户积分接口 |
| 日历签到 | `checkin-calendar` | 每日签到接口 |
| 底部导航 | `tabbar` | 页面路由 |
| 详情底部操作栏 | `detail-action-bar` | 报名/兑换接口 |
