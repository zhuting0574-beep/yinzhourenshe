const { request } = require('../../utils/request')

function normalizeBanners(banners) {
  if (!Array.isArray(banners)) return []
  return banners
    .filter((banner) => banner && (banner.image_url || banner.imageUrl) && (banner.activity_id || banner.activityId))
    .map((banner) => ({
      ...banner,
      imageUrl: banner.image_url || banner.imageUrl,
      activityId: banner.activity_id || banner.activityId,
    }))
}

function normalizeQuestionnaire(questionnaire) {
  if (!questionnaire) return null
  return {
    ...questionnaire,
    popupImageUrl: questionnaire.popup_image_url || questionnaire.popupImageUrl || '',
    popupTitle: questionnaire.popup_title || questionnaire.popupTitle || '',
    headerTitle: questionnaire.header_title || questionnaire.headerTitle || '',
  }
}

Page({
  data: {
    banners: [],
    activities: [],
    points: 0,
    about: [],
    questionnaire: null,
    loading: true,
    showAbout: false,
    aboutTitle: '关于我们',
    aboutImageUrl: '',
    aboutContent: '',
    showSurvey: false,
    userName: '',
  },
  onShow() {
    const tab = this.getTabBar && this.getTabBar()
    if (tab) tab.setData({ selected: 0 })
    const user = getApp().globalData.user
    request('/mini/home')
      .then((d) => {
        const questionnaire = normalizeQuestionnaire(d.questionnaire)
        this.setData({
          ...d,
          banners: normalizeBanners(d.banners),
          questionnaire,
          aboutTitle: d.about && d.about[0] && d.about[0].title ? d.about[0].title : '关于我们',
          aboutImageUrl: d.about && d.about[0] ? (d.about[0].image_url || d.about[0].imageUrl || '') : '',
          aboutContent: d.about && d.about[0] && d.about[0].content ? d.about[0].content : '',
          loading: false,
          userName: (user && user.nickname) || '',
          showSurvey: !!(questionnaire && !questionnaire.completedThisMonth),
        })
      })
      .catch(() => this.setData({ loading: false, banners: [], questionnaire: null, aboutImageUrl: '', showSurvey: false, userName: (user && user.nickname) || '' }))
  },
  openAbout() {
    this.setData({ showAbout: true })
  },
  closeAbout() {
    this.setData({ showAbout: false })
  },
  openBanner(e) {
    const activityId = e.currentTarget.dataset.activityId
    if (!activityId) return
    wx.navigateTo({ url: `/pages/activity-detail/activity-detail?id=${encodeURIComponent(activityId)}` })
  },
  closeSurvey() {
    this.setData({ showSurvey: false })
  },
  goSurvey() {
    wx.navigateTo({ url: '/pages/questionnaire/questionnaire' })
  },
  noop() {},
})
