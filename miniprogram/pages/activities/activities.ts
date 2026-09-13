import {request} from '../../utils/request'
Page({data:{items:[],loading:true},onLoad(){request<any>('/mini/activities').then((r:any)=>this.setData({items:r.data||[],loading:false})).catch(()=>this.setData({loading:false}))}})
