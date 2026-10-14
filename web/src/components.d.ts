export {}

declare module 'vue' {
  export interface GlobalComponents {
    Hint: typeof import('./components/Hint.vue')['default']
  }
}
