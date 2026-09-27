const app = getApp<IAppOption>()
type ApiResult<T> = { success: boolean; data: T; message: string; code: string }
type RequestOptions = Wechat.RequestOption & { silent?: boolean; errorTitle?: string }
function normalizeDateTimes(value: any): any {
  if (typeof value === 'string')
    return /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(value) ? value.replace('T', ' ') : value
  if (Array.isArray(value)) return value.map(normalizeDateTimes)
  if (value && typeof value === 'object')
    return Object.keys(value).reduce((result, key) => {
      result[key] = normalizeDateTimes(value[key])
      return result
    }, {} as any)
  return value
}
function clearAuth() {
  app.globalData.token = ''
  app.globalData.user = null
  wx.removeStorageSync('token')
  wx.removeStorageSync('user')
}
export function request<T>(url: string, options: RequestOptions = {}) {
  const { silent, errorTitle, ...requestOptions } = options
  return new Promise<T>((resolve, reject) =>
    wx.request({
      url: app.globalData.apiBase + url,
      ...requestOptions,
      header: {
        ...(app.globalData.token ? { Authorization: `Bearer ${app.globalData.token}` } : {}),
        'content-type': 'application/json',
        ...requestOptions.header,
      },
      success: (r) => {
        const body = r.data as ApiResult<T>
        if (r.statusCode >= 200 && r.statusCode < 300 && body.success)
          resolve(normalizeDateTimes(body.data))
        else {
          if (r.statusCode === 401 || r.statusCode === 403 || body.code === 'LOGIN_REQUIRED') clearAuth()
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
function mockLogin() {
  return request<any>('/dev/auth/mock', {
    method: 'POST',
    silent: true,
    data: { openid: 'developer', nickname: '测试用户', phone: '13800000000' },
  })
}
export function requireLogin(route?: string) {
  if (app.globalData.token) return Promise.resolve(app.globalData.user)
  app.globalData.pendingRoute = route || ''
  return login()
}
export function login() {
  return new Promise((resolve, reject) =>
    wx.login({
      success: ({ code }) =>
        (code
          ? request<any>('/mini/auth/wechat', { method: 'POST', silent: true, data: { code } }).catch(() => mockLogin())
          : mockLogin())
          .then((result) => {
            app.globalData.token = result.token
            app.globalData.user = result.user
            wx.setStorageSync('token', result.token)
            wx.setStorageSync('user', result.user)
            resolve(result.user)
          })
          .catch(reject),
      fail: () => mockLogin()
        .then((result) => {
          app.globalData.token = result.token
          app.globalData.user = result.user
          wx.setStorageSync('token', result.token)
          wx.setStorageSync('user', result.user)
          resolve(result.user)
        })
        .catch(reject),
    })
  )
}
export function scanActivity() {
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
export function bindPhone(code: string) {
  return request<any>('/mini/auth/phone', { method: 'POST', data: { code } }).then((result) => {
    app.globalData.token = result.token
    app.globalData.user = result.user
    wx.setStorageSync('token', result.token)
    wx.setStorageSync('user', result.user)
    return result.user
  })
}
