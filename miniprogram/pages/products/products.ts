import { request, scanActivity } from '../../utils/request'
Page({
  data: { items: [], loading: true },
  onShow() {
    const tab = this.getTabBar && this.getTabBar()
    if (tab) tab.setData({ selected: 3 })
    request<any[]>('/mini/products')
      .then((items) => this.setData({ items, loading: false }))
      .catch(() => this.setData({ loading: false }))
  },
  open(e: any) {
    wx.navigateTo({ url: `/pages/product-detail/product-detail?id=${e.currentTarget.dataset.id}` })
  },
  scan() {
    scanActivity()
  },
})
