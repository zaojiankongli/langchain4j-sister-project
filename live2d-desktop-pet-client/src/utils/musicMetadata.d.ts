export interface ParsedTrackTags {
  title?: string
  artist?: string
  album?: string
  cover?: {
    mimeType: string
    bytes: Uint8Array
  }
}

export declare function readTrackTagsFromBytes(bytes: Uint8Array): ParsedTrackTags
export declare function formatTrackDuration(durationSeconds: number | null): string
