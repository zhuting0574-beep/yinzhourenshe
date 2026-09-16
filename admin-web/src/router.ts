import {createRouter,createWebHistory} from 'vue-router'
import Login from './views/Login.vue'
import Workbench from './views/Workbench.vue'
const modules=['dashboard','users','activities','questionnaires','products','orders','points','content']
const router=createRouter({history:createWebHistory(),routes:[{path:'/login',component:Login},{path:'/',redirect:'/dashboard'},...modules.map(name=>({path:`/${name}`,component:Workbench,props:{module:name}}))]})
router.beforeEach(to=>to.path==='/login'||localStorage.getItem('adminToken')?true:'/login')
export default router
