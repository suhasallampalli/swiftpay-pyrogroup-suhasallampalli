// SwiftPay gateway load test.
//
// Spec target: 250 TPS for a total of 1,000,000 transactions
//   => constant arrival rate of 250 req/s for 4000 s (~66.7 min).
//
// Each iteration POSTs a unique payment to the gateway. Sender / receiver are
// drawn at random from the load-user-* pool seeded by load-test/seed-accounts.sql.
//
// Run:
//   k6 run \
//     --summary-export=load-test/artifacts/k6-summary.json \
//     load-test/swiftpay-load.js
//
// Tunables via env:
//   GATEWAY_URL   (default http://localhost:8080)
//   RATE          (default 250)   requests per second
//   DURATION_S    (default 4000)  seconds  -> RATE * DURATION_S total requests
//   ACCOUNT_COUNT (default 10000) size of the seeded account pool

import http from 'k6/http';
import { check } from 'k6';

const GATEWAY = __ENV.GATEWAY_URL || 'http://localhost:8080';
const RATE = parseInt(__ENV.RATE || '250', 10);
const DURATION_S = parseInt(__ENV.DURATION_S || '4000', 10);
const ACCOUNT_COUNT = parseInt(__ENV.ACCOUNT_COUNT || '10000', 10);

export const options = {
  scenarios: {
    payments: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: `${DURATION_S}s`,
      preAllocatedVUs: 200,
      maxVUs: 800,
      gracefulStop: '60s',
    },
  },
  thresholds: {
    'http_req_failed{name:POST /v1/payments}': ['rate<0.01'],
    'http_req_duration{name:POST /v1/payments}': ['p(95)<1000'],
    checks: ['rate>0.99'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  discardResponseBodies: true,
};

function uuid() {
  return 'xxxxxxxxxxxx4xxxyxxxxxxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}

function acct(n) {
  return 'load-user-' + String(n).padStart(6, '0');
}

export default function () {
  const sender = 1 + Math.floor(Math.random() * ACCOUNT_COUNT);
  let receiver = 1 + Math.floor(Math.random() * ACCOUNT_COUNT);
  if (receiver === sender) receiver = (receiver % ACCOUNT_COUNT) + 1;

  const payload = JSON.stringify({
    transactionId: 'load-' + uuid(),
    senderId: acct(sender),
    receiverId: acct(receiver),
    amount: (1 + Math.floor(Math.random() * 400) / 100).toFixed(2), // 1.00 - 4.99
    currency: 'USD',
  });

  const res = http.post(`${GATEWAY}/v1/payments`, payload, {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'POST /v1/payments' },
  });

  check(res, { 'status is 202': (r) => r.status === 202 });
}
