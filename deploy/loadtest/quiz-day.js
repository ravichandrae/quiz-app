// k6 load test: a class of students all starting the same quiz at once.
//
// Run against a throwaway stack (it creates students and a quiz). See docs/deployment.md:
//   docker run --rm -i -e BASE_URL=http://host.docker.internal:3100/api -e STUDENTS=100 \
//     grafana/k6 run - < deploy/loadtest/quiz-day.js
// The stack needs AUTH_RATE_LIMIT_PER_MINUTE raised, since every simulated student shares one address.
import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://host.docker.internal:3100/api';
const STUDENTS = Number(__ENV.STUDENTS || 100);
const ADMIN_MOBILE = __ENV.ADMIN_MOBILE || '9999999999';
const ADMIN_PIN = __ENV.ADMIN_PIN || '123456';
const QUESTIONS = 5;
const PIN = '2468';
const JSON_HEADERS = { 'Content-Type': 'application/json' };

// Login checks the PIN with a deliberately slow hash, so a whole class logging in at once queues
// up; it is measured separately from the requests made while taking the quiz.
const loginTime = new Trend('login_duration', true);
const quizRequestTime = new Trend('quiz_request_duration', true);
const answerTime = new Trend('answer_duration', true);

export const options = {
  scenarios: {
    // Every student starts at the same moment, the busiest case for a class.
    class: { executor: 'per-vu-iterations', vus: STUDENTS, iterations: 1, maxDuration: '5m' },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    login_duration: ['p(95)<2000'],
    quiz_request_duration: ['p(95)<500'],
    answer_duration: ['p(95)<300'],
    checks: ['rate>0.99'],
  },
};

function post(path, body, token) {
  const headers = token ? { ...JSON_HEADERS, Authorization: `Bearer ${token}` } : JSON_HEADERS;
  return http.post(`${BASE}${path}`, body === undefined ? null : JSON.stringify(body), { headers });
}

function get(path, token) {
  return http.get(`${BASE}${path}`, { headers: { Authorization: `Bearer ${token}` } });
}

function mobileFor(n) {
  return `70000${String(n).padStart(5, '0')}`;
}

export function setup() {
  const admin = post('/auth/login', { mobile: ADMIN_MOBILE, pin: ADMIN_PIN });
  if (admin.status !== 200) fail(`admin login failed: ${admin.status} ${admin.body}`);
  const token = admin.json('accessToken');

  const questionIds = [];
  for (let i = 0; i < QUESTIONS; i++) {
    const q = post('/admin/questions', {
      text: `Load test question ${i + 1}`,
      options: ['A', 'B', 'C', 'D'],
      correctOption: i % 4,
      timeLimitSeconds: 30,
    }, token);
    if (q.status !== 201) fail(`question failed: ${q.status} ${q.body}`);
    questionIds.push(q.json('id'));
  }
  const quiz = post('/admin/quizzes', {
    title: `Load test ${new Date().toISOString()}`,
    totalTimeLimitSeconds: null,
    showAnswers: true,
    questionIds,
  }, token);
  if (quiz.status !== 201) fail(`quiz failed: ${quiz.status} ${quiz.body}`);
  const quizId = quiz.json('id');
  post(`/admin/quizzes/${quizId}/assignments`, { targetType: 'ALL' }, token);

  for (let n = 1; n <= STUDENTS; n++) {
    // 409 means the student exists from an earlier run, which is fine.
    const res = http.post(`${BASE}/auth/register`, JSON.stringify({ name: `Load Student ${n}`, mobile: mobileFor(n), pin: PIN }), {
      headers: JSON_HEADERS,
      responseCallback: http.expectedStatuses(201, 409),
    });
    if (res.status !== 201 && res.status !== 409) fail(`register failed: ${res.status} ${res.body}`);
  }
  return { quizId };
}

export default function ({ quizId }) {
  const login = post('/auth/login', { mobile: mobileFor(__VU), pin: PIN });
  loginTime.add(login.timings.duration);
  if (!check(login, { 'logged in': (r) => r.status === 200 })) return;
  const token = login.json('accessToken');

  const list = get('/me/quizzes', token);
  quizRequestTime.add(list.timings.duration);
  check(list, { 'quiz list': (r) => r.status === 200 && r.json().length > 0 });

  let state = post(`/me/quizzes/${quizId}/attempt`, undefined, token);
  quizRequestTime.add(state.timings.duration);
  if (!check(state, { 'started': (r) => r.status === 200 })) return;
  const attemptId = state.json('attemptId');

  for (let i = 0; i < QUESTIONS; i++) {
    sleep(2 + Math.random() * 4); // reading and thinking
    const position = state.json('question.position');
    state = post(`/me/attempts/${attemptId}/answers`, { position, selectedOption: Math.floor(Math.random() * 4) }, token);
    answerTime.add(state.timings.duration);
    quizRequestTime.add(state.timings.duration);
    check(state, { 'answer accepted': (r) => r.status === 200 });
  }
  check(state, { 'finished': (r) => r.json('status') === 'COMPLETED' });
  const review = get(`/me/results/${attemptId}`, token);
  quizRequestTime.add(review.timings.duration);
  check(review, { 'review': (r) => r.status === 200 });
}
