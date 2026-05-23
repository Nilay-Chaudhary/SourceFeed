export function parseServerTimestamp(value) {
  if (!value) return new Date(NaN);
  if (value instanceof Date) return value;

  const raw = String(value).trim();
  const hasTimeZone = /([zZ]|[+-]\d{2}:?\d{2})$/.test(raw);
  return new Date(hasTimeZone ? raw : `${raw}Z`);
}

export function formatRelativeTime(value) {
  const date = parseServerTimestamp(value);
  if (Number.isNaN(date.getTime())) return "Unknown time";

  const diffMs = date.getTime() - Date.now();
  const absMs = Math.abs(diffMs);
  const rtf = new Intl.RelativeTimeFormat(undefined, { numeric: "auto" });

  if (absMs < 60_000) return rtf.format(Math.round(diffMs / 1000), "second");
  if (absMs < 3_600_000) return rtf.format(Math.round(diffMs / 60_000), "minute");
  if (absMs < 86_400_000) return rtf.format(Math.round(diffMs / 3_600_000), "hour");
  return rtf.format(Math.round(diffMs / 86_400_000), "day");
}

export function formatLocalDateTime(value) {
  const date = parseServerTimestamp(value);
  if (Number.isNaN(date.getTime())) return "Unknown time";

  return new Intl.DateTimeFormat(undefined, {
    weekday: "short",
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
    hour12: true,
    timeZone: "Asia/Kolkata"
  }).format(date);
}
