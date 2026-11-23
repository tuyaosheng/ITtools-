// 测试环境（jsdom）没有 window.matchMedia；手机适配的 useIsMobile 用到它
if (typeof window !== 'undefined' && !window.matchMedia) {
  window.matchMedia = (query: string) => ({
    matches: false, media: query, onchange: null,
    addListener: () => {}, removeListener: () => {},
    addEventListener: () => {}, removeEventListener: () => {}, dispatchEvent: () => false
  }) as unknown as MediaQueryList
}
