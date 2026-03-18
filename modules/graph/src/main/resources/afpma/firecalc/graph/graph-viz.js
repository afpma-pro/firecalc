/* SPDX-License-Identifier: AGPL-3.0-or-later | Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal */

// ../graph/src/ts/graph-viz.ts
import {
  Chart,
  LineController,
  LineElement,
  PointElement,
  LinearScale,
  Tooltip,
  Legend,
  Filler
} from "chart.js";
Chart.register(
  LineController,
  LineElement,
  PointElement,
  LinearScale,
  Tooltip,
  Legend,
  Filler
);
function initGraphViz(container, data, config) {
  const canvas = document.createElement("canvas");
  container.appendChild(canvas);
  const chartConfig = buildChartConfig(data, config);
  const chart = new Chart(canvas, chartConfig);
  return {
    dispose() {
      chart.destroy();
      canvas.remove();
    },
    update(newData) {
      chart.data.datasets = newData.series.map(seriesToDataset);
      const scales = chart.options.scales;
      for (const key of Object.keys(scales)) {
        if (key !== "x") {
          delete scales[key];
        }
      }
      if (scales.x && scales.x.title) {
        scales.x.title.text = newData.xAxisLabel;
      }
      for (const axis of newData.yAxes) {
        scales[axis.id] = {
          type: "linear",
          position: axis.position,
          title: {
            display: true,
            text: axis.label
          },
          ...axis.min !== void 0 && { min: axis.min },
          ...axis.max !== void 0 && { max: axis.max }
        };
      }
      chart.update();
    }
  };
}
function seriesToDataset(s) {
  return {
    label: s.name,
    data: s.points,
    // DataPointJS objects (Chart.js reads x/y + we access extras in callbacks)
    borderColor: s.color,
    backgroundColor: s.color + "20",
    // 12% opacity fill
    borderWidth: s.lineWidth,
    borderDash: s.dashed ? [6, 3] : [],
    yAxisID: s.yAxisId,
    pointRadius: 2,
    pointHoverRadius: 4,
    tension: 0,
    // no curve smoothing
    fill: false
  };
}
function buildChartConfig(data, config) {
  const datasets = data.series.map(seriesToDataset);
  const scales = {
    x: {
      type: "linear",
      title: {
        display: true,
        text: data.xAxisLabel
      },
      ticks: {
        maxRotation: 0
      }
    }
  };
  for (const axis of data.yAxes) {
    scales[axis.id] = {
      type: "linear",
      position: axis.position,
      title: {
        display: true,
        text: axis.label
      },
      ...axis.min !== void 0 && { min: axis.min },
      ...axis.max !== void 0 && { max: axis.max }
    };
  }
  return {
    type: "line",
    data: {
      datasets
    },
    options: {
      responsive: config.responsive,
      maintainAspectRatio: config.maintainAspectRatio,
      interaction: {
        mode: "index",
        intersect: false
      },
      plugins: {
        tooltip: {
          enabled: true,
          callbacks: {
            title(items) {
              if (!items.length) return "";
              const raw = items[0].raw;
              const lines = [];
              if (raw?.tooltipTitle) lines.push(raw.tooltipTitle);
              if (raw?.tooltipExtra) lines.push(raw.tooltipExtra);
              return lines;
            },
            label(item) {
              const raw = item.raw;
              const seriesName = item.dataset.label || "";
              const value = raw?.formattedValue || item.formattedValue;
              return `${seriesName}: ${value}`;
            }
          }
        },
        legend: {
          display: true,
          position: "top"
        }
      },
      scales
    }
  };
}
export {
  initGraphViz
};
