import { request } from '../../utils/request'
Page({
  data: {
    banners: [],
    activities: [],
    points: 0,
    about: [],
    questionnaire: null as any,
    loading: true,
    showSurvey: false,
    userName: '',
  },
  onShow() {
    const tab = this.getTabBar && this.getTabBar()
    if (tab) tab.setData({ selected: 0 })
    const user = getApp<IAppOption>().globalData.user as any
    request<any>('/mini/home')
      .then((d) =>
        this.setData({
          ...d,
          loading: false,
          userName: user?.nickname || '',
          showSurvey: !!(d.questionnaire && !d.questionnaire.completedThisMonth),
        })
      )
      .catch(() => this.setData({ loading: false, userName: user?.nickname || '' }))
  },
  openActivities() {
    wx.switchTab({ url: '/pages/activities/activities' })
  },
  closeSurvey() {
    this.setData({ showSurvey: false })
  },
  goSurvey() {
    wx.navigateTo({ url: '/pages/questionnaire/questionnaire' })
  },
  noop() {},
})
