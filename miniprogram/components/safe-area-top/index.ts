Component({
  data: {
    height: 52,
  },
  lifetimes: {
    attached() {
      let statusBarHeight = 28
      try {
        const systemInfo = typeof wx.getWindowInfo === 'function' ? wx.getWindowInfo() : wx.getSystemInfoSync()
        statusBarHeight = Number(systemInfo.statusBarHeight) || statusBarHeight
      } catch (error) {
        // Keep a conservative top inset if system information is unavailable.
      }
      this.setData({ height: statusBarHeight + 24 })
    },
  },
})
