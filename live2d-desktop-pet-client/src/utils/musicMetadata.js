export function readTrackTagsFromBytes(bytes) {
  return {
    ...readMp4Tags(bytes),
    ...readId3v2Tags(bytes),
    ...readId3v1Tags(bytes),
  }
}

export function formatTrackDuration(durationSeconds) {
  if (durationSeconds == null || !Number.isFinite(durationSeconds)) {
    return '--:--'
  }

  const totalSeconds = Math.max(0, Math.floor(durationSeconds))
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

function readId3v2Tags(bytes) {
  if (bytes.length < 10 || readAscii(bytes, 0, 3) !== 'ID3') {
    return {}
  }

  const tagSize = readSyncSafeInteger(bytes.subarray(6, 10))
  const version = bytes[3]
  const result = {}
  let offset = 10
  const limit = Math.min(bytes.length, 10 + tagSize)

  while (offset + 10 <= limit) {
    const frameId = readAscii(bytes, offset, 4)
    if (!frameId.trim()) {
      break
    }

    const frameSize = version === 4
      ? readSyncSafeInteger(bytes.subarray(offset + 4, offset + 8))
      : readBigEndianInteger(bytes.subarray(offset + 4, offset + 8))

    if (frameSize <= 0 || offset + 10 + frameSize > limit) {
      break
    }

    const frameData = bytes.subarray(offset + 10, offset + 10 + frameSize)
    const frameValue = normalizeTagValue(decodeTextFrame(frameData))

    if (frameId === 'APIC' || frameId === 'PIC') {
      const picture = decodePictureFrame(frameId, frameData)
      if (picture) {
        result.cover = picture
      }
    } else if (frameId === 'TIT2' && frameValue) {
      result.title = frameValue
    } else if (frameId === 'TPE1' && frameValue) {
      result.artist = frameValue
    } else if (frameId === 'TALB' && frameValue) {
      result.album = frameValue
    }

    offset += 10 + frameSize
  }

  return result
}

function readMp4Tags(bytes) {
  const ilst = findMp4Ilst(bytes)
  if (!ilst) {
    return {}
  }

  const result = {}
  let offset = 0

  while (offset + 8 <= ilst.length) {
    const atomSize = readBigEndianInteger(ilst.subarray(offset, offset + 4))
    const atomType = readAscii(ilst, offset + 4, 4)

    if (atomSize < 8 || offset + atomSize > ilst.length) {
      break
    }

    const atomData = ilst.subarray(offset + 8, offset + atomSize)
    const value = decodeMp4MetadataAtom(atomType, atomData)

    if (value) {
      if ((atomType === '©nam' || atomType === 'titl') && typeof value === 'string') {
        result.title = value
      } else if ((atomType === '©ART' || atomType === 'aART' || atomType === 'auth') && typeof value === 'string') {
        result.artist = value
      } else if ((atomType === '©alb' || atomType === 'albm') && typeof value === 'string') {
        result.album = value
      } else if (atomType === 'covr' && typeof value === 'object') {
        result.cover = value
      }
    }

    offset += atomSize
  }

  return result
}

function findMp4Ilst(bytes) {
  let offset = 0
  while (offset + 8 <= bytes.length) {
    const atomSize = readBigEndianInteger(bytes.subarray(offset, offset + 4))
    const atomType = readAscii(bytes, offset + 4, 4)

    if (atomSize < 8 || offset + atomSize > bytes.length) {
      break
    }

    if (atomType === 'moov' || atomType === 'udta') {
      const found = findMp4Ilst(bytes.subarray(offset + 8, offset + atomSize))
      if (found) {
        return found
      }
    } else if (atomType === 'meta') {
      const found = findMp4Ilst(bytes.subarray(offset + 12, offset + atomSize))
      if (found) {
        return found
      }
    } else if (atomType === 'ilst') {
      return bytes.subarray(offset + 8, offset + atomSize)
    }

    offset += atomSize
  }

  return null
}

function decodeMp4MetadataAtom(atomType, atomData) {
  let offset = 0
  while (offset + 8 <= atomData.length) {
    const childSize = readBigEndianInteger(atomData.subarray(offset, offset + 4))
    const childType = readAscii(atomData, offset + 4, 4)

    if (childSize < 8 || offset + childSize > atomData.length) {
      break
    }

    if (childType === 'data') {
      const payload = atomData.subarray(offset + 16, offset + childSize)
      if (atomType === 'covr') {
        const dataType = readBigEndianInteger(atomData.subarray(offset + 8, offset + 12))
        const mimeType = dataType === 14 ? 'image/png' : 'image/jpeg'
        return {
          mimeType,
          bytes: payload,
        }
      }

      return normalizeTagValue(new TextDecoder('utf-8').decode(payload).replace(/\0/g, ''))
    }

    offset += childSize
  }

  return null
}

function readId3v1Tags(bytes) {
  if (bytes.length < 128 || readAscii(bytes, bytes.length - 128, 3) !== 'TAG') {
    return {}
  }

  const base = bytes.length - 125
  const title = normalizeTagValue(decodeLatin1(bytes.subarray(base, base + 30)))
  const artist = normalizeTagValue(decodeLatin1(bytes.subarray(base + 30, base + 60)))
  const album = normalizeTagValue(decodeLatin1(bytes.subarray(base + 60, base + 90)))

  return {
    ...(title ? { title } : {}),
    ...(artist ? { artist } : {}),
    ...(album ? { album } : {}),
  }
}

function decodePictureFrame(frameId, frameData) {
  if (frameData.length < 4) {
    return null
  }

  const encoding = frameData[0]

  if (frameId === 'APIC') {
    let offset = 1
    const mimeEnd = frameData.indexOf(0, offset)
    if (mimeEnd === -1) {
      return null
    }

    const mimeType = decodeLatin1(frameData.subarray(offset, mimeEnd)).trim()
    offset = mimeEnd + 1
    offset += 1
    offset = skipEncodedText(frameData, offset, encoding)

    if (offset >= frameData.length) {
      return null
    }

    return {
      mimeType: mimeType || 'image/jpeg',
      bytes: frameData.subarray(offset),
    }
  }

  let offset = 1
  const format = decodeLatin1(frameData.subarray(offset, offset + 3)).trim().toUpperCase()
  offset += 3
  offset += 1
  offset = skipEncodedText(frameData, offset, encoding)

  if (offset >= frameData.length) {
    return null
  }

  const mimeType = format === 'PNG' ? 'image/png' : 'image/jpeg'
  return {
    mimeType,
    bytes: frameData.subarray(offset),
  }
}

function decodeTextFrame(frameData) {
  if (frameData.length === 0) {
    return ''
  }

  const encoding = frameData[0]
  const content = frameData.subarray(1)

  if (encoding === 0 || encoding === 3) {
    return new TextDecoder(encoding === 0 ? 'latin1' : 'utf-8').decode(content).replace(/\0/g, '')
  }

  if (encoding === 1 || encoding === 2) {
    const hasBom = content.length >= 2 && ((content[0] === 0xff && content[1] === 0xfe) || (content[0] === 0xfe && content[1] === 0xff))
    const decoder = new TextDecoder(hasBom && content[0] === 0xff ? 'utf-16le' : 'utf-16be')
    const textBytes = hasBom ? content.subarray(2) : content
    return decoder.decode(textBytes).replace(/\0/g, '')
  }

  return ''
}

function decodeLatin1(bytes) {
  return new TextDecoder('latin1').decode(bytes).replace(/\0/g, '')
}

function skipEncodedText(bytes, offset, encoding) {
  if (encoding === 0 || encoding === 3) {
    const end = bytes.indexOf(0, offset)
    return end === -1 ? bytes.length : end + 1
  }

  for (let index = offset; index + 1 < bytes.length; index += 2) {
    if (bytes[index] === 0 && bytes[index + 1] === 0) {
      return index + 2
    }
  }

  return bytes.length
}

function normalizeTagValue(value) {
  return typeof value === 'string' ? value.trim() : ''
}

function readAscii(bytes, start, length) {
  return String.fromCharCode(...bytes.subarray(start, start + length))
}

function readSyncSafeInteger(bytes) {
  return bytes.reduce((acc, byte) => (acc << 7) | (byte & 0x7f), 0)
}

function readBigEndianInteger(bytes) {
  return bytes.reduce((acc, byte) => (acc << 8) | byte, 0)
}
