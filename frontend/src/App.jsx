import { useEffect, useMemo, useState } from "react";
import { api } from "./api";
import AuthCard from "./components/AuthCard";
import NotificationBell from "./components/NotificationBell";
import { clearAuth, getToken, getUser, saveAuth } from "./lib/auth";
import FeedPage from "./pages/FeedPage";
import SearchProfilesPage from "./pages/SearchProfilesPage";
import { toast } from "react-toastify";

const APP_NAME = "SourceFeed";
const TOPIC_TAG_OPTIONS = [
  { value: "physics", label: "Physics" },
  { value: "chemistry", label: "Chemistry" },
  { value: "biology", label: "Biology" },
  { value: "finance", label: "Finance" },
  { value: "computer-science", label: "Computer Science" }
];

function App() {
  const [token, setToken] = useState("");
  const [user, setUser] = useState(null);
  const [mode, setMode] = useState("login");
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const [authForm, setAuthForm] = useState({
    username: "",
    displayName: "",
    email: "",
    password: ""
  });

  const [currentPage, setCurrentPage] = useState("feed");
  const [postContent, setPostContent] = useState("");
  const [postSourcesInput, setPostSourcesInput] = useState("");
  const [postTopicTags, setPostTopicTags] = useState([]);
  const [timeline, setTimeline] = useState([]);
  const [timelineCursor, setTimelineCursor] = useState(null);
  const [timelineHasMore, setTimelineHasMore] = useState(true);
  const [timelineLoading, setTimelineLoading] = useState(false);
  const [forYou, setForYou] = useState([]);
  const [myPosts, setMyPosts] = useState([]);
  const [myPostsLoading, setMyPostsLoading] = useState(false);
  const [myPostsPage, setMyPostsPage] = useState(0);
  const [myPostsTotalPages, setMyPostsTotalPages] = useState(0);
  const [myPostsPageSize] = useState(10);
  const [activeFeedTab, setActiveFeedTab] = useState("timeline");

  const [profile, setProfile] = useState(null);
  const [profileReputationHistory, setProfileReputationHistory] = useState(null);
  const [profilePosts, setProfilePosts] = useState([]);
  const [profileLoading, setProfileLoading] = useState(false);
  const [profilePostsLoading, setProfilePostsLoading] = useState(false);
  const [profilePostsPage, setProfilePostsPage] = useState(0);
  const [profilePostsTotalPages, setProfilePostsTotalPages] = useState(0);
  const [profilePostsPageSize] = useState(10);
  const [searchQuery, setSearchQuery] = useState("");
  const [searchResults, setSearchResults] = useState([]);
  const [searchLoading, setSearchLoading] = useState(false);

  const [postSearchQuery, setPostSearchQuery] = useState("");
  const [postSearchResults, setPostSearchResults] = useState([]);
  const [postSearchLoading, setPostSearchLoading] = useState(false);

  const [likePending, setLikePending] = useState({});
  const [dislikePending, setDislikePending] = useState({});
  const [followPending, setFollowPending] = useState({});
  const [counterpointsByParentId, setCounterpointsByParentId] = useState({});
  const [expandedCounterpointsByParentId, setExpandedCounterpointsByParentId] = useState({});
  const [counterpointsLoadingByParentId, setCounterpointsLoadingByParentId] = useState({});
  const [counterpointComposerPostId, setCounterpointComposerPostId] = useState(null);
  const [counterpointContent, setCounterpointContent] = useState("");
  const [counterpointSourcesInput, setCounterpointSourcesInput] = useState("");

  const [unreadCount, setUnreadCount] = useState(0);
  const [notificationsLoaded, setNotificationsLoaded] = useState(false);

  const isAuthed = useMemo(() => Boolean(token && user), [token, user]);

  useEffect(() => {
    const savedToken = getToken();
    const savedUser = getUser();
    if (savedToken && savedUser) {
      setToken(savedToken);
      setUser(savedUser);
    }
  }, []);

  useEffect(() => {
    if (isAuthed) {
      loadTimeline();
      loadForYou();
      loadMyPosts(0);
    }
  }, [isAuthed]);

  useEffect(() => {
    if (isAuthed && !notificationsLoaded) {
      // Load unread count immediately
      async function loadUnreadCount() {
        try {
          const response = await api.getUnreadCount(token);
          setUnreadCount(response.count || 0);
          setNotificationsLoaded(true);
        } catch (err) {
          console.error("Failed to load unread count:", err);
        }
      }
      loadUnreadCount();

      // Poll for unread count every 30 seconds
      const interval = setInterval(async () => {
        try {
          const response = await api.getUnreadCount(token);
          setUnreadCount(response.count || 0);
        } catch (err) {
          console.error("Failed to refresh unread count:", err);
        }
      }, 30000);

      return () => clearInterval(interval);
    }
  }, [isAuthed, notificationsLoaded, token]);

  function mutateAllPostLists(postId, mapper) {
    setTimeline((prev) => prev.map((item) => (item.id === postId ? mapper(item) : item)));
    setForYou((prev) => prev.map((item) => (item.id === postId ? mapper(item) : item)));
    setMyPosts((prev) => prev.map((item) => (item.id === postId ? mapper(item) : item)));
    setProfilePosts((prev) => prev.map((item) => (item.id === postId ? mapper(item) : item)));
    setCounterpointsByParentId((prev) =>
      Object.fromEntries(
        Object.entries(prev).map(([parentId, posts]) => [
          parentId,
          posts.map((item) => (item.id === postId ? mapper(item) : item))
        ])
      )
    );
  }

  async function loadCounterpoints(parentPostId) {
    if (!parentPostId || counterpointsLoadingByParentId[parentPostId]) return;

    try {
      setCounterpointsLoadingByParentId((prev) => ({ ...prev, [parentPostId]: true }));
      const results = await api.getCounterpoints(parentPostId, token);
      setCounterpointsByParentId((prev) => ({
        ...prev,
        [parentPostId]: Array.isArray(results) ? results : []
      }));
      setExpandedCounterpointsByParentId((prev) => ({ ...prev, [parentPostId]: true }));
    } catch (err) {
      const msg = err.message || "Failed to load counterpoints";
      setError(msg);
      toast.error(msg);
    } finally {
      setCounterpointsLoadingByParentId((prev) => {
        const next = { ...prev };
        delete next[parentPostId];
        return next;
      });
    }
  }

  async function toggleCounterpoints(parentPostId) {
    if (!parentPostId) return;

    if (expandedCounterpointsByParentId[parentPostId]) {
      setExpandedCounterpointsByParentId((prev) => ({ ...prev, [parentPostId]: false }));
      return;
    }

    if (!counterpointsByParentId[parentPostId]) {
      await loadCounterpoints(parentPostId);
      return;
    }

    setExpandedCounterpointsByParentId((prev) => ({ ...prev, [parentPostId]: true }));
  }

  function openCounterpointComposer(postId) {
    if (!postId) {
      setCounterpointComposerPostId(null);
      setCounterpointContent("");
      setCounterpointSourcesInput("");
      return;
    }
    setCounterpointComposerPostId((prev) => (prev === postId ? null : postId));
    setCounterpointContent("");
    setCounterpointSourcesInput("");
  }

  async function handleCreateCounterpoint(event) {
    event.preventDefault();

    const parentPostId = counterpointComposerPostId;
    if (!parentPostId) return;

    const content = counterpointContent.trim();
    if (!content) {
      const msg = "Counterpoint text is required.";
      setError(msg);
      toast.error(msg);
      return;
    }

    if (content.length > 300) {
      const msg = "Counterpoint content must be 300 characters or less.";
      setError(msg);
      toast.error(msg);
      return;
    }

    const sources = counterpointSourcesInput
      .split(",")
      .map((source) => source.trim())
      .filter(Boolean)
      .slice(0, 3);

    if (sources.length === 0) {
      const msg = "At least one valid source URL is required.";
      setError(msg);
      toast.error(msg);
      return;
    }

    try {
      setError("");
      setMessage("");
      await api.createCounterpoint(parentPostId, content, sources, token);
      setCounterpointContent("");
      setCounterpointSourcesInput("");
      setCounterpointComposerPostId(null);
      await loadCounterpoints(parentPostId);
      const msg = "Counterpoint created";
      setMessage(msg);
      toast.success(msg);
    } catch (err) {
      const msg = err.message || "Create counterpoint failed";
      setError(msg);
      toast.error(msg);
    }
  }

  async function loadTimeline(reset = true) {
    if (timelineLoading) return;

    try {
      setTimelineLoading(true);

      const cursorToUse = reset ? null : timelineCursor;

      try {
        const response = await api.getTimelineByCursor(cursorToUse, 20, token);
        const items = Array.isArray(response?.items) ? response.items : [];

        setTimeline((prev) => (reset ? items : [...prev, ...items]));
        setTimelineCursor(response?.nextCursor || null);
        setTimelineHasMore(Boolean(response?.hasMore));
        return;
      } catch (cursorErr) {
        if (!reset) {
          throw cursorErr;
        }

        // Backward compatibility for older backend versions.
        const feed = await api.getTimeline(0, 20, token);
        const items = Array.isArray(feed) ? feed : [];
        setTimeline(items);
        setTimelineCursor(null);
        setTimelineHasMore(false);
        return;
      }
    } catch (err) {
      const msg = err.message || "Failed to load timeline";
      setError(msg);
      toast.error(msg);
    } finally {
      setTimelineLoading(false);
    }
  }

  async function loadMoreTimeline() {
    if (timelineLoading || !timelineHasMore) return;
    await loadTimeline(false);
  }

  async function loadForYou() {
    try {
      const feed = await api.getForYouTimeline(0, 20, token);
      setForYou(Array.isArray(feed) ? feed : []);
    } catch (err) {
      const msg = err.message || "Failed to load For You feed";
      setError(msg);
      toast.error(msg);
    }
  }

  async function loadMyPosts(page = 0, append = false) {
    if (!user?.userId) return;
    try {
      setMyPostsLoading(true);
      const response = await api.getUserPostsPage(user.userId, page, myPostsPageSize, token);
      const posts = Array.isArray(response?.content) ? response.content : [];
      setMyPosts((prev) => (append ? [...prev, ...posts] : posts));
      setMyPostsPage(Number(response?.number || 0));
      setMyPostsTotalPages(Number(response?.totalPages || 0));
    } catch (err) {
      const msg = err.message || "Failed to load your posts";
      setError(msg);
      toast.error(msg);
    } finally {
      setMyPostsLoading(false);
    }
  }

  async function loadMoreMyPosts() {
    if (myPostsLoading) return;
    if (myPostsTotalPages <= 0) return;
    const nextPage = myPostsPage + 1;
    if (nextPage >= myPostsTotalPages) return;
    await loadMyPosts(nextPage, true);
  }

  async function loadProfilePosts(userId, page = 0, append = false) {
    try {
      setProfilePostsLoading(true);
      const response = await api.getUserPostsPage(userId, page, profilePostsPageSize, token);
      const posts = Array.isArray(response?.content) ? response.content : [];
      setProfilePosts((prev) => (append ? [...prev, ...posts] : posts));
      setProfilePostsPage(Number(response?.number || 0));
      setProfilePostsTotalPages(Number(response?.totalPages || 0));
    } catch (err) {
      const msg = err.message || "Failed to load profile posts";
      setError(msg);
      toast.error(msg);
    } finally {
      setProfilePostsLoading(false);
    }
  }

  async function openProfile(userId) {
    try {
      setError("");
      setProfileLoading(true);
      const [profileData, posts, reputationHistory] = await Promise.all([
        api.getUserProfile(userId, token),
        api.getUserPostsPage(userId, 0, profilePostsPageSize, token),
        api.getUserReputationHistory(userId, 80, token)
      ]);
      setProfile(profileData || null);
      setProfileReputationHistory(reputationHistory || null);
      setProfilePosts(Array.isArray(posts?.content) ? posts.content : []);
      setProfilePostsPage(Number(posts?.number || 0));
      setProfilePostsTotalPages(Number(posts?.totalPages || 0));
      setCurrentPage("search");
    } catch (err) {
      const msg = err.message || "Failed to load profile";
      setError(msg);
      toast.error(msg);
    } finally {
      setProfileLoading(false);
    }
  }

  async function loadMoreProfilePosts() {
    if (!profile?.id) return;
    if (profilePostsLoading) return;
    if (profilePostsTotalPages <= 0) return;
    const nextPage = profilePostsPage + 1;
    if (nextPage >= profilePostsTotalPages) return;
    await loadProfilePosts(profile.id, nextPage, true);
  }

  function patchFollowState(targetUserId, nextFollowing) {
    setSearchResults((prev) =>
      prev.map((result) =>
        result.id === targetUserId
          ? {
              ...result,
              following: nextFollowing
            }
          : result
      )
    );

    setProfile((prev) => {
      if (!prev || prev.id !== targetUserId) return prev;
      const followerCount = Number(prev.followerCount || 0);
      return {
        ...prev,
        following: nextFollowing,
        followerCount: Math.max(0, followerCount + (nextFollowing ? 1 : -1))
      };
    });
  }

  async function toggleFollow(targetUserId, currentlyFollowing) {
    if (followPending[targetUserId]) return;
    if (!targetUserId || targetUserId === user?.userId) return;

    const nextFollowing = !currentlyFollowing;
    setFollowPending((prev) => ({ ...prev, [targetUserId]: true }));
    patchFollowState(targetUserId, nextFollowing);

    try {
      if (nextFollowing) {
        await api.followUser(targetUserId, token);
      } else {
        await api.unfollowUser(targetUserId, token);
      }
      setMessage(nextFollowing ? "User followed" : "User unfollowed");
      if (activeFeedTab === "timeline") {
        setTimeout(loadTimeline, 300);
      } else if (activeFeedTab === "for-you") {
        setTimeout(loadForYou, 300);
      }
    } catch (err) {
      patchFollowState(targetUserId, currentlyFollowing);
      setError(err.message || "Follow update failed");
    } finally {
      setFollowPending((prev) => {
        const next = { ...prev };
        delete next[targetUserId];
        return next;
      });
    }
  }

  async function searchUsers(event) {
    event.preventDefault();
    const query = searchQuery.trim();
    if (!query) return;

    try {
      setError("");
      setSearchLoading(true);
      const users = await api.searchUsers(query, token);
      setSearchResults(Array.isArray(users) ? users : []);
    } catch (err) {
      const msg = err.message || "Search failed";
      setError(msg);
      toast.error(msg);
    } finally {
      setSearchLoading(false);
    }
  }

  async function searchPosts(event) {
    event.preventDefault();
    const query = postSearchQuery.trim();
    if (!query) return;

    try {
      setError("");
      setPostSearchLoading(true);
      const results = await api.searchPosts(query, 20, token);
      const normalized = Array.isArray(results)
        ? results.map((result) => ({
            ...(result?.post || {}),
            relevanceScore: result?.relevanceScore
          }))
        : [];
      setPostSearchResults(normalized);
    } catch (err) {
      const msg = err.message || "Post search failed";
      setError(msg);
      toast.error(msg);
    } finally {
      setPostSearchLoading(false);
    }
  }

  async function submitAuth(event) {
    event.preventDefault();
    setLoading(true);
    setError("");
    setMessage("");

    try {
      const payload =
        mode === "register"
          ? {
              username: authForm.username,
              displayName: authForm.displayName,
              email: authForm.email,
              password: authForm.password
            }
          : {
              username: authForm.username,
              password: authForm.password
            };

      const response = mode === "register" ? await api.register(payload) : await api.login(payload);
      const nextUser = {
        userId: response.userId,
        username: response.username,
        displayName: response.displayName || response.username
      };
      saveAuth(response.token, nextUser);
      setToken(response.token);
      setUser(nextUser);

      // Kick off profile embedding generation on login/register for better For You ranking.
      try {
        await api.recomputeMyEmbedding(response.token);
      } catch (embeddingErr) {
        console.error("Profile embedding recompute failed", embeddingErr);
      }

      const msg = `Welcome ${response.displayName || response.username}`;
      setMessage(msg);
      toast.success(msg);
      setAuthForm({ username: "", displayName: "", email: "", password: "" });
    } catch (err) {
      const msg = err.message || "Authentication failed";
      setError(msg);
      toast.error(msg);
    } finally {
      setLoading(false);
    }
  }

  async function handleCreatePost(event) {
    event.preventDefault();
    if (!postContent.trim()) return;

    const sources = postSourcesInput
      .split(",")
      .map((source) => source.trim())
      .filter(Boolean)
      .slice(0, 3);

    if (sources.length === 0) {
      const msg = "At least one valid source URL is required.";
      setError(msg);
      toast.error(msg);
      return;
    }

    if (postTopicTags.length === 0) {
      const msg = "Select at least one topic tag.";
      setError(msg);
      toast.error(msg);
      return;
    }

    try {
      setError("");
      setMessage("");
      await api.createPost(postContent.trim(), sources, postTopicTags, token);
      setPostContent("");
      setPostSourcesInput("");
      setPostTopicTags([]);
      const msg = "Post created";
      setMessage(msg);
      toast.success(msg);
      setTimeout(() => {
        setMyPostsPage(0);
        loadMyPosts(0, false);
        loadTimeline();
        loadForYou();
      }, 400);
    } catch (err) {
      const msg = err.message || "Create post failed";
      setError(msg);
      toast.error(msg);
    }
  }

  async function handleToggleLike(postId, currentlyLiked) {
    if (likePending[postId] || dislikePending[postId]) return;

    const nextLiked = !currentlyLiked;
    setLikePending((prev) => ({ ...prev, [postId]: true }));

    mutateAllPostLists(postId, (item) => {
      const currentCount = Number(item.likeCount || 0);
      return {
        ...item,
        likedByMe: nextLiked,
        likeCount: Math.max(0, currentCount + (nextLiked ? 1 : -1)),
        dislikedByMe: nextLiked ? false : Boolean(item.dislikedByMe),
        dislikeCount: nextLiked && item.dislikedByMe ? Math.max(0, Number(item.dislikeCount || 0) - 1) : Number(item.dislikeCount || 0)
      };
    });

    try {
      const status = nextLiked ? await api.likePost(postId, token) : await api.unlikePost(postId, token);
      mutateAllPostLists(postId, (item) => ({
        ...item,
        likedByMe: Boolean(status?.likedByMe),
        likeCount: Number(status?.likeCount || 0),
        dislikedByMe: Boolean(status?.dislikedByMe),
        dislikeCount: Number(status?.dislikeCount || 0)
      }));
    } catch (err) {
      mutateAllPostLists(postId, (item) => {
        const currentCount = Number(item.likeCount || 0);
        const currentDislikeCount = Number(item.dislikeCount || 0);
        return {
          ...item,
          likedByMe: currentlyLiked,
          likeCount: Math.max(0, currentCount + (currentlyLiked ? 1 : -1)),
          dislikedByMe: Boolean(item.dislikedByMe),
          dislikeCount: currentDislikeCount
        };
      });
      const msg = err.message || "Like update failed";
      setError(msg);
      toast.error(msg);
    } finally {
      setLikePending((prev) => {
        const next = { ...prev };
        delete next[postId];
        return next;
      });
    }
  }

  async function handleToggleDislike(postId, currentlyDisliked) {
    if (likePending[postId] || dislikePending[postId]) return;

    const nextDisliked = !currentlyDisliked;
    setDislikePending((prev) => ({ ...prev, [postId]: true }));

    mutateAllPostLists(postId, (item) => {
      const currentCount = Number(item.dislikeCount || 0);
      return {
        ...item,
        dislikedByMe: nextDisliked,
        dislikeCount: Math.max(0, currentCount + (nextDisliked ? 1 : -1)),
        likedByMe: nextDisliked ? false : Boolean(item.likedByMe),
        likeCount: nextDisliked && item.likedByMe ? Math.max(0, Number(item.likeCount || 0) - 1) : Number(item.likeCount || 0)
      };
    });

    try {
      const status = nextDisliked ? await api.dislikePost(postId, token) : await api.undislikePost(postId, token);
      mutateAllPostLists(postId, (item) => ({
        ...item,
        likedByMe: Boolean(status?.likedByMe),
        likeCount: Number(status?.likeCount || 0),
        dislikedByMe: Boolean(status?.dislikedByMe),
        dislikeCount: Number(status?.dislikeCount || 0)
      }));
    } catch (err) {
      mutateAllPostLists(postId, (item) => {
        const currentCount = Number(item.dislikeCount || 0);
        return {
          ...item,
          dislikedByMe: currentlyDisliked,
          dislikeCount: Math.max(0, currentCount + (currentlyDisliked ? 1 : -1))
        };
      });
      const msg = err.message || "Dislike update failed";
      setError(msg);
      toast.error(msg);
    } finally {
      setDislikePending((prev) => {
        const next = { ...prev };
        delete next[postId];
        return next;
      });
    }
  }

  function logout() {
    clearAuth();
    setToken("");
    setUser(null);
    setTimeline([]);
    setTimelineCursor(null);
    setTimelineHasMore(true);
    setTimelineLoading(false);
    setForYou([]);
    setMyPosts([]);
    setProfile(null);
    setProfileReputationHistory(null);
    setProfilePosts([]);
    setSearchResults([]);
    setPostSearchResults([]);
    setCounterpointsByParentId({});
    setExpandedCounterpointsByParentId({});
    setCounterpointsLoadingByParentId({});
    setCounterpointComposerPostId(null);
    setCounterpointContent("");
    setCounterpointSourcesInput("");
    const msg = "Logged out";
    setMessage(msg);
    setError("");
    toast.success(msg);
    setCurrentPage("feed");
  }

  function handleRefreshFeed() {
    if (activeFeedTab === "timeline") {
      loadTimeline(true);
      return;
    }
    if (activeFeedTab === "for-you") {
      loadForYou();
      return;
    }
    loadMyPosts(0, false);
  }

  if (!isAuthed) {
    return (
      <AuthCard
        mode={mode}
        authForm={authForm}
        loading={loading}
        error={error}
        onModeChange={setMode}
        onSubmit={submitAuth}
        onFormChange={setAuthForm}
        appName={APP_NAME}
      />
    );
  }

  return (
    <div className="mx-auto my-8 w-full max-w-6xl px-4 pb-10 text-slate-100">
      <header className="mb-6 flex flex-col gap-4 rounded-3xl border border-slate-800 bg-slate-900/90 p-6 shadow-lg backdrop-blur sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight text-slate-50">{APP_NAME}</h1>
          <p className="mt-2 text-sm text-slate-400">
            Signed in as <span className="font-semibold text-slate-100">{user.displayName || user.username}</span>
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            className={`rounded-xl px-4 py-2 text-sm font-semibold ${
              currentPage === "feed"
                ? "bg-primary-500 text-slate-50 shadow-md"
                : "bg-slate-800 text-slate-200 hover:bg-slate-700"
            }`}
            onClick={() => setCurrentPage("feed")}
          >
            Feed
          </button>
          <button
            type="button"
            className={`rounded-xl px-4 py-2 text-sm font-semibold ${
              currentPage === "search"
                ? "bg-primary-500 text-slate-50 shadow-md"
                : "bg-slate-800 text-slate-200 hover:bg-slate-700"
            }`}
            onClick={() => setCurrentPage("search")}
          >
            Search & Profiles
          </button>
          <div className="flex items-center gap-2">
            <NotificationBell
              token={token}
              unreadCount={unreadCount}
              setUnreadCount={setUnreadCount}
            />
            <button
              type="button"
              className="rounded-xl bg-slate-800 px-5 py-2.5 text-sm font-semibold text-slate-100 hover:bg-slate-700"
              onClick={logout}
            >
              Logout
            </button>
          </div>
        </div>
      </header>

      {currentPage === "feed" ? (
        <FeedPage
          activeFeedTab={activeFeedTab}
          setActiveFeedTab={setActiveFeedTab}
          postContent={postContent}
          setPostContent={setPostContent}
          postSourcesInput={postSourcesInput}
          setPostSourcesInput={setPostSourcesInput}
          postTopicTags={postTopicTags}
          setPostTopicTags={setPostTopicTags}
          topicTagOptions={TOPIC_TAG_OPTIONS}
          onCreatePost={handleCreatePost}
          counterpointsByParentId={counterpointsByParentId}
          expandedCounterpointsByParentId={expandedCounterpointsByParentId}
          counterpointsLoadingByParentId={counterpointsLoadingByParentId}
          counterpointComposerPostId={counterpointComposerPostId}
          counterpointContent={counterpointContent}
          setCounterpointContent={setCounterpointContent}
          counterpointSourcesInput={counterpointSourcesInput}
          setCounterpointSourcesInput={setCounterpointSourcesInput}
          onToggleCounterpoints={toggleCounterpoints}
          onOpenCounterpointComposer={openCounterpointComposer}
          onCreateCounterpoint={handleCreateCounterpoint}
          onRefreshFeed={handleRefreshFeed}
          timeline={timeline}
          timelineLoading={timelineLoading}
          hasMoreTimeline={timelineHasMore}
          forYou={forYou}
          myPosts={myPosts}
          myPostsLoading={myPostsLoading}
          myPostsPage={myPostsPage}
          myPostsTotalPages={myPostsTotalPages}
          hasMoreMyPosts={myPostsPage + 1 < myPostsTotalPages}
          likePending={likePending}
          dislikePending={dislikePending}
          onToggleLike={handleToggleLike}
          onToggleDislike={handleToggleDislike}
          onOpenProfile={openProfile}
          onLoadMoreTimeline={loadMoreTimeline}
          onLoadMoreMyPosts={loadMoreMyPosts}
        />
      ) : (
        <SearchProfilesPage
          searchQuery={searchQuery}
          setSearchQuery={setSearchQuery}
          searchResults={searchResults}
          searchLoading={searchLoading}
          onSearch={searchUsers}
          postSearchQuery={postSearchQuery}
          setPostSearchQuery={setPostSearchQuery}
          postSearchResults={postSearchResults}
          postSearchLoading={postSearchLoading}
          onSearchPosts={searchPosts}
          onOpenProfile={openProfile}
          onToggleFollow={toggleFollow}
          followPending={followPending}
          profile={profile}
          profileReputationHistory={profileReputationHistory}
          profilePosts={profilePosts}
          profileLoading={profileLoading}
          profilePostsLoading={profilePostsLoading}
          profilePostsPage={profilePostsPage}
          profilePostsTotalPages={profilePostsTotalPages}
          hasMoreProfilePosts={profilePostsPage + 1 < profilePostsTotalPages}
          currentUserId={user?.userId}
          likePending={likePending}
          dislikePending={dislikePending}
          onToggleLike={handleToggleLike}
          onToggleDislike={handleToggleDislike}
          counterpointsByParentId={counterpointsByParentId}
          expandedCounterpointsByParentId={expandedCounterpointsByParentId}
          counterpointsLoadingByParentId={counterpointsLoadingByParentId}
          counterpointComposerPostId={counterpointComposerPostId}
          counterpointContent={counterpointContent}
          setCounterpointContent={setCounterpointContent}
          counterpointSourcesInput={counterpointSourcesInput}
          setCounterpointSourcesInput={setCounterpointSourcesInput}
          onToggleCounterpoints={toggleCounterpoints}
          onOpenCounterpointComposer={openCounterpointComposer}
          onCreateCounterpoint={handleCreateCounterpoint}
          onLoadMoreProfilePosts={loadMoreProfilePosts}
        />
      )}
    </div>
  );
}

export default App;
