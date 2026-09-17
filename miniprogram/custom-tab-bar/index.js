const {scanActivity}=require('../utils/request')
Component({
  data:{selected:0,list:[
    {pagePath:'/pages/home/home',text:'首页',iconPath:'/assets/tabbar/home.png',selectedIconPath:'/assets/tabbar/home-active.png'},
    {pagePath:'/pages/activities/activities',text:'活动',iconPath:'/assets/tabbar/activities.png',selectedIconPath:'/assets/tabbar/activities-active.png'},
    {scan:true,text:'扫码'},
    {pagePath:'/pages/products/products',text:'兑换',iconPath:'/assets/tabbar/products.png',selectedIconPath:'/assets/tabbar/products-active.png'},
    {pagePath:'/pages/profile/profile',text:'我的',iconPath:'/assets/tabbar/profile.png',selectedIconPath:'/assets/tabbar/profile-active.png'}
  ]},
  lifetimes:{attached(){this.sync()}},
  pageLifetimes:{show(){this.sync()}},
  methods:{
    sync(){const pages=getCurrentPages();const route=pages.length?`/${pages[pages.length-1].route}`:'';const index=this.data.list.findIndex(item=>item.pagePath===route);if(index>=0)this.setData({selected:index})},
    switchTab(e){const index=e.currentTarget.dataset.index;const item=this.data.list[index];if(item.scan)return scanActivity();this.setData({selected:index});wx.switchTab({url:item.pagePath})}
  }
})
