const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api";

async function request(path, { method = "GET", body, token } = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    ...(body ? { body: JSON.stringify(body) } : {})
  });

  if (!response.ok) {
    let message = "Request failed";
    const rawBody = await response.text();
    if (rawBody) {
      try {
        const data = JSON.parse(rawBody);
        message = data.message || data.error || JSON.stringify(data);
      } catch {
        message = rawBody;
      }
    }
    throw new Error(message || `HTTP ${response.status}`);
  }

  if (response.status === 204) return null;
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

export const api = {
  register: (payload) => request("/auth/register", { method: "POST", body: payload }),
  login: (payload) => request("/auth/login", { method: "POST", body: payload }),

  searchUsers: (query, token) =>
    request(`/users/search?query=${encodeURIComponent(query)}`, { token }),

  getUserByUsername: (username, token) =>
    request(`/users/username/${encodeURIComponent(username)}`, { token }),

  getUserProfile: (userId, token) => request(`/users/${userId}/profile`, { token }),

  getUserProfileByUsername: (username, token) =>
    request(`/users/username/${encodeURIComponent(username)}/profile`, { token }),

  getUserReputationHistory: (userId, limit, token) =>
    request(`/users/${userId}/reputation-history?limit=${encodeURIComponent(limit)}`, { token }),

  getUserPostsPage: (userId, page, size, token) =>
    request(`/posts/user/${userId}/page?page=${encodeURIComponent(page)}&size=${encodeURIComponent(size)}`, { token }),

  followUser: (userId, token) => request(`/users/${userId}/follow`, { method: "POST", token }),

  unfollowUser: (userId, token) => request(`/users/${userId}/unfollow`, { method: "DELETE", token }),

  createPost: (content, sources, topicTags, token) =>
    request("/posts", { method: "POST", token, body: { content, sources, topicTags } }),

  getSupportedTopicTags: (token) => request("/posts/topic-tags", { token }),

  getUserPosts: (userId, token) => request(`/posts/user/${userId}`, { token }),

  getCounterpoints: (postId, token) => request(`/posts/${postId}/counterpoints`, { token }),

  createCounterpoint: (postId, content, sources, token) =>
    request(`/posts/${postId}/counterpoints`, { method: "POST", token, body: { content, sources } }),

  likePost: (postId, token) => request(`/posts/${postId}/likes`, { method: "POST", token }),

  unlikePost: (postId, token) => request(`/posts/${postId}/likes`, { method: "DELETE", token }),

  dislikePost: (postId, token) => request(`/posts/${postId}/dislikes`, { method: "POST", token }),

  undislikePost: (postId, token) => request(`/posts/${postId}/dislikes`, { method: "DELETE", token }),

  searchPosts: (query, limit, token) =>
    request(`/posts/search?query=${encodeURIComponent(query)}&limit=${encodeURIComponent(limit)}`, { token }),

  recomputeMyEmbedding: (token) =>
    request("/users/me/embedding/recompute", { method: "POST", token }),

  getTimeline: (page, size, token) =>
    request(`/timeline?page=${page}&size=${size}`, { token }),

  getTimelineByCursor: (cursor, size, token) => {
    const cursorPart = cursor ? `&cursor=${encodeURIComponent(cursor)}` : "";
    return request(`/timeline/cursor?size=${encodeURIComponent(size)}${cursorPart}`, { token });
  },

  getForYouTimeline: (page, size, token) =>
    request(`/timeline/for-you?page=${page}&size=${size}`, { token }),

  getNotifications: (page, token) =>
    request(`/notifications?page=${encodeURIComponent(page)}`, { token }),

  getUnreadNotifications: (token) =>
    request("/notifications/unread", { token }),

  getUnreadCount: (token) =>
    request("/notifications/unread/count", { token }),

  markNotificationAsRead: (notificationId, token) =>
    request(`/notifications/${notificationId}/read`, { method: "PUT", token }),

  markAllNotificationsAsRead: (token) =>
    request("/notifications/read-all", { method: "PUT", token })
};