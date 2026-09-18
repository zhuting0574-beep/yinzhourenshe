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
function request(url, options = {}) {
  return new Promise((resolve, reject) =>
    wx.request({
      url: app.globalData.apiBase + url,
      ...options,
      header: {
        ...(app.globalData.token ? { Authorization: `Bearer ${app.globalData.token}` } : {}),
        'content-type': 'application/json',
        ...(options.header || {}),
      },
      success: (r) => {
        const body = r.data || {}
        if (r.statusCode >= 200 && r.statusCode < 300 && body.success)
          resolve(normalizeDateTimes(body.data))
        else {
          if (r.statusCode === 401 || body.code === 'LOGIN_REQUIRED') app.globalData.token = ''
          wx.showToast({ title: body.message || '请求失败', icon: 'none' })
          reject(body)
        }
      },
      fail: (e) => {
        wx.showToast({ title: '网络连接失败', icon: 'none' })
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
        request('/mini/auth/wechat', { method: 'POST', data: { code } })
          .catch(() =>
            request('/dev/auth/mock', {
              method: 'POST',
              data: { openid: 'developer', nickname: '测试用户', phone: '13800000000' },
            })
          )
          .then((result) => {
            app.globalData.token = result.token
            app.globalData.user = result.user
            wx.setStorageSync('token', result.token)
            wx.setStorageSync('user', result.user)
            resolve(result.user)
          })
          .catch(reject),
      fail: reject,
    })
  )
}
function scanActivity() {
  return requireLogin().then(
    () =>
      new Promise((resolve, reject) =>
        wx.scanCode({
          onlyFromCamera: false,
          success: (r) =>
            request('/mini/activities/verify', { method: 'POST', data: { token: r.result } })
              .then((data) => {
                wx.showModal({
                  title: '核销成功',
                  content: '活动已签到，获得100积分',
                  showCancel: false,
                })
                resolve(data)
              })
              .catch(reject),
          fail: reject,
        })
      )
  )
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
module.exports = { request, requireLogin, login, scanActivity, bindPhone }
