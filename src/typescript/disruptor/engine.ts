export class DisruptorEngine<T> {
  private name: string;
  private pipelineCallable: (payload: T) => unknown | Promise<unknown>;
  private capacity: number;
  private queue: Array<T>;
  private running: boolean;
  private pendingPublishes: Array<{ payload: T; resolve: () => void }>;

  constructor(name: string, pipelineCallable: (payload: T) => unknown | Promise<unknown>, capacity: number = 8192) {
    this.name = name;
    this.pipelineCallable = pipelineCallable;
    this.capacity = capacity;
    this.queue = [];
    this.running = true;
    this.pendingPublishes = [];
    this.runLoop();
  }

  private async runLoop(): Promise<void> {
    // Single-consumer loop
    while (this.running) {
      const nextItem = this.queue.shift();
      if (nextItem === undefined) {
        await new Promise<void>(resolve => setTimeout(resolve, 10));
        continue;
      }
      try {
        await this.pipelineCallable(nextItem);
      } finally {
        this.drainPendingPublishes();
      }
    }
  }

  private drainPendingPublishes(): void {
    while (this.queue.length < this.capacity && this.pendingPublishes.length > 0) {
      const next = this.pendingPublishes.shift();
      if (!next) {
        break;
      }
      this.queue.push(next.payload);
      next.resolve();
    }
  }

  async publish(payload: T): Promise<void> {
    if (!this.running) {
      throw new Error("engine stopped");
    }
    if (this.queue.length < this.capacity) {
      this.queue.push(payload);
      return;
    }
    // Backpressure: block (via promise) until space is available
    await new Promise<void>(resolve => {
      this.pendingPublishes.push({ payload, resolve });
    });
  }

  shutdown(): void {
    this.running = false;
  }

  close(): void {
    this.shutdown();
  }
}
