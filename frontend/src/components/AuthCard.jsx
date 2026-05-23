export default function AuthCard({
  mode,
  authForm,
  loading,
  error,
  onModeChange,
  onSubmit,
  onFormChange,
  appName,
  appTagline
}) {
  return (
    <div className="mx-auto mt-16 w-full max-w-md rounded-3xl border border-slate-800 bg-slate-900/90 p-8 shadow-2xl backdrop-blur">
      <div className="mb-8 text-center">
        <h1 className="text-4xl font-bold tracking-tight text-slate-50">{appName}</h1>
        <p className="mt-3 text-sm text-slate-400">{appTagline}</p>
      </div>

      <div className="mb-6 flex gap-2">
        <button
          type="button"
          className={`flex-1 rounded-xl px-3 py-2.5 text-sm font-semibold transition ${
            mode === "login"
              ? "bg-primary-500 text-slate-50 shadow-md"
              : "border border-slate-700 bg-slate-800 text-slate-200 hover:bg-slate-700"
          }`}
          onClick={() => onModeChange("login")}
        >
          Login
        </button>
        <button
          type="button"
          className={`flex-1 rounded-xl px-3 py-2.5 text-sm font-semibold transition ${
            mode === "register"
              ? "bg-primary-500 text-slate-50 shadow-md"
              : "border border-slate-700 bg-slate-800 text-slate-200 hover:bg-slate-700"
          }`}
          onClick={() => onModeChange("register")}
        >
          Register
        </button>
      </div>

      <form onSubmit={onSubmit} className="space-y-3">
        <input
          placeholder="Username"
          value={authForm.username}
          onChange={(event) => onFormChange({ ...authForm, username: event.target.value })}
          required
        />

        {mode === "register" && (
          <>
            <input
              placeholder="Display name"
              value={authForm.displayName}
              onChange={(event) => onFormChange({ ...authForm, displayName: event.target.value })}
            />
            <input
              type="email"
              placeholder="Email"
              value={authForm.email}
              onChange={(event) => onFormChange({ ...authForm, email: event.target.value })}
              required
            />
          </>
        )}

        <input
          type="password"
          placeholder="Password"
          value={authForm.password}
          onChange={(event) => onFormChange({ ...authForm, password: event.target.value })}
          required
        />

        <button type="submit" disabled={loading} className="w-full">
          {loading ? "Please wait..." : mode === "register" ? "Create account" : "Login"}
        </button>
      </form>

      {error && <p className="mt-4 text-sm font-medium text-red-400">{error}</p>}
    </div>
  );
}
