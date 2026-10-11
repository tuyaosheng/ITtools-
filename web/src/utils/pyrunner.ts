/**
 * 浏览器端 Python 运行（public/pyrunner/worker.js，基于 Brython）。
 * 仅用于学生调试；正式评分由服务端沙箱完成（结果以服务端为准）。
 * Worker 只在首次运行时加载约 6MB 运行时，此后复用；超时则 terminate 并在下次运行时重建。
 */
export interface RunResult {
  status: 'OK' | 'TLE'
  ms: number
}

let worker: Worker | null = null
let seq = 0

function getWorker(): Worker {
  if (!worker) worker = new Worker('/pyrunner/worker.js')
  return worker
}

/** 预加载运行时（进入编程页面时调用，避免首次运行等待） */
export function warmUp(): void {
  run('pass', '', () => undefined, 30000).catch(() => undefined)
}

export function run(code: string, stdin: string, onOutput: (text: string, kind: 'out' | 'err') => void, timeoutMs = 10000): Promise<RunResult> {
  const w = getWorker()
  const id = ++seq
  const started = performance.now()
  return new Promise(resolve => {
    const timer = window.setTimeout(() => {
      w.removeEventListener('message', onMsg)
      w.terminate()
      if (worker === w) worker = null
      resolve({ status: 'TLE', ms: timeoutMs })
    }, timeoutMs)
    function onMsg(e: MessageEvent) {
      if (e.data?.id !== id) return
      if (e.data.type === 'out') {
        onOutput(e.data.text, e.data.kind)
      } else if (e.data.type === 'done') {
        window.clearTimeout(timer)
        w.removeEventListener('message', onMsg)
        resolve({ status: 'OK', ms: Math.round(performance.now() - started) })
      }
    }
    w.addEventListener('message', onMsg)
    w.postMessage({ id, code, stdin })
  })
}

/** 与服务端一致的输出比对：统一换行，忽略行尾空白与末尾空行 */
export function normalizeOutput(s: string): string {
  return s.replace(/\r\n?/g, '\n').split('\n').map(l => l.replace(/\s+$/, '')).join('\n').replace(/\n+$/, '')
}
