declare module 'sockjs-client' {
  export default class SockJS {
    constructor(url: string)
    onclose: ((event: unknown) => void) | null
  }
}

declare module 'sockjs-client/dist/sockjs.min.js' {
  export default class SockJS {
    constructor(url: string)
    onclose: ((event: unknown) => void) | null
  }
}
