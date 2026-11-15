import axios from 'axios'
import { clearAdminSession, getAdminStorage } from './utils/adminStorage'

function normalizeDateTimes(value:any):any {
  if (typeof value === 'string') {
    const match=value.replace('T',' ').match(/^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})/)
    return match?match[1]:value
  }
  if (Array.isArray(value)) return value.map(normalizeDateTimes)
  if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([key,item])=>[key,normalizeDateTimes(item)]))
  return value
}

const api=axios.create({baseURL:import.meta.env.VITE_API_BASE||'/api'})
api.interceptors.request.use(c=>{const token=getAdminStorage('adminToken');if(token)c.headers.Authorization=`Bearer ${token}`;return c})
api.interceptors.response.use(r=>{if(r.data?.success===false)return Promise.reject(new Error(r.data.message));return normalizeDateTimes(r.data?.data??r.data)},e=>{if(e.response?.status===401||e.response?.status===403){clearAdminSession();const loginPath=`${import.meta.env.BASE_URL}login`;if(location.pathname!==loginPath)location.href=loginPath}return Promise.reject(new Error(e.response?.data?.message||e.message||'请求失败'))})
export default api
