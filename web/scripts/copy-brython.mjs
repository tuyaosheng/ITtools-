// 把 Brython 运行时复制到 public/pyrunner，供浏览器端 Web Worker 用 importScripts 加载（不进 git）
import { copyFileSync, mkdirSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const src = resolve(root, 'node_modules/brython')
const dest = resolve(root, 'public/pyrunner')
mkdirSync(dest, { recursive: true })
for (const f of ['brython.min.js', 'brython_stdlib.js']) copyFileSync(resolve(src, f), resolve(dest, f))
console.log('[copy-brython] runtime copied to public/pyrunner')
