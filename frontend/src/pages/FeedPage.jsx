import PostList from "../components/PostList";

export default function FeedPage({
  activeFeedTab,
  setActiveFeedTab,
  postContent,
  setPostContent,
  postSourcesInput,
  setPostSourcesInput,
  postTopicTags,
  setPostTopicTags,
  topicTagOptions,
  onCreatePost,
  onRefreshFeed,
  timeline,
  timelineLoading,
  hasMoreTimeline,
  forYou,
  myPosts,
  myPostsLoading,
  myPostsPage,
  myPostsTotalPages,
  hasMoreMyPosts,
  likePending,
  dislikePending,
  onToggleLike,
  onToggleDislike,
  onOpenProfile,
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
  onLoadMoreTimeline,
  onLoadMoreMyPosts
}) {
  const handleTopicTagToggle = (tagValue) => {
    const alreadySelected = postTopicTags.includes(tagValue);
    if (alreadySelected) {
      setPostTopicTags(postTopicTags.filter((tag) => tag !== tagValue));
      return;
    }

    if (postTopicTags.length >= 3) {
      return;
    }

    setPostTopicTags([...postTopicTags, tagValue]);
  };

  const handleMyPostsScroll = (event) => {
    if (activeFeedTab === "timeline") {
      if (timelineLoading || !hasMoreTimeline) return;

      const target = event.currentTarget;
      const nearBottom = target.scrollTop + target.clientHeight >= target.scrollHeight - 80;
      if (nearBottom) {
        onLoadMoreTimeline?.();
      }
      return;
    }

    if (activeFeedTab !== "my-posts") return;
    if (myPostsLoading || !hasMoreMyPosts) return;

    const target = event.currentTarget;
    const nearBottom = target.scrollTop + target.clientHeight >= target.scrollHeight - 80;
    if (nearBottom) {
      onLoadMoreMyPosts?.();
    }
  };

  return (
    <div className="grid gap-6 md:grid-cols-3">
      <section className="md:col-span-1 rounded-3xl border border-slate-800 bg-slate-900/90 p-6 shadow-lg">
        <h2 className="mb-2 text-xl font-bold text-primary-700">Create Post</h2>
        <p className="mb-4 text-sm text-slate-400">Share a quick update with your followers. At least one source is required.</p>
        <form onSubmit={onCreatePost} className="space-y-4">
          <textarea
            rows={4}
            maxLength={1000}
            placeholder="What are you thinking?"
            value={postContent}
            onChange={(event) => setPostContent(event.target.value)}
            required
            className="min-h-28 rounded-xl"
          />
          <input
            type="text"
            placeholder="Sources (required, comma-separated, max 3)"
            value={postSourcesInput}
            onChange={(event) => setPostSourcesInput(event.target.value)}
            required
            className="rounded-xl"
          />
          <div>
            <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-400">Topic tags (choose up to 3)</p>
            <div className="flex flex-wrap gap-2">
              {topicTagOptions?.map((tag) => {
                const selected = postTopicTags.includes(tag.value);
                return (
                  <button
                    key={tag.value}
                    type="button"
                    onClick={() => handleTopicTagToggle(tag.value)}
                    className={`rounded-full px-3 py-1.5 text-xs font-semibold transition ${selected
                        ? "bg-emerald-500 text-slate-50"
                        : "border border-slate-700 bg-slate-800 text-slate-300 hover:bg-slate-700"
                      }`}
                  >
                    {tag.label}
                  </button>
                );
              })}
            </div>
          </div>
            <p className="text-xs text-slate-500">Any valid source URL is allowed. Trusted research domains receive an initial credibility boost.</p>
          <button type="submit" className="w-full rounded-xl bg-primary-500 py-3 text-sm font-semibold text-slate-50 hover:bg-primary-400 active:bg-primary-500/80 transition sm:w-auto sm:px-8">
            Publish
          </button>
        </form>
      </section>

      <section className="md:col-span-2 rounded-3xl border border-slate-800 bg-slate-900/90 p-6 shadow-lg">
        <div className="mb-5 flex items-center justify-between">
          <div>
            <h2 className="text-xl font-bold text-slate-50">Feed</h2>
            <p className="text-sm text-slate-400">Timeline and your posts are separated.</p>
          </div>
          <button
            type="button"
            className="rounded-xl border border-slate-700 bg-slate-800 px-5 py-2.5 text-sm font-semibold text-slate-100 hover:bg-slate-700 transition"
            onClick={onRefreshFeed}
          >
            Refresh
          </button>
        </div>

        <div className="mb-5 flex gap-3">
          <button
            type="button"
            className={`rounded-xl px-5 py-2.5 text-sm font-semibold transition ${
              activeFeedTab === "timeline"
                ? "bg-primary-500 text-slate-50 shadow-md"
                : "border border-slate-700 bg-slate-800 text-slate-200 hover:bg-slate-700"
            }`}
            onClick={() => setActiveFeedTab("timeline")}
          >
            Timeline
          </button>
          <button
            type="button"
            className={`rounded-xl px-5 py-2.5 text-sm font-semibold transition ${
              activeFeedTab === "for-you"
                ? "bg-primary-500 text-slate-50 shadow-md"
                : "border border-slate-700 bg-slate-800 text-slate-200 hover:bg-slate-700"
            }`}
            onClick={() => setActiveFeedTab("for-you")}
          >
            For You
          </button>
          <button
            type="button"
            className={`rounded-xl px-5 py-2.5 text-sm font-semibold transition ${
              activeFeedTab === "my-posts"
                ? "bg-primary-500 text-slate-50 shadow-md"
                : "border border-slate-700 bg-slate-800 text-slate-200 hover:bg-slate-700"
            }`}
            onClick={() => setActiveFeedTab("my-posts")}
          >
            My Posts
          </button>
        </div>

        <div className="h-[560px] overflow-y-auto pr-1" onScroll={handleMyPostsScroll}>
          {activeFeedTab === "timeline" ? (
            <div className="space-y-3">
              {timelineLoading && timeline.length === 0 ? (
                <p className="text-sm text-slate-400">Loading timeline...</p>
              ) : (
                <PostList
                  posts={timeline}
                  emptyLabel="No timeline posts yet."
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

              {timelineLoading && timeline.length > 0 && (
                <p className="text-center text-xs text-slate-500">Loading more timeline posts...</p>
              )}

              {!timelineLoading && !hasMoreTimeline && timeline.length > 0 && (
                <p className="text-center text-xs text-slate-500">You have reached the end of the timeline.</p>
              )}
            </div>
          ) : activeFeedTab === "for-you" ? (
            <PostList
              posts={forYou}
              emptyLabel="No personalized posts yet."
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
          ) : (
            <div className="space-y-3">
              <div className="text-sm text-slate-400">
                Loaded {myPosts.length} posts
              </div>

              {myPostsLoading ? (
                myPosts.length === 0 ? (
                  <p className="text-sm text-slate-400">Loading your posts...</p>
                ) : (
                  <PostList
                    posts={myPosts}
                    emptyLabel="You have not posted anything yet."
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
                  posts={myPosts}
                  emptyLabel="You have not posted anything yet."
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

              {myPostsLoading && myPosts.length > 0 && (
                <p className="text-center text-xs text-slate-500">Loading more posts...</p>
              )}

              {!myPostsLoading && !hasMoreMyPosts && myPostsTotalPages > 0 && (
                <p className="text-center text-xs text-slate-500">You have reached the end of your posts.</p>
              )}
            </div>
          )}
        </div>
      </section>
    </div>
  );
}
