import http from 'k6/http';
import { check, sleep } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

// Order Checkout Performance Benchmark
// Scenarios: Ramp-up across 100, 500, and 1000 concurrent users (VUs)
export const options = {
  scenarios: {
    checkout_100_vus: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 100 },
        { duration: '1m', target: 100 },
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
    checkout_500_vus: {
      executor: 'ramping-vus',
      startTime: '2m10s',
      startVUs: 0,
      stages: [
        { duration: '45s', target: 500 },
        { duration: '2m', target: 500 },
        { duration: '45s', target: 0 },
      ],
      gracefulRampDown: '15s',
    },
    checkout_1000_vus: {
      executor: 'ramping-vus',
      startTime: '6m',
      startVUs: 0,
      stages: [
        { duration: '1m', target: 1000 },
        { duration: '2m', target: 1000 },
        { duration: '1m', target: 0 },
      ],
      gracefulRampDown: '20s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'], // < 1% error rate
    'http_req_duration{scenario:checkout_100_vus}': ['p(50)<80', 'p(95)<150', 'p(99)<250'],
    'http_req_duration{scenario:checkout_500_vus}': ['p(50)<120', 'p(95)<260', 'p(99)<450'],
    'http_req_duration{scenario:checkout_1000_vus}': ['p(50)<210', 'p(95)<480', 'p(99)<850'],
  },
};

const BASE_URL = __ENV.GATEWAY_URL || 'http://localhost:8080';

export default function () {
  const idempotencyKey = uuidv4();
  const userId = '11111111-1111-1111-1111-111111111111';
  const productId = '33333333-3333-3333-3333-333333333331';

  const payload = JSON.stringify({
    userId: userId,
    customerPhone: '254712345678',
    items: [
      {
        productId: productId,
        quantity: 1,
        unitPrice: 44999.0,
      },
    ],
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': idempotencyKey,
      'X-Request-ID': idempotencyKey,
    },
  };

  const res = http.post(`${BASE_URL}/api/v1/orders`, payload, params);

  check(res, {
    'status is 202 or 200': (r) => r.status === 202 || r.status === 200,
    'response has orderId': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.orderId !== undefined || body.id !== undefined;
      } catch (e) {
        return false;
      }
    },
  });

  sleep(1);
}
