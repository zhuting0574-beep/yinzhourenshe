const { request } = require('../../utils/request')
Page({
  data: { items: [], displayItems: [], loading: true },
  onShow() {
    const tab = this.getTabBar && this.getTabBar()
    if (tab) tab.setData({ selected: 1 })
    this.load()
  },
  onPullDownRefresh() {
    this.load().finally(wx.stopPullDownRefresh)
  },
  load() {
    return request('/mini/activities')
      .then((items) => this.setData({ items, displayItems: items, loading: false }))
      .catch(() => this.setData({ loading: false }))
  },
  search(e) {
    const keyword = String(e.detail.value || '')
      .trim()
      .toLowerCase()
    this.setData({
      displayItems: keyword
        ? this.data.items.filter((item) =>
            String(item.title || '')
              .toLowerCase()
              .includes(keyword)
          )
        : this.data.items,
    })
  },
  open(e) {
    wx.navigateTo({
      url: `/pages/activity-detail/activity-detail?id=${e.currentTarget.dataset.id}`,
    })
  },
})
