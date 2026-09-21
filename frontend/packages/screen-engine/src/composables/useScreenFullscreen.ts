/**
 * useScreenFullscreen - 全屏 API composable
 */
import { ref, computed } from 'vue'

export function useScreenFullscreen() {
  const isFullscreen = ref(false)

  const canFullscreen = computed(() => {
    return !!(
      document.fullscreenEnabled ||
      (document as unknown as Record<string, boolean>).webkitFullscreenEnabled ||
      (document as unknown as Record<string, boolean>).mozFullScreenEnabled ||
      (document as unknown as Record<string, boolean>).msFullscreenEnabled
    )
  })

  async function enterFullscreen(element?: HTMLElement): Promise<void> {
    const el = element || document.documentElement
    try {
      if (el.requestFullscreen) {
        await el.requestFullscreen()
      } else if ((el as unknown as Record<string, () => Promise<void>>).webkitRequestFullscreen) {
        await (el as unknown as Record<string, () => Promise<void>>).webkitRequestFullscreen()
      } else if ((el as unknown as Record<string, () => Promise<void>>).mozRequestFullScreen) {
        await (el as unknown as Record<string, () => Promise<void>>).mozRequestFullScreen()
      } else if ((el as unknown as Record<string, () => Promise<void>>).msRequestFullscreen) {
        await (el as unknown as Record<string, () => Promise<void>>).msRequestFullscreen()
      }
      isFullscreen.value = true
    } catch (err) {
      console.warn('Failed to enter fullscreen:', err)
    }
  }

  async function exitFullscreen(): Promise<void> {
    try {
      if (document.exitFullscreen) {
        await document.exitFullscreen()
      } else if ((document as unknown as Record<string, () => Promise<void>>).webkitExitFullscreen) {
        await (document as unknown as Record<string, () => Promise<void>>).webkitExitFullscreen()
      } else if ((document as unknown as Record<string, () => Promise<void>>).mozCancelFullScreen) {
        await (document as unknown as Record<string, () => Promise<void>>).mozCancelFullScreen()
      } else if ((document as unknown as Record<string, () => Promise<void>>).msExitFullscreen) {
        await (document as unknown as Record<string, () => Promise<void>>).msExitFullscreen()
      }
      isFullscreen.value = false
    } catch (err) {
      console.warn('Failed to exit fullscreen:', err)
    }
  }

  async function toggleFullscreen(element?: HTMLElement): Promise<void> {
    if (isFullscreen.value) {
      await exitFullscreen()
    } else {
      await enterFullscreen(element)
    }
  }

  function handleFullscreenChange(): void {
    isFullscreen.value = !!(
      document.fullscreenElement ||
      (document as unknown as Record<string, Element | null>).webkitFullscreenElement ||
      (document as unknown as Record<string, Element | null>).mozFullScreenElement ||
      (document as unknown as Record<string, Element | null>).msFullscreenElement
    )
  }

  if (typeof document !== 'undefined') {
    document.addEventListener('fullscreenchange', handleFullscreenChange)
    document.addEventListener('webkitfullscreenchange', handleFullscreenChange)
    document.addEventListener('mozfullscreenchange', handleFullscreenChange)
    document.addEventListener('MSFullscreenChange', handleFullscreenChange)
  }

  return {
    isFullscreen,
    canFullscreen,
    enterFullscreen,
    exitFullscreen,
    toggleFullscreen,
  }
}
