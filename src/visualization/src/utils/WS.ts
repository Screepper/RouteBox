export interface WebSocketOptions {
  url: string;
  protocols?: string | string[];
  autoReconnect?: boolean;
  reconnectInterval?: number;
  heartbeatInterval?: number;
  heartbeatMsg?: string;
  onOpen?: (event: Event) => void;
  onMessage?: (event: MessageEvent) => void;
  onClose?: (event: CloseEvent) => void;
  onError?: (event: Event) => void;
}

export interface WebSocketState {
  isConnected: boolean;
  readyState: number;
}

class WebSocketUtil {
  private ws: WebSocket | null = null;
  private isConnected = false;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private heartbeatTimer: ReturnType<typeof setTimeout> | null = null;
  private options: WebSocketOptions;

  constructor(options: WebSocketOptions) {
    this.options = {
      protocols: [],
      autoReconnect: true,
      reconnectInterval: 3000,
      heartbeatInterval: 120000,
      heartbeatMsg: "ping",
      ...options,
    };
    this.init();
  }

  private init(): void {
    try {
      this.ws = this.options.protocols?.length
        ? new WebSocket(this.options.url, this.options.protocols)
        : new WebSocket(this.options.url);

      this.setupEventListeners();
    } catch (error) {
      this.reconnect();
    }
  }

  private setupEventListeners(): void {
    if (!this.ws) return;

    this.ws.onopen = (event) => {
      this.isConnected = true;
      this.options.onOpen?.(event);
      this.startHeartbeat();

      this.clearReconnectTimer();
    };

    this.ws.onmessage = (event) => {
      this.options.onMessage?.(event);

      if (event.data === "pong") {
        this.resetHeartbeat();
      }
    };

    this.ws.onclose = (event) => {
      this.isConnected = false;
      this.options.onClose?.(event);
      this.stopHeartbeat();

      if (this.options.autoReconnect) {
        this.reconnect();
      }
    };

    this.ws.onerror = (event) => {
      this.options.onError?.(event);
    };
  }

  private startHeartbeat(): void {
    if (!this.options.heartbeatInterval || this.heartbeatTimer) return;

    this.heartbeatTimer = setInterval(() => {
      if (this.isConnected && this.ws?.readyState === WebSocket.OPEN) {
        this.send(this.options.heartbeatMsg!);
      }
    }, this.options.heartbeatInterval);
  }

  private stopHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  private resetHeartbeat(): void {
    this.stopHeartbeat();
    this.startHeartbeat();
  }

  private reconnect(): void {
    if (this.reconnectTimer || !this.options.autoReconnect) return;

    this.reconnectTimer = setTimeout(() => {
      console.log("Reconnecting WebSocket...");
      this.init();
    }, this.options.reconnectInterval);
  }

  private clearReconnectTimer(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  public send(data: string | ArrayBuffer | Blob | ArrayBufferView): void {
    if (!this.isConnected || this.ws?.readyState !== WebSocket.OPEN) return;

    this.ws.send(data);
  }

  public close(code?: number, reason?: string): void {
    this.options.autoReconnect = false;
    this.clearReconnectTimer();
    this.stopHeartbeat();

    if (
      this.ws?.readyState !== WebSocket.CLOSED &&
      this.ws?.readyState !== WebSocket.CLOSING
    ) {
      this.ws?.close(code, reason);
    }
  }

  public getState(): WebSocketState {
    return {
      isConnected: this.isConnected,
      readyState: this.ws?.readyState ?? WebSocket.CLOSED,
    };
  }
}

export default function WS(options: WebSocketOptions): WebSocketUtil {
  return new WebSocketUtil(options);
}
