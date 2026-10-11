// k6 load test: N different patients try to book the SAME slot at the same moment.
//
//   k6 run loadtest/book-same-slot.js
//   k6 run -e USERS=200 -e BASE_URL=https://your-deployed-app.onrender.com loadtest/book-same-slot.js
//
// What "pass" means (see thresholds below): exactly ONE request gets 201 Created, every other request
// gets a clean 409 Conflict, and nothing else (no 500s) happens. The summary at the end also prints the
// latency percentiles (p95 etc.) you can quote in your write-up.
import http from 'k6/http';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const USERS = parseInt(__ENV.USERS || '100', 10);
const ADMIN_EMAIL = __ENV.ADMIN_EMAIL || 'admin@clinic.com';
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || 'Admin@123';

const created = new Counter('bookings_created');
const conflicts = new Counter('bookings_conflict');
const unexpected = new Counter('bookings_unexpected');

export const options = {
  setupTimeout: '5m', // registering many users (BCrypt hashing) takes a little while
  scenarios: {
    same_slot_race: { executor: 'per-vu-iterations', vus: USERS, iterations: 1, maxDuration: '2m' },
  },
  thresholds: {
    bookings_created: ['count==1'],     // exactly one winner
    bookings_unexpected: ['count==0'],  // only 201 or 409 allowed
    'http_req_duration{name:book}': ['p(95)<2000'],
  },
};

export function setup() {
  const jsonHeaders = { 'Content-Type': 'application/json' };

  const login = http.post(`${BASE_URL}/api/auth/login`,
    JSON.stringify({ email: ADMIN_EMAIL, password: ADMIN_PASSWORD }), { headers: jsonHeaders });
  if (login.status !== 200) {
    throw new Error(`Admin login failed (${login.status}). Check ADMIN_EMAIL / ADMIN_PASSWORD.`);
  }
  const adminHeaders = { ...jsonHeaders, Authorization: `Bearer ${login.json('token')}` };

  // a fresh doctor with a single slot, so every run starts from a clean state
  const doctor = http.post(`${BASE_URL}/api/doctors`,
    JSON.stringify({ name: 'Dr. Load Test', specialization: 'Performance' }), { headers: adminHeaders });
  const doctorId = doctor.json('id');

  const date = new Date(Date.now() + 3 * 24 * 3600 * 1000).toISOString().slice(0, 10);
  const slots = http.post(`${BASE_URL}/api/doctors/${doctorId}/slots`,
    JSON.stringify({ date, startTime: '09:00', endTime: '09:30', slotMinutes: 30 }), { headers: adminHeaders });
  const slotId = slots.json()[0].id;

  const stamp = Date.now();
  const tokens = [];
  for (let i = 0; i < USERS; i++) {
    const res = http.post(`${BASE_URL}/api/auth/register`,
      JSON.stringify({ name: `Load ${i}`, email: `load-${stamp}-${i}@test.com`, password: 'Passw0rd!' }),
      { headers: jsonHeaders });
    tokens.push(res.json('token'));
  }
  return { slotId, tokens };
}

export default function (data) {
  const token = data.tokens[__VU - 1];
  const res = http.post(`${BASE_URL}/api/appointments`, JSON.stringify({ slotId: data.slotId }), {
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    tags: { name: 'book' },
  });
  created.add(res.status === 201 ? 1 : 0);
  conflicts.add(res.status === 409 ? 1 : 0);
  unexpected.add(res.status === 201 || res.status === 409 ? 0 : 1);
}
