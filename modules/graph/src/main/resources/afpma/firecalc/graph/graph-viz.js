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
var graphVizPlugin = {
  id: "graphVizPlugin",
  beforeDraw(chart) {
    const bands = chart._backgroundBands;
    if (!bands || bands.length === 0) return;
    const { ctx, chartArea, scales } = chart;
    if (!chartArea || !scales.x) return;
    const xScale = scales.x;
    ctx.save();
    for (const band of bands) {
      const x1 = Math.max(xScale.getPixelForValue(band.xStart), chartArea.left);
      const x2 = Math.min(xScale.getPixelForValue(band.xEnd), chartArea.right);
      if (x2 <= x1) continue;
      ctx.fillStyle = band.color;
      ctx.fillRect(x1, chartArea.top, x2 - x1, chartArea.bottom - chartArea.top);
    }
    ctx.restore();
  }
};
function initGraphViz(container, data, config) {
  const canvas = document.createElement("canvas");
  container.appendChild(canvas);
  const chartConfig = buildChartConfig(data, config);
  const chart = new Chart(canvas, chartConfig);
  chart._backgroundBands = data.backgroundBands;
  return {
    dispose() {
      chart.destroy();
      canvas.remove();
    },
    update(newData) {
      chart.data.datasets = newData.series.map(seriesToDataset);
      chart._backgroundBands = newData.backgroundBands;
      const scales = chart.options.scales;
      for (const key of Object.keys(scales)) {
        if (key !== "x") {
          delete scales[key];
        }
      }
      if (scales.x && scales.x.title) {
        scales.x.title.text = newData.xAxisLabel;
      }
      newData.yAxes.forEach((axis, i) => {
        scales[axis.id] = buildYAxisScale(axis, i === 0);
      });
      chart.update();
    }
  };
}
var Y_AXIS_TICK_COUNT = 11;
function buildYAxisScale(axis, isPrimary) {
  const ticks = axis.stepSize !== void 0 ? { stepSize: axis.stepSize } : { count: Y_AXIS_TICK_COUNT };
  return {
    type: "linear",
    position: axis.position,
    title: {
      display: true,
      text: axis.label
    },
    border: {
      dash: (ctx) => ctx.tick.value === 0 ? [] : [4, 4]
    },
    grid: {
      drawOnChartArea: true,
      color: (ctx) => ctx.tick.value === 0 ? "rgba(0, 0, 0, 0.35)" : isPrimary ? "rgba(0, 0, 0, 0.1)" : "transparent",
      lineWidth: (ctx) => ctx.tick.value === 0 ? 2 : 1
    },
    ticks,
    ...axis.min !== void 0 && { min: axis.min },
    ...axis.max !== void 0 && { max: axis.max }
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
  data.yAxes.forEach((axis, i) => {
    scales[axis.id] = buildYAxisScale(axis, i === 0);
  });
  return {
    type: "line",
    data: {
      datasets
    },
    plugins: [graphVizPlugin],
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
