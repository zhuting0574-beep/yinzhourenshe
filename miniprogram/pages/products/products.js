const {request}=require('../../utils/request')
Page({data:{items:[],loading:true},onShow(){const tab=this.getTabBar&&this.getTabBar();if(tab)tab.setData({selected:3});request('/mini/products').then(items=>this.setData({items,loading:false})).catch(()=>this.setData({loading:false}))},open(e){wx.navigateTo({url:`/pages/product-detail/product-detail?id=${e.currentTarget.dataset.id}`})}})
