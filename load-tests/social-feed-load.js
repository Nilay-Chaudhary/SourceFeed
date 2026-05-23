import http from "k6/http";
import { check, sleep, fail } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080/api";
const PASSWORD = __ENV.TEST_PASSWORD || "pass123";

const RATE = Number(__ENV.RATE || 50); // 20 req/s = 1200 req/min
const DURATION = __ENV.DURATION || "1m";
const PREALLOCATED_VUS = Number(__ENV.PREALLOCATED_VUS || 100);
const MAX_VUS = Number(__ENV.MAX_VUS || 200);
const FEED_SIZE = Number(__ENV.FEED_SIZE || 20);
const SEEDED_POSTS = Number(__ENV.SEEDED_POSTS || 300);
const THINK_TIME = Number(__ENV.THINK_TIME || 0.1);
const FEED_MODE = (__ENV.FEED_MODE || "mixed").toLowerCase(); // mixed | timeline | for_you

// Per-VU cursor state is retained across iterations in k6 runtimes.
let timelineCursor = null;

export const options = {
  scenarios: {
    feed_benchmark: {
      executor: "constant-arrival-rate",
      rate: RATE,
      timeUnit: "1s",
      duration: DURATION,
      preAllocatedVUs: PREALLOCATED_VUS,
      maxVUs: MAX_VUS,
      exec: "readFeed",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    "http_req_duration{endpoint:timeline}": ["p(95)<220"],
    "http_req_duration{endpoint:for_you}": ["p(95)<250"],
  },
};

function jsonHeaders(token) {
  const headers = { "Content-Type": "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;
  return { headers };
}

function safeJson(res) {
  try {
    return res.json();
  } catch {
    return null;
  }
}

function registerOrLogin(username, email, displayName) {
  const registerPayload = JSON.stringify({
    username,
    email,
    displayName,
    password: PASSWORD,
  });

  const registerRes = http.post(
    `${BASE_URL}/auth/register`,
    registerPayload,
    jsonHeaders()
  );

  if (registerRes.status === 200 || registerRes.status === 201) {
    const data = safeJson(registerRes);
    if (data?.token && data?.userId) return data;
  }

  const loginPayload = JSON.stringify({ username, password: PASSWORD });
  const loginRes = http.post(`${BASE_URL}/auth/login`, loginPayload, jsonHeaders());

  check(loginRes, {
    "login succeeds": (r) => r.status === 200,
  });

  const data = safeJson(loginRes);
  if (!data?.token || !data?.userId) {
    fail("Login did not return token/userId");
  }
  return data;
}

function createPost(token, content, sourceUrl) {
  const payload = JSON.stringify({
    content,
    sources: [sourceUrl],
    topicTags: ["computer-science"],
  });

  const res = http.post(`${BASE_URL}/posts`, payload, jsonHeaders(token));
  check(res, {
    "seed post created": (r) => r.status === 201,
  });

  return safeJson(res);
}

export function setup() {
  const suffix = Date.now().toString().slice(-8);

  const author = registerOrLogin(
    `author_${suffix}`,
    `author_${suffix}@test.com`,
    "Feed Author"
  );

  const viewer = registerOrLogin(
    `viewer_${suffix}`,
    `viewer_${suffix}@test.com`,
    "Feed Viewer"
  );

  const followRes = http.post(
    `${BASE_URL}/users/${author.userId}/follow`,
    null,
    jsonHeaders(viewer.token)
  );

  check(followRes, {
    "viewer follows author": (r) => r.status === 200 || r.status === 400,
  });

  const seededPostIds = [];
  for (let i = 0; i < SEEDED_POSTS; i++) {
    const created = createPost(
      author.token,
      `k6 seed post #${i + 1} at ${new Date().toISOString()}`,
      i % 2 === 0
        ? "https://arxiv.org/abs/1706.03762"
        : "https://example.com/article"
    );

    if (created?.id) seededPostIds.push(created.id);
  }

  return {
    viewerToken: viewer.token,
    viewerUserId: viewer.userId,
    authorUserId: author.userId,
    seededPostIds,
  };
}

export function readFeed(data) {
  const auth = jsonHeaders(data.viewerToken);

  let endpoint = "timeline";
  if (FEED_MODE === "for_you") {
    endpoint = "for_you";
  } else if (FEED_MODE !== "timeline") {
    endpoint = Math.random() < 0.75 ? "timeline" : "for_you";
  }

  if (endpoint === "timeline") {
    const cursorPart = timelineCursor ? `&cursor=${encodeURIComponent(timelineCursor)}` : "";
    const res = http.get(
      `${BASE_URL}/timeline/cursor?size=${FEED_SIZE}${cursorPart}`,
      {
        ...auth,
        tags: { endpoint: "timeline" },
      }
    );

    const payload = safeJson(res);
    if (res.status === 200 && payload && Array.isArray(payload.items)) {
      timelineCursor = payload.hasMore ? payload.nextCursor || null : null;
    } else if (res.status >= 400) {
      timelineCursor = null;
    }

    check(res, {
      "timeline 200": (r) => r.status === 200,
      "timeline payload shape": () => payload && Array.isArray(payload.items),
    });
  } else {
    const res = http.get(
      `${BASE_URL}/timeline/for-you?page=0&size=${FEED_SIZE}`,
      {
        ...auth,
        tags: { endpoint: "for_you" },
      }
    );

    check(res, {
      "for-you 200": (r) => r.status === 200,
    });
  }

  sleep(THINK_TIME);
}