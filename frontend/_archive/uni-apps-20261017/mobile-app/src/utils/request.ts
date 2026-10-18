/**
 * uni 端通用请求封装：走网关 /api，统一解 R 响应。
 */

// #ifdef H5
// H5 走 Vite 代理（vite.config.ts /api → 网关 8080），规避跨域
export const BASE_URL = '/api'
// #endif
// #ifndef H5
export const BASE_URL = 'http://localhost:8080/api'
// #endif

const TOKEN_KEY = 'dataviz_access_token'

export function getToken(): string {
  return (uni.getStorageSync(TOKEN_KEY) as string) || ''
}

export function request<T>(method: 'GET' | 'POST' | 'PUT' | 'DELETE', url: string, data?: object): Promise<T> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: BASE_URL + url,
      method,
      data,
      header: {
        'Content-Type': 'application/json',
        Authorization: getToken() ? `Bearer ${getToken()}` : '',
      },
      timeout: 15000,
      success: (res) => {
        const body = res.data as { code?: number; message?: string; data?: T }
        if (res.statusCode === 200 && (body.code === 0 || body.code === 200)) {
          resolve(body.data as T)
        } else {
          reject(new Error(body.message || `请求失败(${res.statusCode})`))
        }
      },
      fail: () => reject(new Error('网络错误，请检查后端服务是否启动')),
    })
  })
}
