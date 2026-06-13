/**
 * 流式调用后端 Chat API
 */
export async function streamChat(
  message: string,
  history: { role: string; content: string }[],
  onChunk: (text: string) => void,
  onDone: () => void,
  onError: (err: string) => void,
  signal?: AbortSignal
): Promise<void> {
  try {
    const response = await fetch('/api/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        message,
        history: history.map((h) => ({ role: h.role, content: h.content })),
      }),
      signal,
    })

    if (!response.ok) {
      const text = await response.text()
      throw new Error(text || `HTTP ${response.status}`)
    }

    const reader = response.body?.getReader()
    if (!reader) {
      throw new Error('无法读取响应流')
    }

    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })

      // 解析 SSE 事件
      const lines = buffer.split('\n')
      buffer = lines.pop() || '' // 保留未完成的行

      for (const line of lines) {
        if (line.startsWith('event:chunk')) {
          // 下一个 data 行会包含内容
          continue
        }
        if (line.startsWith('data:')) {
          const raw = line.slice(5).trim()
          if (!raw) continue
          if (raw === '"[DONE]"' || raw === '[DONE]') {
            onDone()
            return
          }
          // JSON 解码（后端 JSON 包裹以避免换行截断）
          try {
            const parsed = JSON.parse(raw)
            onChunk(typeof parsed === 'string' ? parsed : raw)
          } catch {
            onChunk(raw)
          }
        }
        if (line.startsWith('event:error')) {
          continue
        }
        if (line.startsWith('event:done')) {
          onDone()
          return
        }
      }
    }

    onDone()
  } catch (err: any) {
    if (err.name === 'AbortError') {
      return
    }
    onError(err.message || '请求失败')
  }
}
