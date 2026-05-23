import { useEffect, useRef, useState } from "react";
import { FaBell, FaTimes } from "react-icons/fa";
import { api } from "../api";
import { formatRelativeTime } from "../utils/time";
import { toast } from "react-toastify";

export default function NotificationBell({ token, unreadCount, setUnreadCount }) {
  const [isOpen, setIsOpen] = useState(false);
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const panelRef = useRef(null);
  const buttonRef = useRef(null);

  // Close panel when clicking outside
  useEffect(() => {
    function handleClickOutside(event) {
      if (
        panelRef.current &&
        !panelRef.current.contains(event.target) &&
        buttonRef.current &&
        !buttonRef.current.contains(event.target)
      ) {
        setIsOpen(false);
      }
    }

    if (isOpen) {
      document.addEventListener("mousedown", handleClickOutside);
      return () => document.removeEventListener("mousedown", handleClickOutside);
    }
  }, [isOpen]);

  // Load notifications when panel opens
  useEffect(() => {
    if (isOpen && notifications.length === 0) {
      loadNotifications();
    }
  }, [isOpen]);

  async function loadNotifications() {
    if (loading) return;

    try {
      setLoading(true);
      setError("");
      const data = await api.getUnreadNotifications(token);
      setNotifications(Array.isArray(data) ? data : []);
    } catch (err) {
      const msg = err.message || "Failed to load notifications";
      setError(msg);
      console.error(msg);
    } finally {
      setLoading(false);
    }
  }

  async function handleMarkAsRead(notificationId) {
    try {
      await api.markNotificationAsRead(notificationId, token);
      setNotifications((prev) => prev.filter((n) => n.id !== notificationId));
      setUnreadCount((prev) => Math.max(0, prev - 1));
    } catch (err) {
      const msg = err.message || "Failed to mark as read";
      toast.error(msg);
      console.error(msg);
    }
  }

  async function handleMarkAllAsRead() {
    try {
      await api.markAllNotificationsAsRead(token);
      setNotifications([]);
      setUnreadCount(0);
    } catch (err) {
      const msg = err.message || "Failed to mark all as read";
      toast.error(msg);
      console.error(msg);
    }
  }

  const getNotificationMessage = (notification) => {
    switch (notification.type) {
      case "COUNTERPOINT_CREATED":
        return `💬 ${notification.message}`;
      case "POST_LIKED":
        return `👍 ${notification.message}`;
      case "USER_FOLLOWED":
        return `👥 ${notification.message}`;
      default:
        return notification.message;
    }
  };

  return (
    <div className="relative">
      {/* Bell Button */}
      <button
        ref={buttonRef}
        type="button"
        onClick={() => setIsOpen(!isOpen)}
        className="relative inline-flex items-center justify-center rounded-full bg-slate-800 p-2.5 text-slate-200 transition hover:bg-slate-700"
        aria-label="Notifications"
      >
        <FaBell className="h-5 w-5" aria-hidden="true" />
        {/* Unread Badge */}
        {unreadCount > 0 && (
          <span className="absolute -right-1 -top-1 inline-flex min-w-max items-center justify-center rounded-full bg-rose-500 px-2 py-0.5 text-xs font-bold text-slate-50 shadow-md">
            {unreadCount > 99 ? "99+" : unreadCount}
          </span>
        )}
      </button>

      {/* Notification Panel */}
      {isOpen && (
        <div
          ref={panelRef}
          className="absolute right-0 top-full z-50 mt-2 w-96 max-w-[calc(100vw-2rem)] rounded-2xl border border-slate-700 bg-slate-900/95 p-4 shadow-xl backdrop-blur"
        >
          {/* Header */}
          <div className="mb-4 flex items-center justify-between border-b border-slate-700 pb-3">
            <h3 className="text-sm font-semibold text-slate-100">Notifications</h3>
            {notifications.length > 0 && (
              <button
                type="button"
                onClick={handleMarkAllAsRead}
                className="text-xs font-semibold text-primary-400 hover:text-primary-300 transition"
              >
                Mark all as read
              </button>
            )}
          </div>

          {/* Content */}
          {loading ? (
            <p className="text-center text-sm text-slate-400">Loading...</p>
          ) : error ? (
            <p className="text-center text-sm text-rose-400">{error}</p>
          ) : notifications.length > 0 ? (
            <ul className="space-y-2 max-h-96 overflow-y-auto">
              {notifications.map((notification) => (
                <li
                  key={notification.id}
                  className="group flex items-start gap-3 rounded-lg border border-slate-700/50 bg-slate-950/50 p-3 transition hover:bg-slate-950 hover:border-slate-600"
                >
                  <div className="flex-1 text-sm">
                    <p className="text-slate-100">{getNotificationMessage(notification)}</p>
                    <p className="mt-1 text-xs text-slate-500">
                      {formatRelativeTime(notification.createdAt)}
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() => handleMarkAsRead(notification.id)}
                    className="flex-shrink-0 rounded-full bg-slate-800 p-1.5 text-slate-400 opacity-0 transition group-hover:opacity-100 hover:bg-slate-700 hover:text-slate-200"
                    aria-label="Mark as read"
                  >
                    <FaTimes className="h-3.5 w-3.5" aria-hidden="true" />
                  </button>
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-center text-sm text-slate-400">No new notifications</p>
          )}
        </div>
      )}
    </div>
  );
}
