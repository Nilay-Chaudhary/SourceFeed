import { getPostTitle, getUserInitial } from "../utils/post";
import { formatLocalDateTime, formatRelativeTime } from "../utils/time";
import { FaChevronDown, FaChevronUp, FaCommentDots, FaTimes, FaThumbsDown, FaThumbsUp } from "react-icons/fa";

function getVerdictBadge(verdict) {
  switch ((verdict || "PROCESSING").toUpperCase()) {
    case "STRONGLY_SUPPORTED":
      return { label: "Strongly supported", className: "border-emerald-700/70 bg-emerald-900/40 text-emerald-300" };
    case "WEAKLY_SUPPORTED":
      return { label: "Weakly supported", className: "border-amber-700/70 bg-amber-900/30 text-amber-300" };
    case "CONTRADICTORY":
      return { label: "Contradictory", className: "border-rose-700/70 bg-rose-900/30 text-rose-300" };
    case "COULD_NOT_PROCESS":
      return { label: "Could not process", className: "border-slate-700 bg-slate-800 text-slate-300" };
    default:
      return { label: "Processing", className: "border-sky-700/70 bg-sky-900/30 text-sky-300" };
  }
}

export default function PostList({
  posts,
  emptyLabel,
  likePending,
  dislikePending,
  onToggleLike,
  onToggleDislike,
  onOpenProfile,
  counterpointsByParentId = {},
  expandedCounterpointsByParentId = {},
  counterpointsLoadingByParentId = {},
  counterpointComposerPostId = null,
  counterpointContent = "",
  setCounterpointContent,
  counterpointSourcesInput = "",
  setCounterpointSourcesInput,
  onToggleCounterpoints,
  onOpenCounterpointComposer,
  onCreateCounterpoint,
  depth = 0
}) {
  if (!posts.length) {
    return <p className="text-sm text-slate-400">{emptyLabel}</p>;
  }

  return (
    <ul className="space-y-4">
      {posts.map((item) => {
        const isRootPost = item.parentPostId == null;
        const verdictBadge = getVerdictBadge(item.aiVerdict);

        return (
        <li
          key={`${item.id}-${item.createdAt}`}
          className={`rounded-2xl border border-slate-800 bg-slate-900/90 p-5 shadow-md transition hover:-translate-y-1 hover:shadow-lg ${depth > 0 ? "ml-4 border-slate-700 bg-slate-950/70" : ""}`}
        >
          <div className="mb-3 flex items-start justify-between gap-3">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-full bg-gradient-to-br from-primary-500 to-accent-500 text-sm font-bold text-slate-50 shadow-md">
                {getUserInitial(item)}
              </div>
              <div onClick={() => onOpenProfile?.(item.userId)} className="cursor-pointer">
                <div className="text-left text-sm font-semibold transition">
                  {getPostTitle(item)}
                </div>
                <p className="text-xs text-slate-400">@{item.username || "unknown"}</p>
                {item.parentPostId != null && (
                  <span className="mt-1 inline-flex rounded-full border border-emerald-700/70 bg-emerald-900/30 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-emerald-300">
                    Comment
                  </span>
                )}
              </div>
            </div>
          </div>
          <p className="mb-3 text-sm leading-relaxed text-slate-100">{item.content}</p>
          {Array.isArray(item.sources) && item.sources.length > 0 && (
            <div className="mb-3 flex flex-wrap gap-2">
              {item.sources.slice(0, 3).map((source) => (
                <span
                  key={`${item.id}-${source}`}
                  className="inline-flex rounded-full border border-slate-700 bg-slate-800 px-3 py-1 text-[11px] font-semibold text-slate-300"
                >
                  {source}
                </span>
              ))}
            </div>
          )}
          {Array.isArray(item.topicTags) && item.topicTags.length > 0 && (
            <div className="mb-3 flex flex-wrap gap-2">
              {item.topicTags.map((tag) => (
                <span
                  key={`${item.id}-tag-${tag}`}
                  className="inline-flex rounded-full border border-emerald-700/70 bg-emerald-900/40 px-3 py-1 text-[11px] font-semibold text-emerald-300"
                >
                  {tag}
                </span>
              ))}
            </div>
          )}
          {item.relevanceScore != null && (
            <div className="mb-3 inline-flex rounded-full border border-slate-700 bg-slate-800 px-3 py-1 text-[11px] font-semibold uppercase tracking-wide text-slate-300">
              Relevance {(Number(item.relevanceScore) * 100).toFixed(0)}%
            </div>
          )}
          <div className={`mb-3 inline-flex rounded-full border px-3 py-1 text-[11px] font-semibold uppercase tracking-wide ${verdictBadge.className}`}>
            AI {verdictBadge.label}
          </div>
          <div className="mt-3 flex items-center justify-between text-xs text-slate-400">
            <span title={`${formatLocalDateTime(item.createdAt)} IST`}>
              {formatRelativeTime(item.createdAt)} · {formatLocalDateTime(item.createdAt)} IST
            </span>
            <div className="flex items-center gap-2">
              <span className="rounded-full border border-slate-700 bg-slate-800 px-2.5 py-1 text-[11px] font-semibold text-slate-300">
                Credibility {Number(item.postTrustScore || 0).toFixed(1)}
              </span>
              <button
                type="button"
                onClick={() => onToggleLike(item.id, Boolean(item.likedByMe))}
                disabled={Boolean(likePending[item.id]) || Boolean(dislikePending[item.id])}
                className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs font-semibold transition ${item.likedByMe
                    ? "bg-primary-500 text-slate-50 hover:bg-primary-400 shadow-md"
                    : "border border-slate-700 bg-slate-800 text-slate-200 hover:bg-slate-700"
                  }`}
              >
                <FaThumbsUp aria-hidden="true" className="h-4 w-4" />
                <span>{Number(item.likeCount || 0)}</span>
                <span className="sr-only">{item.likedByMe ? "Unlike" : "Like"}</span>
              </button>
              <button
                type="button"
                onClick={() => onToggleDislike(item.id, Boolean(item.dislikedByMe))}
                disabled={Boolean(likePending[item.id]) || Boolean(dislikePending[item.id])}
                className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs font-semibold transition ${item.dislikedByMe
                    ? "bg-rose-500 text-slate-50 hover:bg-rose-400 shadow-md"
                    : "border border-slate-700 bg-slate-800 text-slate-200 hover:bg-slate-700"
                  }`}
              >
                <FaThumbsDown aria-hidden="true" className="h-4 w-4" />
                <span>{Number(item.dislikeCount || 0)}</span>
                <span className="sr-only">{item.dislikedByMe ? "Undo dislike" : "Dislike"}</span>
              </button>
            </div>
          </div>

          {depth === 0 && isRootPost && (
            <div className="mt-4 flex flex-wrap gap-2">
              <button
                type="button"
                onClick={() => onToggleCounterpoints?.(item.id)}
                className="inline-flex items-center gap-1.5 rounded-full border border-slate-700 bg-slate-800 px-3 py-1.5 text-xs font-semibold text-slate-200 transition hover:bg-slate-700"
              >
                {expandedCounterpointsByParentId[item.id] ? <FaChevronUp aria-hidden="true" /> : <FaChevronDown aria-hidden="true" />}
                Comments
              </button>
              <button
                type="button"
                onClick={() => onOpenCounterpointComposer?.(item.id)}
                className="inline-flex items-center gap-1.5 rounded-full border border-emerald-700/70 bg-emerald-900/30 px-3 py-1.5 text-xs font-semibold text-emerald-300 transition hover:bg-emerald-900/50"
              >
                <FaCommentDots aria-hidden="true" />
                Add comment
              </button>
            </div>
          )}

          {depth === 0 && isRootPost && counterpointComposerPostId === item.id && (
            <form onSubmit={onCreateCounterpoint} className="mt-4 space-y-3 rounded-2xl border border-slate-800 bg-slate-950/70 p-4">
              <div className="flex items-center justify-between gap-2">
                <div>
                  <p className="text-sm font-semibold text-slate-100">Write a short comment</p>
                  <p className="text-xs text-slate-400">Up to 300 characters. Uses this post&apos;s topic tags automatically.</p>
                </div>
                <button
                  type="button"
                  onClick={() => onOpenCounterpointComposer?.(null)}
                  className="rounded-full border border-slate-700 bg-slate-800 p-2 text-slate-300 transition hover:bg-slate-700"
                  aria-label="Close comment composer"
                >
                  <FaTimes aria-hidden="true" />
                </button>
              </div>
              <textarea
                rows={3}
                maxLength={300}
                placeholder="Add your comment..."
                value={counterpointContent}
                onChange={(event) => setCounterpointContent?.(event.target.value)}
                className="min-h-24 rounded-xl"
                required
              />
              <input
                type="text"
                placeholder="Sources (required, comma-separated, max 3)"
                value={counterpointSourcesInput}
                onChange={(event) => setCounterpointSourcesInput?.(event.target.value)}
                className="rounded-xl"
                required
              />
              <div className="flex gap-2">
                <button type="submit" className="rounded-xl bg-emerald-500 px-4 py-2.5 text-sm font-semibold text-slate-50 transition hover:bg-emerald-400">
                  Publish comment
                </button>
              </div>
            </form>
          )}

          {depth === 0 && isRootPost && expandedCounterpointsByParentId[item.id] && (
            <div className="mt-4 border-l border-slate-700 pl-4">
              <div className="max-h-80 overflow-y-auto pr-2">
                {counterpointsLoadingByParentId[item.id] ? (
                  <p className="text-xs text-slate-400">Loading comments...</p>
                ) : (
                  <>
                    {Array.isArray(counterpointsByParentId[item.id]) && counterpointsByParentId[item.id].length > 0 ? (
                      <PostList
                        posts={counterpointsByParentId[item.id]}
                        emptyLabel="No comments yet."
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
                        depth={1}
                      />
                    ) : (
                      <p className="text-xs text-slate-400">No comments yet.</p>
                    )}
                  </>
                )}
              </div>
            </div>
          )}
        </li>
        );
      })}
    </ul>
  );
}
