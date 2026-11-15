import { onBeforeUnmount, onMounted, ref } from 'vue'

/** 窄屏（手机）判断：宽度 ≤ 768px 时为 true，随窗口变化更新。 */
export function useIsMobile(max = 768) {
  const query = `(max-width: ${max}px)`
  const isMobile = ref(typeof window !== 'undefined' && window.matchMedia(query).matches)
  let mql: MediaQueryList | undefined
  const onChange = (e: MediaQueryListEvent) => (isMobile.value = e.matches)
  onMounted(() => {
    mql = window.matchMedia(query)
    isMobile.value = mql.matches
    mql.addEventListener('change', onChange)
  })
  onBeforeUnmount(() => mql?.removeEventListener('change', onChange))
  return isMobile
}
