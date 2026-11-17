import axios from 'axios'
import { clearAdminSession, getAdminStorage } from './utils/adminStorage'

const api=axios.create({baseURL:import.meta.env.VITE_API_BASE||'/api'})
api.interceptors.request.use(c=>{const token=getAdminStorage('adminToken');if(token)c.headers.Authorization=`Bearer ${token}`;return c})
api.interceptors.response.use(r=>{if(r.data?.success===false)return Promise.reject(new Error(r.data.message));return r.data?.data??r.data},e=>{if(e.response?.status===401||e.response?.status===403){clearAdminSession();const loginPath=`${import.meta.env.BASE_URL}login`;if(location.pathname!==loginPath)location.href=loginPath}return Promise.reject(new Error(e.response?.data?.message||e.message||'请求失败'))})
export default api
