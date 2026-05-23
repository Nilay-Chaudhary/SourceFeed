import PostList from "../components/PostList";
import ReputationTrendChart from "../components/ReputationTrendChart";

export default function SearchProfilesPage({
  searchQuery,
  setSearchQuery,
  searchResults,
  searchLoading,
  onSearch,
  postSearchQuery,
  setPostSearchQuery,
  postSearchResults,
  postSearchLoading,
  onSearchPosts,
  onOpenProfile,
  onToggleFollow,
  followPending,
  profile,
  profileReputationHistory,
  profilePosts,
  profileLoading,
  profilePostsLoading,
  profilePostsPage,
  profilePostsTotalPages,
  hasMoreProfilePosts,
  currentUserId,
  likePending,
  dislikePending,
  onToggleLike,
  onToggleDislike,
  counterpointsByParentId,
  expandedCounterpointsByParentId,
  counterpointsLoadingByParentId,
  counterpointComposerPostId,
  counterpointContent,
  setCounterpointContent,
  counterpointSourcesInput,
  setCounterpointSourcesInput,
  onToggleCounterpoints,
  onOpenCounterpointComposer,
  onCreateCounterpoint,
  onLoadMoreProfilePosts
}) {
  const handleProfilePostsScroll = (event) => {
    if (profilePostsLoading || !hasMoreProfilePosts) return;

    const target = event.currentTarget;
    const nearBottom = target.scrollTop + target.clientHeight >= target.scrollHeight - 80;
    if (nearBottom) {
      onLoadMoreProfilePosts?.();
    }
  };

  return (
    <div className="grid gap-6 xl:grid-cols-3">
      <section className="space-y-6 xl:col-span-1">
        <div className="rounded-3xl border border-slate-800 bg-slate-900/90 p-6 shadow-lg">
          <h2 className="mb-2 text-xl font-bold text-slate-50">Search Users</h2>
          <p className="mb-4 text-sm text-slate-400">Search by username and open user profiles.</p>
          <form onSubmit={onSearch} className="space-y-4">
            <input
              placeholder="Search username"
              value={searchQuery}
              onChange={(event) => setSearchQuery(event.target.value)}
              required
            />
            <button type="submit" className="w-full rounded-xl bg-primary-500 py-3 text-sm font-semibold text-slate-50 hover:bg-primary-400 active:bg-primary-500/80 transition">
              {searchLoading ? "Searching..." : "Search"}
            </button>
          </form>

          <div className="mt-5 max-h-[280px] overflow-y-auto pr-1">
            {searchResults.length === 0 ? (
              <p className="text-sm text-slate-400">No results.</p>
            ) : (
              <ul className="space-y-2">
                {searchResults.map((result) => (
                  <li key={result.id} className="rounded-xl border border-slate-800 bg-slate-900/90 p-3 hover:bg-slate-800 transition">
                    <div className="flex items-center justify-between gap-2">
                      <div className="cursor-pointer text-left" onClick={() => onOpenProfile(result.id)}>
                        <span className="block text-sm font-semibold text-slate-50">{result.displayName || result.username}</span>
                        <span className="block text-xs text-slate-400">@{result.username}</span>
                        <span className="mt-1 inline-block rounded-full bg-slate-800 px-2 py-0.5 text-[11px] font-semibold text-slate-200">
                          Credibility: {Number(result.userTrustScore ?? 0).toFixed(2)}
                        </span>
                      </div>
                      {result.me || result.id === currentUserId ? (
                        <span className="rounded-full bg-slate-800 px-3 py-1 text-xs font-semibold text-slate-100">You</span>
                      ) : (
                        <button
                          type="button"
                          onClick={() => onToggleFollow(result.id, Boolean(result.following))}
                          disabled={Boolean(followPending[result.id])}
                          className={`rounded-full px-3 py-1 text-xs font-semibold transition ${
                            result.following
                              ? "bg-slate-800 text-slate-200 border border-slate-600"
                              : "bg-primary-500 text-slate-50 hover:bg-primary-400"
                          }`}
                        >
                          {result.following ? "Following" : "Follow"}
                        </button>
                      )}
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>

        <div className="rounded-3xl border border-slate-800 bg-slate-900/90 p-6 shadow-lg">
          <h2 className="mb-2 text-xl font-bold text-slate-50">Search Posts</h2>
          <p className="mb-4 text-sm text-slate-400">Semantic search over posts using AI embeddings.</p>
          <form onSubmit={onSearchPosts} className="space-y-4">
            <input
              placeholder="Search posts"
              value={postSearchQuery}
              onChange={(event) => setPostSearchQuery(event.target.value)}
              required
            />
            <button type="submit" className="w-full rounded-xl bg-primary-500 py-3 text-sm font-semibold text-slate-50 hover:bg-primary-400 active:bg-primary-500/80 transition">
              {postSearchLoading ? "Searching..." : "Search posts"}
            </button>
          </form>

          <div className="mt-5 max-h-[420px] overflow-y-auto pr-1">
            <PostList
              posts={postSearchResults}
              emptyLabel="No semantic post results yet."
              likePending={likePending}
              dislikePending={dislikePending}
              onToggleLike={onToggleLike}
              onToggleDislike={onToggleDislike}
              onOpenProfile={onOpenProfile}
              counterpointsByParentId={counterpointsByParentId}
              expandedCounterpointsByParentId={expandedCounterpointsByParentId}
              counterpointsLoadingByParentId={counterpointsLoadingByParentId}
              counterpointComposerPostId={counterpointComposerPostId}
              counterpointContent={counterpointContent}
              setCounterpointContent={setCounterpointContent}
              counterpointSourcesInput={counterpointSourcesInput}
              setCounterpointSourcesInput={setCounterpointSourcesInput}
              onToggleCounterpoints={onToggleCounterpoints}
              onOpenCounterpointComposer={onOpenCounterpointComposer}
              onCreateCounterpoint={onCreateCounterpoint}
            />
          </div>
        </div>
      </section>

      <section className="rounded-3xl border border-slate-800 bg-slate-900/90 p-6 shadow-lg xl:col-span-2">
        <div className="mb-5 flex items-center justify-between">
          <div>
            <h2 className="text-xl font-bold text-slate-50">Profile</h2>
            <p className="text-sm text-slate-400">User details and posts live on this page only.</p>
          </div>
          {profile && !profile.me && (
            <button
              type="button"
              onClick={() => onToggleFollow(profile.id, Boolean(profile.following))}
              disabled={Boolean(followPending[profile.id])}
              className={`rounded-full px-4 py-2 text-sm font-semibold transition ${
                profile.following
                  ? "bg-slate-800 text-slate-200 border border-slate-600"
                  : "bg-primary-500 text-slate-50 hover:bg-primary-400"
              }`}
            >
              {profile.following ? "Following" : "Follow"}
            </button>
          )}
        </div>

        {profileLoading ? (
          <p className="text-sm text-slate-400">Loading profile...</p>
        ) : !profile ? (
          <p className="text-sm text-slate-400">Select a user from search results.</p>
        ) : (
          <>
            <div className="mb-5 rounded-2xl border border-slate-800 bg-slate-900 p-5">
              <h3 className="text-lg font-bold text-slate-50">{profile.displayName || profile.username}</h3>
              <p className="text-sm text-slate-400">@{profile.username}</p>
              <p className="mt-2 text-sm text-slate-200 font-semibold">
                Credibility score: {Number(profile.userTrustScore ?? 0).toFixed(2)}
              </p>
              <p className="mt-2 text-sm text-slate-400">
                {profile.me ? "This is you" : profile.following ? "You follow this user" : "You do not follow this user"}
              </p>
              <div className="mt-4 flex flex-wrap gap-2 text-xs">
                <span className="rounded-full bg-slate-800 px-3 py-1.5 text-slate-100 font-semibold">Posts: {Number(profile.postCount || 0)}</span>
                <span className="rounded-full bg-slate-800 px-3 py-1.5 text-slate-100 font-semibold">Followers: {Number(profile.followerCount || 0)}</span>
                <span className="rounded-full bg-slate-800 px-3 py-1.5 text-slate-100 font-semibold">Following: {Number(profile.followingCount || 0)}</span>
              </div>

              {Array.isArray(profile.topicReputations) && profile.topicReputations.length > 0 && (
                <div className="mt-4">
                  <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-400">Topic Credibility</p>
                  <div className="flex flex-wrap gap-2">
                    {profile.topicReputations.map((topic) => (
                      <span
                        key={`${profile.id}-topic-${topic.topicTag}`}
                        className="rounded-full border border-emerald-700/60 bg-emerald-900/30 px-3 py-1.5 text-xs font-semibold text-emerald-300"
                      >
                        {topic.topicTag}: {Number(topic.topicTrustScore ?? 1).toFixed(2)}
                      </span>
                    ))}
                  </div>
                </div>
              )}

              <ReputationTrendChart history={profileReputationHistory} />
            </div>

            <div className="mb-3 text-sm text-slate-400">Loaded {profilePosts.length} posts</div>

            <div className="h-[420px] overflow-y-auto pr-1" onScroll={handleProfilePostsScroll}>
              {profilePostsLoading ? (
                profilePosts.length === 0 ? (
                  <p className="text-sm text-slate-400">Loading profile posts...</p>
                ) : (
                  <PostList
                    posts={profilePosts}
                    emptyLabel="This user has no posts yet."
                    likePending={likePending}
                    dislikePending={dislikePending}
                    onToggleLike={onToggleLike}
                    onToggleDislike={onToggleDislike}
                    onOpenProfile={onOpenProfile}
                    counterpointsByParentId={counterpointsByParentId}
                    expandedCounterpointsByParentId={expandedCounterpointsByParentId}
                    counterpointsLoadingByParentId={counterpointsLoadingByParentId}
                    counterpointComposerPostId={counterpointComposerPostId}
                    counterpointContent={counterpointContent}
                    setCounterpointContent={setCounterpointContent}
                    counterpointSourcesInput={counterpointSourcesInput}
                    setCounterpointSourcesInput={setCounterpointSourcesInput}
                    onToggleCounterpoints={onToggleCounterpoints}
                    onOpenCounterpointComposer={onOpenCounterpointComposer}
                    onCreateCounterpoint={onCreateCounterpoint}
                  />
                )
              ) : (
                <PostList
                  posts={profilePosts}
                  emptyLabel="This user has no posts yet."
                  likePending={likePending}
                  dislikePending={dislikePending}
                  onToggleLike={onToggleLike}
                  onToggleDislike={onToggleDislike}
                  onOpenProfile={onOpenProfile}
                  counterpointsByParentId={counterpointsByParentId}
                  expandedCounterpointsByParentId={expandedCounterpointsByParentId}
                  counterpointsLoadingByParentId={counterpointsLoadingByParentId}
                  counterpointComposerPostId={counterpointComposerPostId}
                  counterpointContent={counterpointContent}
                  setCounterpointContent={setCounterpointContent}
                  counterpointSourcesInput={counterpointSourcesInput}
                  setCounterpointSourcesInput={setCounterpointSourcesInput}
                  onToggleCounterpoints={onToggleCounterpoints}
                  onOpenCounterpointComposer={onOpenCounterpointComposer}
                  onCreateCounterpoint={onCreateCounterpoint}
                />
              )}

              {profilePostsLoading && profilePosts.length > 0 && (
                <p className="mt-3 text-center text-xs text-slate-500">Loading more posts...</p>
              )}

              {!profilePostsLoading && !hasMoreProfilePosts && profilePostsTotalPages > 0 && (
                <p className="mt-3 text-center text-xs text-slate-500">No more posts to load.</p>
              )}
            </div>
          </>
        )}
      </section>
    </div>
  );
}
