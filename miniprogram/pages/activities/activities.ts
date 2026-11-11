import { request, scanActivity } from '../../utils/request'
Page({
  data: { items: [] as any[], displayItems: [] as any[], loading: true, endedLoading: false, endedHasMore: true, endedPage: 1, keyword: '' },
  onShow() {
    const tab = this.getTabBar && this.getTabBar()
    if (tab) tab.setData({ selected: 1 })
    this.load()
  },
  onPullDownRefresh() {
    this.load().finally(wx.stopPullDownRefresh)
  },
  load() {
    this.setData({ loading: true, endedLoading: false, endedHasMore: true, endedPage: 1, items: [], displayItems: [] })
    return request<any[]>('/mini/activities')
      .then((items) => { this.setData({ items: items || [], loading: false }); return this.loadEnded(1, items || []) })
      .catch(() => this.setData({ loading: false }))
  },
  loadEnded(page: number, currentItems: any[] = this.data.items) {
    if (this.data.endedLoading || (page > 1 && !this.data.endedHasMore)) return Promise.resolve()
    this.setData({ endedLoading: true })
    return request<any>(`/mini/activities/ended?page=${page}`)
      .then((result) => {
        const known = new Set(currentItems.map((item: any) => String(item.id)))
        const hasEnded = currentItems.some((item: any) => item.ended)
        const ended = (result.items || []).filter((item: any) => !known.has(String(item.id))).map((item: any, index: number) => ({ ...item, ended: true, endedSectionStart: !hasEnded && index === 0 }))
        const items = [...currentItems, ...ended]
        this.setData({ items, endedPage: page, endedHasMore: !!result.hasMore, endedLoading: false })
        this.applySearch(items, this.data.keyword)
      })
      .catch(() => this.setData({ endedLoading: false }))
  },
  onReachBottom() {
    if (this.data.endedHasMore && !this.data.endedLoading) this.loadEnded(this.data.endedPage + 1)
  },
  applySearch(items: any[], keyword: string) {
    this.setData({ displayItems: keyword ? items.filter((item: any) => String(item.title || '').toLowerCase().includes(keyword)) : items })
  },
  search(e: any) {
    const keyword = String(e.detail.value || '')
      .trim()
      .toLowerCase()
    this.setData({ keyword })
    this.applySearch(this.data.items, keyword)
  },
  open(e: any) {
    wx.navigateTo({
      url: `/pages/activity-detail/activity-detail?id=${e.currentTarget.dataset.id}`,
    })
  },
  scan() {
    scanActivity()
  },
  onShareAppMessage() { return { title: '活动报名', path: '/pages/activities/activities' } },
  onShareTimeline() { return { title: '活动报名', query: '' } },
})
