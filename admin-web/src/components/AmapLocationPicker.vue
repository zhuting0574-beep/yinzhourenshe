<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import AMapLoader from '@amap/amap-jsapi-loader'

const props = defineProps<{
  address?: string | null
  longitude?: number | null
  latitude?: number | null
}>()
const emit = defineEmits<{
  (event: 'update:longitude', value: number): void
  (event: 'update:latitude', value: number): void
}>()

declare global {
  interface Window {
    AMap?: any
    _AMapSecurityConfig?: { securityJsCode: string }
  }
}

const mapContainer = ref<HTMLElement>()
const loadingAddress = ref(false)
const mapError = ref('')
const amapKey = import.meta.env.VITE_AMAP_KEY?.trim() || ''
let map: any
let marker: any
let geocoder: any
let placeSearch: any
let amapPromise: Promise<any> | undefined

function validCoordinate(value: unknown) {
  return value !== null && value !== undefined && value !== '' && Number.isFinite(Number(value))
}

function loadAmap() {
  if (window.AMap) return Promise.resolve(window.AMap)
  if (amapPromise) return amapPromise
  if (!amapKey) return Promise.reject(new Error('请配置高德地图 Web JS API Key'))
  const securityCode = import.meta.env.VITE_AMAP_SECURITY_CODE?.trim()
  if (securityCode) window._AMapSecurityConfig = { securityJsCode: securityCode }
  amapPromise = AMapLoader.load({ key: amapKey, version: '2.0', plugins: ['AMap.Geocoder', 'AMap.PlaceSearch'] })
  return amapPromise
}

function setMarker(longitude: number, latitude: number, center = false) {
  if (!map || !window.AMap) return
  const position = [longitude, latitude]
  if (!marker) {
    marker = new window.AMap.Marker({ position, anchor: 'bottom-center' })
    map.add(marker)
  } else marker.setPosition(position)
  if (center) {
    map.setZoomAndCenter(16, position)
    requestAnimationFrame(() => map?.resize?.())
  }
}

function updateLocation(longitude: number, latitude: number, center = false) {
  const lng = Number(longitude.toFixed(7))
  const lat = Number(latitude.toFixed(7))
  emit('update:longitude', lng)
  emit('update:latitude', lat)
  setMarker(lng, lat, center)
}

async function locateAddress() {
  const address = props.address?.trim()
  if (!address) return ElMessage.warning('请先填写上方地址')
  if (!geocoder) return ElMessage.error(mapError.value || '地图尚未加载完成')
  loadingAddress.value = true
  const withTimeout = <T>(start: (resolve: (value: T) => void, reject: (reason?: any) => void) => void, ms: number, message: string) =>
    new Promise<T>((resolve, reject) => {
      const timer = window.setTimeout(() => reject(new Error(message)), ms)
      try {
        start(
          value => { window.clearTimeout(timer); resolve(value) },
          error => { window.clearTimeout(timer); reject(error) },
        )
      } catch (error) {
        window.clearTimeout(timer)
        reject(error)
      }
    })

  try {
    let location: any
    try {
      location = await withTimeout<any>((resolve, reject) => {
        geocoder.getLocation(address, (status: string, result: any) => {
          const found = result?.geocodes?.[0]?.location
          if (status === 'complete' && found) resolve(found)
          else reject(new Error('地址解析未找到匹配位置'))
        })
      }, 10000, '地址解析超时')
    } catch (geocodeError) {
      // Geocoding can miss place names such as universities; fall back to POI search.
      if (!placeSearch) throw geocodeError
      location = await withTimeout<any>((resolve, reject) => {
        placeSearch.search(address, (status: string, result: any) => {
          const found = result?.poiList?.pois?.[0]?.location
          if (status === 'complete' && found) resolve(found)
          else reject(new Error('地点搜索未找到匹配位置'))
        })
      }, 10000, '地点搜索超时')
    }

    const longitude = Number(typeof location?.getLng === 'function' ? location.getLng() : location?.lng)
    const latitude = Number(typeof location?.getLat === 'function' ? location.getLat() : location?.lat)
    if (!Number.isFinite(longitude) || !Number.isFinite(latitude)) throw new Error('地图服务返回了无效坐标')
    await nextTick()
    updateLocation(longitude, latitude, true)
    ElMessage.success('已根据地址定位，请在地图上确认位置')
  } catch (error: any) {
    console.error('[AmapLocationPicker] address lookup failed', error)
    const message = String(error?.message || '')
    if (message.includes('超时')) ElMessage.error('地图查询超时，请检查网络后重试')
    else if (message.includes('无效坐标')) ElMessage.error(message)
    else ElMessage.error('未找到该地址，请补充城市、区县和详细地址后重试')
  } finally {
    loadingAddress.value = false
  }
}

onMounted(async () => {
  try {
    const AMap = await loadAmap()
    const hasCoordinate = validCoordinate(props.longitude) && validCoordinate(props.latitude)
    const center = hasCoordinate ? [Number(props.longitude), Number(props.latitude)] : [121.55027, 29.87486]
    map = new AMap.Map(mapContainer.value, { zoom: hasCoordinate ? 16 : 11, center, resizeEnable: true })
    // Do not pin address resolution to Ningbo: activity venues may be in any city.
    geocoder = new AMap.Geocoder()
    placeSearch = new AMap.PlaceSearch({ city: '全国', pageSize: 10 })
    if (hasCoordinate) setMarker(center[0], center[1])
    map.on('click', (event: any) => updateLocation(Number(event.lnglat.getLng()), Number(event.lnglat.getLat())))
  } catch (error: any) {
    mapError.value = error?.message || '高德地图加载失败'
  }
})

watch(() => [props.longitude, props.latitude], ([longitude, latitude]) => {
  if (validCoordinate(longitude) && validCoordinate(latitude)) setMarker(Number(longitude), Number(latitude), true)
})

onBeforeUnmount(() => map?.destroy())
</script>

<template>
  <div class="location-picker">
    <div class="location-fields">
      <el-input-number :model-value="longitude" :precision="7" :step="0.0001" :controls="false" placeholder="经度" @update:model-value="value => value != null && emit('update:longitude', Number(value))" />
      <el-input-number :model-value="latitude" :precision="7" :step="0.0001" :controls="false" placeholder="纬度" @update:model-value="value => value != null && emit('update:latitude', Number(value))" />
      <el-button :loading="loadingAddress" :disabled="!amapKey" @click="locateAddress">根据地址定位</el-button>
    </div>
    <div v-show="!mapError" ref="mapContainer" class="amap-container"></div>
    <div v-if="mapError" class="map-error">{{ mapError }}</div>
    <div v-else class="map-hint">点击地图可调整签到位置</div>
  </div>
</template>

<style scoped>
.location-picker { width: 100%; }
.location-fields { display: grid; grid-template-columns: 1fr 1fr auto; gap: 8px; }
.location-fields .el-input-number { width: 100%; }
.amap-container { width: 100%; height: 320px; margin-top: 12px; border: 1px solid #dcdfe6; border-radius: 6px; overflow: hidden; }
.map-hint, .map-error { margin-top: 8px; color: #7c8e9d; font-size: 12px; }
.map-error { min-height: 72px; padding: 24px; border: 1px dashed #d8e0e8; border-radius: 6px; background: #f8fafc; text-align: center; }
@media (max-width: 720px) { .location-fields { grid-template-columns: 1fr 1fr; } .location-fields .el-button { grid-column: 1 / -1; } }
</style>
