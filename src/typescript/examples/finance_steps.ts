export interface Tick {
  symbol: string;
  price: number;
}

export interface Features {
  r1: number;
  vol: number;
  symbol: string;
  price: number;
}

export interface Score {
  value: number;
}

export type OrderResponse = Accept | Reject;

export interface Accept {
  type: "accept";
  symbol: string;
  qty: number;
  price: number;
}

export interface Reject {
  type: "reject";
  reason: string;
}

export class FinanceSteps {
  static computeFeatures(tickValue: Tick): Features {
    const volatility = Math.abs(tickValue.price) * 0.01;
    return { r1: 0.0, vol: volatility, symbol: tickValue.symbol, price: tickValue.price };
  }

  static score(featuresValue: Features): Score {
    const scoreValue = Math.max(0.0, 1.0 - featuresValue.vol);
    return { value: scoreValue };
  }

  static decide(scoreValue: Score): OrderResponse {
    if (scoreValue.value >= 0.5) {
      return { type: "accept", symbol: "AAPL", qty: 10, price: 101.25 };
    } else {
      return { type: "reject", reason: "LowScore" };
    }
  }
}
