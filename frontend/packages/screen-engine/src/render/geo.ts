import * as echarts from 'echarts'

/**
 * GeoJSON 注册表：ECharts 5 不再内置中国地图，
 * 地图类组件需先按 URL 拉取并注册，注册结果按 mapName+url 去重缓存。
 */
const pending = new Map<string, Promise<void>>()

export function geoAlreadyRegistered(mapName: string, url: string): boolean {
  return pending.has(`${mapName}::${url}`) && !!echarts.getMap(mapName)
}

export function loadGeoMap(mapName: string, url: string): Promise<void> {
  const key = `${mapName}::${url}`
  let task = pending.get(key)
  if (!task) {
    task = fetch(url)
      .then((res) => {
        if (!res.ok) throw new Error(`GeoJSON ${res.status}`)
        return res.json()
      })
      .then((json) => {
        echarts.registerMap(mapName, json as never)
      })
      .catch((e) => {
        // 失败后清除缓存，允许下次（如重试按钮）重新请求
        pending.delete(key)
        throw e
      })
    pending.set(key, task)
  }
  return task
}
