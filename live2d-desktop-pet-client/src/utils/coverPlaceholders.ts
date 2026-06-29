/**
 * 封面图库 —— 当音乐文件不含内置封面时使用的 fallback 渐变背景。
 * 基于歌曲标题/歌手名生成一个确定性的渐变风格，保证同一首歌始终显示相同颜色。
 */

type GradientDef = [string, string]

const GRADIENTS: GradientDef[] = [
  ['#667eea', '#764ba2'],
  ['#f093fb', '#f5576c'],
  ['#4facfe', '#00f2fe'],
  ['#43e97b', '#38f9d7'],
  ['#fa709a', '#fee140'],
  ['#a18cd1', '#fbc2eb'],
  ['#fad0c4', '#ffd1ff'],
  ['#ffecd2', '#fcb69f'],
  ['#ff9a9e', '#fecfef'],
  ['#a1c4fd', '#c2e9fb'],
  ['#d4fc79', '#96e6a1'],
  ['#84fab0', '#8fd3f4'],
]

function hashString(str: string): number {
  let hash = 0
  for (let i = 0; i < str.length; i++) {
    hash = ((hash << 5) - hash + str.charCodeAt(i)) | 0
  }
  return Math.abs(hash)
}

export function getCoverPlaceholder(title: string, artist: string): string {
  const index = hashString(`${title}${artist}`) % GRADIENTS.length
  const [from, to] = GRADIENTS[index]
  return `linear-gradient(135deg, ${from}, ${to})`
}

const ALBUM_ICONS = ['🎵', '🎶', '🎼', '🎧', '🎤', '🎹', '🎸', '🥁', '🎺', '🎻', '💿', '📀']

export function getCoverIcon(title: string): string {
  const index = hashString(title) % ALBUM_ICONS.length
  return ALBUM_ICONS[index]
}
