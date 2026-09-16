import axios from 'axios'
const api=axios.create({baseURL:'/api'})
api.interceptors.request.use(c=>{const token=localStorage.getItem('adminToken');if(token)c.headers.Authorization=`Bearer ${token}`;return c})
api.interceptors.response.use(r=>{if(r.data?.success===false)return Promise.reject(new Error(r.data.message));return r.data?.data??r.data},e=>{if(e.response?.status===401){localStorage.removeItem('adminToken');location.href='/login'}return Promise.reject(new Error(e.response?.data?.message||e.message||'请求失败'))})
export default api
