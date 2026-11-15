import {createRouter,createWebHistory} from 'vue-router'
import Login from './views/Login.vue'
import Workbench from './views/Workbench.vue'
import {getAdminStorage} from './utils/adminStorage'
const modules=['dashboard','users','activities','questionnaires','products','orders','points','content','banners']
const router=createRouter({history:createWebHistory(import.meta.env.BASE_URL),routes:[{path:'/login',component:Login},{path:'/',redirect:'/dashboard'},...modules.map(name=>({path:`/${name}`,component:Workbench,props:{module:name}}))]})
router.beforeEach(to=>{if(to.path==='/login')return true;if(!getAdminStorage('adminToken'))return '/login';if(getAdminStorage('adminRole')==='ORDER_OPERATOR'&&to.path!=='/orders')return '/orders';return true})
export default router
