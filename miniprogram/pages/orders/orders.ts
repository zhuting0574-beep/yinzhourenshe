import {request} from '../../utils/request'
Page({data:{items:[]},onLoad(){request<any>('/mini/orders').then((r:any)=>this.setData({items:r.data||[]}))}})
