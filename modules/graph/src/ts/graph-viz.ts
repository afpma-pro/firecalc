/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

import {
    Chart,
    LineController,
    LineElement,
    PointElement,
    LinearScale,
    Tooltip,
    Legend,
    Filler,
    type ChartArea,
    type ChartConfiguration,
    type ChartDataset,
    type TooltipItem,
} from 'chart.js';

Chart.register(LineController, LineElement, PointElement, LinearScale, Tooltip, Legend, Filler);

// ── Interfaces mirroring the Scala.js facades (see GraphVizJS.scala) ──

interface DataPointJS {
    x:                number;
    y:                number;
    tooltipTitle:     string;
    tooltipExtra:     string;
    formattedValue:   string;
    segmentColor:     string;
    highlightTargets: string[];
}

interface ChartSeriesJS {
    id:        string;
    name:      string;
    color:     string;
    points:    DataPointJS[];
    yAxisId:   string;
    lineWidth: number;
    dashed:    boolean;
    /** Axis label shown when this series is the only visible one (focus mode). */
    soloAxisLabel: string;
}

interface YAxisConfigJS {
    id:                 string;
    label:              string;
    position:           'left' | 'right';
    min?:               number;
    max?:               number;
    stepSize?:          number;
    primaryGridStep?:   number;
    secondaryGridStep?: number;
    /** UI-only flag (never sent from Scala): set by focusYAxes() for non-soloed axes. */
    hidden?: boolean;
}

interface XSegmentJS { xStart: number; xEnd: number; }

interface HorizontalReferenceLineJS {
    yAxisId:              string;
    y:                    number;
    color:                string;
    label:                string;
    visibleWhenSeriesIds: string[];
    segments:             XSegmentJS[];
}

interface BackgroundBandJS {
    xStart: number;
    xEnd:   number;
    color:  string;
    label:  string;
}

interface ChartDataJS {
    series:          ChartSeriesJS[];
    yAxes:           YAxisConfigJS[];
    xAxisLabel:      string;
    backgroundBands: BackgroundBandJS[];
    horizontalLines: HorizontalReferenceLineJS[];
    xMin?:           number;
    xMax?:           number;
}

interface GraphConfigJS {
    responsive:          boolean;
    maintainAspectRatio: boolean;
}

interface GraphVizHandleJS {
    dispose(): void;
    update(data: ChartDataJS): void;
}

// ── Chart-attached state ──

/** One stash per chart, read by the plugin hooks. `effYAxes` equals `data.yAxes` when the
  * chart is not in focus mode, and is the focus-mode view (one re-ranged axis + the others
  * flagged `hidden`) when the user has soloed a series via the legend. */
interface VizState {
    data:     ChartDataJS;
    effYAxes: YAxisConfigJS[];
}

function viz(chart: Chart): VizState | undefined {
    return (chart as any)._viz;
}

function setViz(chart: Chart, state: VizState): void {
    (chart as any)._viz = state;
}

// ── Plugin ──

const GRID_PRIMARY_COLOR   = 'rgba(0, 0, 0, 0.18)';
const GRID_SECONDARY_COLOR = 'rgba(0, 0, 0, 0.08)';
const ZERO_LINE_COLOR      = 'rgba(0, 0, 0, 0.45)';

/** Paint horizontal gridlines at every multiple of `step` within `scale`'s current range.
  * Skips y=0 (native rendering draws the zero-line emphasis) and — when `skipAt` is passed —
  * any position that coincides with a `skipAt`-aligned tick, so dashed secondary lines don't
  * overlap solid primary ones. */
function drawGridlines(
    ctx:    CanvasRenderingContext2D,
    scale:  { min: number; max: number; getPixelForValue: (v: number) => number },
    area:   ChartArea,
    step:   number,
    color:  string,
    dash:   number[],
    skipAt?: number,
): void {
    ctx.save();
    ctx.strokeStyle = color;
    ctx.lineWidth   = 1;
    ctx.setLineDash(dash);
    const start = Math.ceil(scale.min / step) * step;
    for (let v = start; v <= scale.max + 1e-9; v += step) {
        if (Math.abs(v) < 1e-9) continue;
        if (skipAt && Math.abs(v / skipAt - Math.round(v / skipAt)) < 1e-9) continue;
        const y = scale.getPixelForValue(v);
        ctx.beginPath();
        ctx.moveTo(area.left,  y);
        ctx.lineTo(area.right, y);
        ctx.stroke();
    }
    ctx.restore();
}

function isSeriesVisible(chart: Chart, seriesId: string): boolean {
    const i = chart.data.datasets.findIndex(d => (d as any)._seriesId === seriesId);
    return i >= 0 && chart.isDatasetVisible(i);
}

const graphVizPlugin = {
    id: 'graphVizPlugin',

    /** Paint semi-transparent vertical background bands behind the chart area. */
    beforeDraw(chart: Chart) {
        const state = viz(chart);
        const { ctx, chartArea, scales } = chart;
        if (!state || !chartArea || !scales.x) return;
        const xScale = scales.x;
        ctx.save();
        for (const b of state.data.backgroundBands) {
            const x1 = Math.max(xScale.getPixelForValue(b.xStart), chartArea.left);
            const x2 = Math.min(xScale.getPixelForValue(b.xEnd),   chartArea.right);
            if (x2 <= x1) continue;
            ctx.fillStyle = b.color;
            ctx.fillRect(x1, chartArea.top, x2 - x1, chartArea.bottom - chartArea.top);
        }
        ctx.restore();
    },

    /** Paint primary (solid) and secondary (dashed) gridlines ourselves. Chart.js has no
      * per-gridline dash control, so mixing 1 px solid native lines with 1 px dashed
      * plugin lines at comparable opacities reads as "everything dashed". Doing both in one
      * pass with explicit `setLineDash` transitions guarantees a clear solid/dashed split.
      * Native Chart.js only strokes the zero-line emphasis — see `buildYAxisScale`. */
    beforeDatasetsDraw(chart: Chart) {
        const state = viz(chart);
        if (!state || !chart.chartArea) return;
        for (const axis of state.effYAxes) {
            if (axis.hidden) continue;
            const scale = chart.scales[axis.id] as any;
            if (!scale) continue;
            if (axis.primaryGridStep) {
                drawGridlines(chart.ctx, scale, chart.chartArea,
                    axis.primaryGridStep, GRID_PRIMARY_COLOR, []);
            }
            if (axis.secondaryGridStep) {
                drawGridlines(chart.ctx, scale, chart.chartArea,
                    axis.secondaryGridStep, GRID_SECONDARY_COLOR, [3, 3], axis.primaryGridStep);
            }
        }
    },

    /** Draw horizontal reference lines, clipped to their x-segments and gated by the
      * visibility of the associated series (e.g. velocity bounds only when velocity is shown).
      * A line whose y falls outside its axis's current range is suppressed — otherwise
      * Chart.js extrapolates and the line "floats" at the chart edge. */
    afterDatasetsDraw(chart: Chart) {
        const state = viz(chart);
        const { ctx, chartArea, scales } = chart;
        if (!state || !chartArea || !scales.x) return;
        const xScale = scales.x;

        for (const line of state.data.horizontalLines) {
            if (line.visibleWhenSeriesIds.length > 0 &&
                !line.visibleWhenSeriesIds.some(id => isSeriesVisible(chart, id))
            ) continue;

            const yScale = scales[line.yAxisId] as any;
            if (!yScale) continue;
            if (line.y < yScale.min || line.y > yScale.max) continue;

            const yPx = yScale.getPixelForValue(line.y);

            ctx.save();
            ctx.strokeStyle = line.color;
            ctx.lineWidth   = 1.5;
            ctx.setLineDash([]);

            let drewAny = false;
            for (const seg of line.segments) {
                const x1 = Math.max(chartArea.left,  xScale.getPixelForValue(seg.xStart));
                const x2 = Math.min(chartArea.right, xScale.getPixelForValue(seg.xEnd));
                if (x2 <= x1) continue;
                ctx.beginPath();
                ctx.moveTo(x1, yPx);
                ctx.lineTo(x2, yPx);
                ctx.stroke();
                drewAny = true;
            }

            // Label on the LEFT edge of the chart so "1.2 m/s" / "6 m/s" land next to the
            // m/s gridline labels on the pressure/velocity axis, not stranded over the
            // temperature axis on the right.
            if (line.label && drewAny) {
                ctx.fillStyle    = line.color;
                ctx.font         = '10px sans-serif';
                ctx.textAlign    = 'right';
                ctx.textBaseline = 'middle';
                ctx.fillText(line.label, chartArea.left - 5, yPx);
            }
            ctx.restore();
        }
    },
};

// ── Public API ──

export function initGraphViz(
    container:     HTMLElement,
    data:          ChartDataJS,
    config:        GraphConfigJS,
    onPointClick?: (targets: string[]) => void,
): GraphVizHandleJS {
    const canvas = document.createElement('canvas');
    container.appendChild(canvas);

    const chartConfig = buildChartConfig(data, config);

    if (onPointClick) {
        chartConfig.options!.onClick = (event: any, _els: any[], chart: any) => {
            const near = chart.getElementsAtEventForMode(event, 'nearest', { intersect: true }, false);
            if (near.length > 0) {
                const el  = near[0];
                const raw = chart.data.datasets[el.datasetIndex].data[el.index] as DataPointJS;
                onPointClick(raw?.highlightTargets ?? []);
            } else {
                onPointClick([]);
            }
        };
    }

    const chart = new Chart(canvas, chartConfig);
    setViz(chart, { data, effYAxes: data.yAxes });

    return {
        dispose() {
            chart.destroy();
            canvas.remove();
        },
        update(newData: ChartDataJS) {
            // A data update always exits focus mode — Chart.js rebuilds datasets so every
            // series is visible again — so effYAxes returns to the originals.
            chart.data.datasets = newData.series.map(seriesToDataset);
            setViz(chart, { data: newData, effYAxes: newData.yAxes });

            const xs = chart.options.scales!.x as any;
            if (xs) {
                if (xs.title) xs.title.text = newData.xAxisLabel;
                if (newData.xMin !== undefined) xs.min = newData.xMin; else delete xs.min;
                if (newData.xMax !== undefined) xs.max = newData.xMax; else delete xs.max;
            }
            applyYAxes(chart, newData.yAxes);
            chart.update();
        },
    };
}

// ── Internal helpers ──

const Y_AXIS_TICK_COUNT = 11;

/** Rebuild every y-axis scale from `axes`, preserving the x-axis. Single source of truth
  * for scale construction — called on init, on data update, and on focus-mode toggle. */
function applyYAxes(chart: Chart, axes: YAxisConfigJS[]): void {
    const scales = chart.options.scales!;
    for (const k of Object.keys(scales)) if (k !== 'x') delete scales[k];
    axes.forEach((a, i) => { scales[a.id] = buildYAxisScale(a, i === 0); });
}

/** Convert a YAxisConfigJS to a Chart.js linear-scale config.
  *
  * Grid ownership:
  *  - `primaryGridStep` set  → plugin paints primary + secondary; native returns
  *                              transparent/0 for non-zero (zero-line still native).
  *  - `primaryGridStep` unset→ only the first (primary) axis draws native non-zero
  *                              gridlines; secondary axes return transparent to avoid
  *                              stacking lines on top of the primary axis's lines.
  *
  * When `stepSize` is set we disable Chart.js's silent 11-tick cap (`maxTicksLimit`) and
  * label-skipping (`autoSkip`) so a right-axis tick count derived from the left primary
  * grid can legitimately exceed the default cap without breaking cross-axis alignment. */
function buildYAxisScale(axis: YAxisConfigJS, isPrimary: boolean): Record<string, any> {
    if (axis.hidden) {
        return {
            type:     'linear',
            position: axis.position,
            display:  false,
            grid:     { drawOnChartArea: false },
        };
    }

    const ticks = axis.stepSize !== undefined
        ? { stepSize: axis.stepSize, maxTicksLimit: Number.MAX_SAFE_INTEGER, autoSkip: false }
        : { count: Y_AXIS_TICK_COUNT };
    const pluginDrawsGrid = axis.primaryGridStep !== undefined;

    return {
        type:     'linear',
        position: axis.position,
        title:    { display: true, text: axis.label },
        border: {
            dash: (ctx: { tick: { value: number } }) => ctx.tick.value === 0 ? [] : [4, 4],
        },
        grid: {
            drawOnChartArea: true,
            color: (ctx: { tick: { value: number } }) => {
                if (ctx.tick.value === 0)          return ZERO_LINE_COLOR;
                if (pluginDrawsGrid || !isPrimary) return 'transparent';
                return 'rgba(0, 0, 0, 0.1)';
            },
            lineWidth: (ctx: { tick: { value: number } }) => {
                if (ctx.tick.value === 0) return 2;
                if (pluginDrawsGrid)      return 0;
                return 1;
            },
        },
        ticks,
        ...(axis.min !== undefined && { min: axis.min }),
        ...(axis.max !== undefined && { max: axis.max }),
    };
}

function seriesToDataset(s: ChartSeriesJS): ChartDataset<'line'> {
    const ds: ChartDataset<'line'> & { _seriesId?: string; _soloAxisLabel?: string } = {
        label:           s.name,
        data:            s.points as any[],
        borderColor:     s.color,
        backgroundColor: s.color + '20',
        borderWidth:     s.lineWidth,
        borderDash:      s.dashed ? [6, 3] : [],
        yAxisID:         s.yAxisId,
        pointRadius:      2,
        pointHoverRadius: 4,
        pointHitRadius:   6,
        tension: 0,
        fill:    false,
        segment: {
            borderColor: (ctx: any) =>
                (ctx.chart.data.datasets[ctx.datasetIndex].data[ctx.p0DataIndex] as DataPointJS)
                    ?.segmentColor || undefined,
        },
        pointBackgroundColor: ((ctx: any) => (ctx.raw as DataPointJS)?.segmentColor || s.color) as any,
        pointBorderColor:     ((ctx: any) => (ctx.raw as DataPointJS)?.segmentColor || s.color) as any,
    };
    ds._seriesId      = s.id;
    ds._soloAxisLabel = s.soloAxisLabel;
    return ds;
}

/** In focus mode (exactly one visible dataset) return the original axes with the soloed
  * axis re-ranged/re-labelled and every other axis flagged `hidden`. Outside focus mode
  * return the originals unchanged.
  *
  *   - left axis  → floor/ceil to 5, primary grid at 5, dashed secondary at 1
  *   - right axis → floor/ceil to 25, uniform grid at 25 */
function focusYAxes(chart: Chart, originals: YAxisConfigJS[]): YAxisConfigJS[] {
    const visibleIdx = chart.data.datasets
        .map((_, i) => i)
        .filter(i => chart.isDatasetVisible(i));
    if (visibleIdx.length !== 1) return originals;

    const ds        = chart.data.datasets[visibleIdx[0]] as any;
    const soloId    = ds.yAxisID as string;
    const soloLabel = ds._soloAxisLabel as string;
    const original  = originals.find(a => a.id === soloId);
    if (!original) return originals;

    const ys      = (ds.data as DataPointJS[]).map(p => p.y).filter(Number.isFinite);
    const isLeft  = soloId === 'left';
    const step    = isLeft ? 5 : 25;
    const dataMin = ys.length ? Math.min(...ys) : (original.min ?? 0);
    const dataMax = ys.length ? Math.max(...ys) : (original.max ?? 0);
    const min     = Math.floor(dataMin / step) * step;
    const max     = Math.max(Math.ceil(dataMax / step) * step, min + step);

    return originals.map(a => a.id === soloId
        ? {
            ...a,
            label:             soloLabel || a.label,
            min, max,
            stepSize:          isLeft ? 1 : step,
            primaryGridStep:   isLeft ? 5 : undefined,
            secondaryGridStep: isLeft ? 1 : undefined,
          }
        : { ...a, hidden: true });
}

function buildChartConfig(data: ChartDataJS, config: GraphConfigJS): ChartConfiguration<'line'> {
    const datasets = data.series.map(seriesToDataset);

    const xScale: Record<string, any> = {
        type:  'linear',
        title: { display: true, text: data.xAxisLabel },
        ticks: { maxRotation: 0 },
        afterBuildTicks(scale: any) {
            const ticks: { value: number }[] = [{ value: -0.5 }, { value: 0 }];
            for (let v = 2; v <= scale.max; v += 2) ticks.push({ value: v });
            scale.ticks = ticks;
        },
    };
    if (data.xMin !== undefined) xScale.min = data.xMin;
    if (data.xMax !== undefined) xScale.max = data.xMax;

    const scales: Record<string, any> = { x: xScale };
    data.yAxes.forEach((axis, i) => { scales[axis.id] = buildYAxisScale(axis, i === 0); });

    return {
        type: 'line',
        data: { datasets },
        plugins: [graphVizPlugin],
        options: {
            responsive:          config.responsive,
            maintainAspectRatio: config.maintainAspectRatio,
            interaction:         { mode: 'index', intersect: true },
            plugins: {
                tooltip: {
                    enabled:  true,
                    position: 'nearest',
                    callbacks: {
                        title(items: TooltipItem<'line'>[]) {
                            if (!items.length) return '';
                            const raw = items[0].raw as DataPointJS;
                            return [raw?.tooltipTitle, raw?.tooltipExtra].filter(Boolean);
                        },
                        label(item: TooltipItem<'line'>) {
                            const raw = item.raw as DataPointJS;
                            return `${item.dataset.label ?? ''}: ${raw?.formattedValue || item.formattedValue}`;
                        },
                    },
                },
                legend: {
                    display:  true,
                    position: 'top',
                    onClick(_e: any, legendItem: any, legend: any) {
                        const chart = legend.chart as Chart;
                        const idx   = legendItem.datasetIndex as number;
                        const n     = chart.data.datasets.length;
                        const vis   = Array.from({ length: n }, (_, i) => chart.isDatasetVisible(i));
                        const alreadySoloed = vis[idx] && !vis.some((v: boolean, i: number) => v && i !== idx);
                        for (let i = 0; i < n; i++) {
                            chart.setDatasetVisibility(i, alreadySoloed || i === idx);
                        }
                        const state = viz(chart);
                        if (state) {
                            state.effYAxes = focusYAxes(chart, state.data.yAxes);
                            applyYAxes(chart, state.effYAxes);
                        }
                        chart.update();
                    },
                },
            },
            scales,
        },
    };
}
