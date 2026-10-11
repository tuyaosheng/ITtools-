/* ITtools 浏览器端 Python 运行器（Web Worker + Brython）。
 * 跑在 Worker 里，学生代码写出死循环时主线程可以直接 terminate()，不会卡死页面。
 * 消息：{ id, code, stdin } → { id, type: 'out', kind: 'out'|'err', text } ... { id, type: 'done' }
 * 输入输出语义与服务端评测一致：input() 从“标准输入”逐行读取，提示语写到标准输出，不回显输入内容。 */
/* global importScripts, __BRYTHON__ */
let ready = false

const PRELUDE = `
import sys, builtins, io, traceback
from browser import self as _w

class _Writer:
    def __init__(self, kind):
        self.kind = kind
    def write(self, text):
        text = str(text)
        _w.pyOut(text, self.kind)
        return len(text)
    def flush(self):
        pass

sys.stdout = _Writer('out')
sys.stderr = _Writer('err')
sys.stdin = io.StringIO(str(_w.pyStdin))

def _input(prompt=''):
    if prompt:
        sys.stdout.write(str(prompt))
    line = sys.stdin.readline()
    if line == '':
        raise EOFError('EOF when reading a line（标准输入已读完）')
    return line[:-1] if line.endswith('\\n') else line

builtins.input = _input
try:
    exec(compile(str(_w.pyCode), 'main.py', 'exec'), {'__name__': '__main__'})
except SystemExit:
    pass
except BaseException as _e:
    # 跳过运行器自身的栈帧，只显示学生代码 main.py 中的位置
    _tb = _e.__traceback__.tb_next if _e.__traceback__ else None
    traceback.print_exception(type(_e), _e, _tb)
`

self.onmessage = (e) => {
  const { id, code, stdin } = e.data
  if (!ready) {
    try {
      importScripts('brython.min.js', 'brython_stdlib.js')
      ready = true
    } catch (err) {
      self.postMessage({ id, type: 'out', kind: 'err', text: '加载 Python 运行环境失败：' + err })
      self.postMessage({ id, type: 'done' })
      return
    }
  }
  self.pyCode = code || ''
  self.pyStdin = stdin || ''
  self.pyOut = (text, kind) => self.postMessage({ id, type: 'out', kind, text })
  try {
    __BRYTHON__.runPythonSource(PRELUDE, { id: 'run_' + id })
  } catch (err) {
    self.postMessage({ id, type: 'out', kind: 'err', text: String((err && err.message) || err) + '\n' })
  }
  self.postMessage({ id, type: 'done' })
}
