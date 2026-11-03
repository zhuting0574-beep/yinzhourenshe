const app = getApp()
function normalizeDateTimes(value) {
  if (typeof value === 'string')
    return /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(value) ? value.replace('T', ' ') : value
  if (Array.isArray(value)) return value.map(normalizeDateTimes)
  if (value && typeof value === 'object')
    return Object.keys(value).reduce((result, key) => {
      result[key] = normalizeDateTimes(value[key])
      return result
    }, {})
  return value
}
function clearAuth() {
  app.globalData.token = ''
  app.globalData.user = null
  wx.removeStorageSync('token')
  wx.removeStorageSync('user')
}
function request(url, options = {}) {
  const { silent, errorTitle, ...requestOptions } = options
  return new Promise((resolve, reject) =>
    wx.request({
      url: app.globalData.apiBase + url,
      ...requestOptions,
      header: {
        ...(app.globalData.token ? { Authorization: `Bearer ${app.globalData.token}` } : {}),
        'content-type': 'application/json',
        ...(requestOptions.header || {}),
      },
      success: (r) => {
        const body = r.data || {}
        if (r.statusCode >= 200 && r.statusCode < 300 && body.success)
          resolve(normalizeDateTimes(body.data))
        else {
          if (r.statusCode === 401 || r.statusCode === 403 || body.code === 'LOGIN_REQUIRED' || (body.code === 'NOT_FOUND' && ['/mini/home', '/mini/profile'].includes(url))) clearAuth()
          if (errorTitle) console.warn(`${url} failed`, r.statusCode, body)
          if (!silent) wx.showToast({ title: errorTitle || body.message || '请求失败', icon: 'none' })
          reject({ ...body, statusCode: r.statusCode })
        }
      },
      fail: (e) => {
        console.error('[wx.request failed]', requestOptions.method || 'GET', app.globalData.apiBase + url, e)
        if (!silent) wx.showToast({ title: errorTitle || '网络连接失败', icon: 'none' })
        reject(e)
      },
    })
  )
}
function requireLogin(route) {
  if (app.globalData.token) return Promise.resolve(app.globalData.user)
  app.globalData.pendingRoute = route || ''
  return login()
}
function login() {
  return new Promise((resolve, reject) =>
    wx.login({
      success: ({ code }) =>
        (code
          ? request('/mini/auth/wechat', { method: 'POST', silent: true, data: { code } })
          : Promise.reject(new Error('微信登录失败，请重试')))
          .then((result) => {
            app.globalData.token = result.token
            app.globalData.user = result.user
            wx.setStorageSync('token', result.token)
            wx.setStorageSync('user', result.user)
            return request('/mini/profile', { silent: true }).catch(() => result.user)
          })
          .then((user) => {
            app.globalData.user = user
            wx.setStorageSync('user', user)
            resolve(user)
          })
          .catch(reject),
      fail: (error) => reject(error),
    })
  )
}
function scanActivity() {
  return requireLogin().then(
    () =>
      new Promise((resolve, reject) =>
        wx.scanCode({
          onlyFromCamera: false,
          success: (r) => {
            const token = r.result
            const sharedActivityId = activityIdFromShareLink(token)
            if (sharedActivityId) {
              wx.navigateTo({
                url: `/pages/activity-detail/activity-detail?id=${encodeURIComponent(sharedActivityId)}`,
                success: () => resolve({ activityId: sharedActivityId, navigated: true }),
                fail: reject,
              })
              return
            }
            request('/mini/activities/verify', { method: 'POST', data: { token } })
              .then((verified) => {
                if (!verified.requiresLocation) return verified
                return new Promise((locationResolve, locationReject) => wx.getLocation({
                  type: 'gcj02', isHighAccuracy: true, highAccuracyExpireTime: 5000,
                  success: locationResolve,
                  fail: (error) => { wx.showModal({ title: '无法获取位置', content: '该活动需要扫码并完成定位签到，请开启定位权限后重新扫码。', confirmText: '去设置', success: (x) => x.confirm && wx.openSetting() }); locationReject(error) }
                })).then((location) => request(`/mini/activities/${verified.activityId}/check-in/location`, { method: 'POST', data: { token, latitude: location.latitude, longitude: location.longitude, accuracy: location.accuracy || 0 } }))
              })
              .then((data) => { wx.showModal({ title: data && data.alreadyCheckedIn ? '已签到' : '签到成功', content: data && data.alreadyCheckedIn ? '该活动已经签到，不能重复领取积分' : `活动已签到，获得${data && data.points || 100}积分`, showCancel: false }); resolve(data) })
              .catch(reject)
          },
          fail: reject,
        })
      )
  )
}
function activityIdFromShareLink(value) {
  const match = String(value || '').match(/^https:\/\/(?:www\.)?yinlinpai\.com\/api\/mini\/activities\/(\d+)\/open(?:[?#].*)?$/i)
  return match ? match[1] : ''
}
function bindPhone(code) {
  return request('/mini/auth/phone', { method: 'POST', data: { code } }).then((result) => {
    app.globalData.token = result.token
    app.globalData.user = result.user
    wx.setStorageSync('token', result.token)
    wx.setStorageSync('user', result.user)
    return result.user
  })
}
function updateWechatProfile(nickname, avatarUrl) {
  return request('/mini/auth/profile', { method: 'POST', data: { nickname, avatarUrl }, silent: true }).then((result) => {
    app.globalData.token = result.token
    app.globalData.user = result.user
    wx.setStorageSync('token', result.token)
    wx.setStorageSync('user', result.user)
    return result.user
  })
}
module.exports = { request, requireLogin, login, scanActivity, bindPhone, updateWechatProfile }
