import { readonly, shallowRef, computed } from 'vue'
import { get, post } from '../utils/apiClient'
import type { ApiResult } from '../types/api'

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

export interface MailEntry {
  id: string
  tag: 'SYSTEM' | 'TIPS' | 'EMOTION' | 'NOTICE' | string
  subject: string
  excerpt: string
  isRead: boolean
  createdAt: string
  date: string
}

/* ------------------------------------------------------------------ */
/*  Tag → display helpers                                              */
/* ------------------------------------------------------------------ */

export const MAIL_TAG_META: Record<string, { label: string; color: string }> = {
  SYSTEM:  { label: '系统', color: '#6b7280' },
  TIPS:    { label: '小贴士', color: '#8b5cf6' },
  EMOTION: { label: '情绪', color: '#ec4899' },
  NOTICE:  { label: '通知', color: '#f59e0b' },
}

export function getTagMeta(tag: string): { label: string; color: string } {
  return MAIL_TAG_META[tag] ?? { label: tag, color: '#6b7280' }
}

/* ------------------------------------------------------------------ */
/*  Composable (singleton-friendly, mirrors usePetConfigState pattern) */
/* ------------------------------------------------------------------ */

const mails = shallowRef<MailEntry[]>([])
const isLoading = shallowRef(false)
const loadError = shallowRef('')

const unreadCount = computed(() => mails.value.filter((m) => !m.isRead).length)

async function fetchMails(): Promise<void> {
  isLoading.value = true
  loadError.value = ''
  try {
    const res = await get<ApiResult<MailEntry[]>>('/api/mails')
    if (res.code === 200 && Array.isArray(res.data)) {
      mails.value = res.data
    } else {
      loadError.value = res.message || '加载信件失败'
    }
  } catch (e: unknown) {
    loadError.value = e instanceof Error ? e.message : String(e)
  } finally {
    isLoading.value = false
  }
}

async function markAsRead(mailId: string): Promise<boolean> {
  try {
    const res = await post<ApiResult<null>>(`/api/mails/${encodeURIComponent(mailId)}/read`)
    if (res.code === 200) {
      // Optimistically update local state
      mails.value = mails.value.map((m) =>
        m.id === mailId ? { ...m, isRead: true } : m,
      )
      return true
    }
  } catch {
    /* swallow — individual read is non-critical */
  }
  return false
}

async function markAllAsRead(): Promise<number> {
  try {
    const res = await post<ApiResult<null>>('/api/mails/read-all')
    if (res.code === 200) {
      const unreadBefore = unreadCount.value
      mails.value = mails.value.map((m) => ({ ...m, isRead: true }))
      return unreadBefore
    }
  } catch {
    /* swallow */
  }
  return 0
}

export function usePetMailbox() {
  return {
    mails: readonly(mails),
    isLoading: readonly(isLoading),
    loadError: readonly(loadError),
    unreadCount,
    fetchMails,
    markAsRead,
    markAllAsRead,
  }
}
