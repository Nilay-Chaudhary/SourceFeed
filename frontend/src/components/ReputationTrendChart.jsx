import {
  CategoryScale,
  Chart as ChartJS,
  Filler,
  LineElement,
  LinearScale,
  PointElement,
  Tooltip,
  Legend
} from "chart.js";
import { Line } from "react-chartjs-2";

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Tooltip, Legend, Filler);

const SERIES_COLORS = [
  "#f59e0b",
  "#22c55e",
  "#38bdf8",
  "#f97316",
  "#eab308",
  "#14b8a6",
  "#a78bfa",
  "#fb7185"
];

function toNumber(value, fallback = 0) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function formatTimestampLabel(timestamp, options = {}) {
  const date = new Date(timestamp);
  if (Number.isNaN(date.getTime())) {
    return "";
  }

  return new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    ...options
  }).format(date);
}

function uniqueSortedTimestamps(seriesList) {
  const set = new Set();
  seriesList.forEach((series) => series.points.forEach((point) => set.add(new Date(point.recordedAt).getTime())));
  return Array.from(set).sort((a, b) => a - b);
}

function buildAlignedSeries(points, allTimestamps) {
  const map = new Map(points.map((point) => [new Date(point.recordedAt).getTime(), toNumber(point.score, 0)]));
  const result = [];
  let last = null;

  for (const timestamp of allTimestamps) {
    if (map.has(timestamp)) {
      last = map.get(timestamp);
      result.push(last);
    } else {
      result.push(last == null ? 0 : last);
    }
  }

  return result;
}

export default function ReputationTrendChart({ history, maxTopics = 5 }) {
  const overallRaw = Array.isArray(history?.overall) ? history.overall : [];
  const topicsRaw = Array.isArray(history?.topics) ? history.topics : [];
  const topicCandidates = topicsRaw.slice(0, maxTopics);

  if (overallRaw.length === 0 && topicCandidates.length === 0) {
    return null;
  }

  const seriesDefs = [
    { name: "Overall", points: overallRaw },
    ...topicCandidates.map((topic) => ({ name: topic.topicTag, points: topic.points }))
  ];

  const allTimestamps = uniqueSortedTimestamps(seriesDefs);
  if (allTimestamps.length === 0) {
    return null;
  }

  const labels = allTimestamps.map((timestamp) => formatTimestampLabel(timestamp));
  const datasets = seriesDefs.map((series, index) => ({
    label: series.name,
    data: buildAlignedSeries(series.points, allTimestamps),
    borderColor: SERIES_COLORS[index % SERIES_COLORS.length],
    backgroundColor: `${SERIES_COLORS[index % SERIES_COLORS.length]}33`,
    pointBackgroundColor: SERIES_COLORS[index % SERIES_COLORS.length],
    pointBorderColor: SERIES_COLORS[index % SERIES_COLORS.length],
    pointRadius: 2,
    pointHoverRadius: 4,
    borderWidth: 2,
    tension: 0.25,
    fill: false
  }));

  const data = {
    labels,
    datasets
  };

  const options = {
    responsive: true,
    maintainAspectRatio: false,
    interaction: {
      mode: "index",
      intersect: false
    },
    plugins: {
      legend: {
        position: "top",
        labels: {
          color: "#cbd5e1",
          usePointStyle: true,
          pointStyle: "line"
        }
      },
      tooltip: {
        backgroundColor: "#020617",
        borderColor: "#334155",
        borderWidth: 1,
        titleColor: "#f8fafc",
        bodyColor: "#cbd5e1",
        callbacks: {
          title(items) {
            const index = items[0]?.dataIndex ?? 0;
            const timestamp = allTimestamps[index];
            return formatTimestampLabel(timestamp, {
              year: "numeric"
            });
          },
          label(context) {
            const value = toNumber(context.parsed.y, 0);
            return `${context.dataset.label}: ${value.toFixed(2)}`;
          }
        }
      }
    },
    scales: {
      x: {
        ticks: {
          color: "#94a3b8",
          maxRotation: 0,
          autoSkip: true,
          maxTicksLimit: 6
        },
        grid: {
          color: "rgba(148, 163, 184, 0.12)"
        }
      },
      y: {
        ticks: {
          color: "#94a3b8"
        },
        grid: {
          color: "rgba(148, 163, 184, 0.12)"
        }
      }
    }
  };

  return (
    <div className="mt-5 rounded-2xl border border-slate-800 bg-slate-950/60 p-4">
      <div className="mb-3 flex items-center justify-between gap-2">
        <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">Credibility Trend</p>
        <span className="text-[11px] text-slate-500">Points: {allTimestamps.length}</span>
      </div>

      <div className="h-80 w-full">
        <Line data={data} options={options} />
      </div>
    </div>
  );
}
