import {request} from '../../utils/request'
Page({data:{questions:[],answers:{}},onLoad(){request<any>('/mini/questionnaires/active').then((r:any)=>this.setData({questions:r.data||[]}))},submit(){request('/mini/questionnaires/submit',{method:'POST',data:{answers:this.data.answers}}).then(()=>wx.showToast({title:'提交成功'}))}})
