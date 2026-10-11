import { Client, type IMessage } from '@stomp/stompjs'
import { onBeforeUnmount } from 'vue'

export interface LessonEvent {
  type: string
  data: any
}

/**
 * 订阅课堂实时事件（STOMP over WebSocket，同源 /ws，会话 cookie 鉴权）。
 * teacher=true 时同时订阅教师专用频道。组件卸载时自动断开；连接断开会自动重连。
 */
export function useLessonChannel(lessonId: number, teacher: boolean, onEvent: (e: LessonEvent) => void) {
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  const client = new Client({
    brokerURL: `${proto}://${location.host}/ws`,
    reconnectDelay: 3000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000
  })
  const handle = (m: IMessage) => {
    try {
      onEvent(JSON.parse(m.body))
    } catch {
      /* ignore malformed */
    }
  }
  client.onConnect = () => {
    client.subscribe(`/topic/lesson.${lessonId}`, handle)
    if (teacher) client.subscribe(`/topic/lesson.${lessonId}.teacher`, handle)
  }
  client.activate()
  onBeforeUnmount(() => { client.deactivate() })
  return client
}
