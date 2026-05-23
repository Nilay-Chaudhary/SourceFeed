export function getPostTitle(item) {
  const displayName = (item.displayName || "").trim();
  if (displayName) return displayName;
  const username = (item.username || "").trim();
  if (username) return `@${username}`;
  if (item.userId != null) return `User #${item.userId}`;
  return "Unknown user";
}

export function getUserInitial(item) {
  const displayName = (item.displayName || "").trim();
  if (displayName) return displayName[0].toUpperCase();
  const username = (item.username || "").trim();
  if (username) return username[0].toUpperCase();
  return "U";
}
