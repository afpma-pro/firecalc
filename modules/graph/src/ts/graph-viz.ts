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
    type ChartConfiguration,
    type ChartDataset,
    type TooltipItem,
} from 'chart.js';

// Register required Chart.js components (tree-shakeable)
Chart.register(
    LineController,
    LineElement,
    PointElement,
    LinearScale,
    Tooltip,
    Legend,
    Filler
);

// ── TypeScript interfaces matching Scala.js facades ──

interface DataPointJS {
    x: number;
    y: number;
    tooltipTitle: string;
    tooltipExtra: string;
    formattedValue: string;
    segmentColor: string;
    highlightTargets: string[];
}

interface ChartSeriesJS {
    id: string;
    name: string;
    color: string;
    points: DataPointJS[];
    yAxisId: string;
    lineWidth: number;
    dashed: boolean;
}

interface YAxisConfigJS {
    id: string;
    label: string;
    position: 'left' | 'right';
    min?: number;
    max?: number;
    stepSize?: number;
    primaryGridStep?: number;
    secondaryGridStep?: number;
}

interface XSegmentJS {
    xStart: number;
    xEnd:   number;
}

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
    xEnd: number;
    color: string;
    label: string;
}

interface ChartDataJS {
    series: ChartSeriesJS[];
    yAxes: YAxisConfigJS[];
    xAxisLabel: string;
    backgroundBands: BackgroundBandJS[];
    xMin?: number;
    xMax?: number;
    horizontalLines: HorizontalReferenceLineJS[];
}

interface GraphConfigJS {
    responsive: boolean;
    maintainAspectRatio: boolean;
}

interface GraphVizHandleJS {
    dispose(): void;
    update(data: ChartDataJS): void;
}

// ── Background bands plugin ──

/** Inline Chart.js plugin: paints semi-transparent vertical bands behind the chart area,
  * draws dashed secondary gridlines, horizontal reference lines, and manages legend focus mode.
  * Reads data from `_backgroundBands`, `_yAxisMeta`, and `_horizontalLines` stashed on the chart. */
const graphVizPlugin = {
    id: 'graphVizPlugin',

    /** Feature 1: paint semi-transparent vertical background bands. */
    beforeDraw(chart: Chart) {
        const bands = (chart as any)._backgroundBands as BackgroundBandJS[] | undefined;
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
    },

    /** Feature 2: draw thin dashed secondary horizontal gridlines for axes that define secondaryGridStep. */
    beforeDatasetsDraw(chart: Chart) {
        const { ctx, chartArea, scales } = chart;
        if (!chartArea) return;
        for (const [axisId, scale] of Object.entries(scales)) {
            const axisMeta = (chart as any)._yAxisMeta?.[axisId] as
                { primaryGridStep?: number; secondaryGridStep?: number } | undefined;
            if (!axisMeta?.secondaryGridStep) continue;
            const { min, max } = scale as any;
            const primary = axisMeta.primaryGridStep;
            const secondary = axisMeta.secondaryGridStep;
            ctx.save();
            ctx.strokeStyle = 'rgba(0, 0, 0, 0.08)';
            ctx.lineWidth = 1;
            ctx.setLineDash([3, 3]);
            const start = Math.ceil(min / secondary) * secondary;
            for (let v = start; v <= max + 1e-9; v += secondary) {
                if (primary && Math.abs(v / primary - Math.round(v / primary)) < 1e-9) continue;
                const y = scale.getPixelForValue(v);
                ctx.beginPath();
                ctx.moveTo(chartArea.left, y);
                ctx.lineTo(chartArea.right, y);
                ctx.stroke();
            }
            ctx.restore();
        }
    },

    /** Feature 4: draw horizontal reference lines scoped to x-ranges, gated on series visibility. */
    afterDatasetsDraw(chart: Chart) {
        const lines = (chart as any)._horizontalLines as HorizontalReferenceLineJS[] | undefined;
        if (!lines || lines.length === 0) return;

        const { ctx, chartArea, scales } = chart;
        if (!chartArea) return;

        const xScale = scales.x;

        for (const line of lines) {
            // Visibility gate: show if any of the gating series is visible
            if (line.visibleWhenSeriesIds.length > 0) {
                const isShown = line.visibleWhenSeriesIds.some(id => {
                    const i = chart.data.datasets.findIndex(d => (d as any)._seriesId === id);
                    return i >= 0 && chart.isDatasetVisible(i);
                });
                if (!isShown) continue;
            }

            const yScale = scales[line.yAxisId];
            if (!yScale || !xScale) continue;
            const yPx = yScale.getPixelForValue(line.y);

            ctx.save();
            ctx.strokeStyle = line.color;
            ctx.lineWidth = 1.5;
            ctx.setLineDash([]);

            let lastX2: number | null = null;
            for (const seg of line.segments) {
                const x1 = Math.max(chartArea.left, xScale.getPixelForValue(seg.xStart));
                const x2 = Math.min(chartArea.right, xScale.getPixelForValue(seg.xEnd));
                if (x2 <= x1) continue;
                ctx.beginPath();
                ctx.moveTo(x1, yPx);
                ctx.lineTo(x2, yPx);
                ctx.stroke();
                lastX2 = x2;
            }

            // Draw label near the right edge of the last drawn segment
            if (line.label && lastX2 !== null) {
                ctx.fillStyle = line.color;
                ctx.font = '10px sans-serif';
                ctx.fillText(line.label, lastX2 + 4, yPx - 3);
            }

            ctx.restore();
        }
    },
};

// ── Public API ──

export function initGraphViz(
    container: HTMLElement,
    data: ChartDataJS,
    config: GraphConfigJS,
    onPointClick?: (targets: string[]) => void
): GraphVizHandleJS {
    const canvas = document.createElement('canvas');
    container.appendChild(canvas);

    const chartConfig = buildChartConfig(data, config);

    // Add click handler: highlight pipe panel elements when a data point is clicked
    if (onPointClick) {
        chartConfig.options!.onClick = (_event: any, _elements: any[], chart: any) => {
            const nearest = chart.getElementsAtEventForMode(
                _event, 'nearest', { intersect: true }, false
            );
            if (nearest.length > 0) {
                const el = nearest[0];
                const raw = chart.data.datasets[el.datasetIndex].data[el.index] as DataPointJS;
                onPointClick(raw?.highlightTargets || []);
            } else {
                onPointClick([]);
            }
        };
    }

    const chart = new Chart(canvas, chartConfig);
    (chart as any)._backgroundBands = data.backgroundBands;
    (chart as any)._yAxisMeta = data.yAxes.reduce((acc, a) => {
        acc[a.id] = { primaryGridStep: a.primaryGridStep, secondaryGridStep: a.secondaryGridStep };
        return acc;
    }, {} as Record<string, any>);
    (chart as any)._horizontalLines = data.horizontalLines;

    return {
        dispose() {
            chart.destroy();
            canvas.remove();
        },
        update(newData: ChartDataJS) {
            // Update datasets
            chart.data.datasets = newData.series.map(seriesToDataset);

            // Update background bands, grid meta, and reference lines
            (chart as any)._backgroundBands = newData.backgroundBands;
            (chart as any)._yAxisMeta = newData.yAxes.reduce((acc, a) => {
                acc[a.id] = { primaryGridStep: a.primaryGridStep, secondaryGridStep: a.secondaryGridStep };
                return acc;
            }, {} as Record<string, any>);
            (chart as any)._horizontalLines = newData.horizontalLines;

            // Update scales
            const scales = chart.options.scales!;
            // Remove old custom scales (keep only 'x')
            for (const key of Object.keys(scales)) {
                if (key !== 'x') {
                    delete scales[key];
                }
            }
            // Update x-axis
            if (scales.x) {
                if ((scales.x as any).title) {
                    (scales.x as any).title.text = newData.xAxisLabel;
                }
                if (newData.xMin !== undefined) (scales.x as any).min = newData.xMin;
                else delete (scales.x as any).min;
                if (newData.xMax !== undefined) (scales.x as any).max = newData.xMax;
                else delete (scales.x as any).max;
            }
            // Add new y scales
            newData.yAxes.forEach((axis, i) => {
                scales[axis.id] = buildYAxisScale(axis, i === 0);
            });

            chart.update();
        },
    };
}

// ── Internal helpers ──

/** Number of ticks on every y-axis so that grids are aligned across scales. */
const Y_AXIS_TICK_COUNT = 11;

/** Grid lines with zero-line emphasis, dashed non-zero lines, and optional stepSize.
  *
  * When `stepSize` is provided we also disable Chart.js's default tick-thinning:
  *  - `maxTicksLimit: Number.MAX_SAFE_INTEGER` bypasses the silent ~11-tick cap on the
  *    linear scale. Without it, a right-axis stepSize derived from the left primary grid
  *    (which can legitimately request more than 11 ticks over a wide range) would be
  *    silently decimated, breaking the 1:1 gridline alignment between left and right axes.
  *  - `autoSkip: false` prevents Chart.js from dropping labels on crowded axes, which
  *    would also desynchronise the right-axis grid from the left primary grid. */
function buildYAxisScale(axis: YAxisConfigJS, isPrimary: boolean): Record<string, any> {
    const ticks: Record<string, any> = axis.stepSize !== undefined
        ? { stepSize: axis.stepSize, maxTicksLimit: Number.MAX_SAFE_INTEGER, autoSkip: false }
        : { count: Y_AXIS_TICK_COUNT };

    return {
        type: 'linear',
        position: axis.position,
        title: {
            display: true,
            text: axis.label,
        },
        border: {
            dash: (ctx: { tick: { value: number } }) =>
                ctx.tick.value === 0 ? [] : [4, 4],
        },
        grid: {
            drawOnChartArea: true,
            color: (ctx: { tick: { value: number } }) =>
                ctx.tick.value === 0
                    ? 'rgba(0, 0, 0, 0.35)'
                    : isPrimary
                        ? 'rgba(0, 0, 0, 0.1)'
                        : 'transparent',
            lineWidth: (ctx: { tick: { value: number } }) =>
                ctx.tick.value === 0 ? 2 : 1,
        },
        ticks,
        ...(axis.min !== undefined && { min: axis.min }),
        ...(axis.max !== undefined && { max: axis.max }),
    };
}

function seriesToDataset(s: ChartSeriesJS): ChartDataset<'line'> {
    const ds: ChartDataset<'line'> & { _seriesId?: string } = {
        label: s.name,
        data: s.points as any[], // DataPointJS objects (Chart.js reads x/y + we access extras in callbacks)
        borderColor: s.color,
        backgroundColor: s.color + '20', // 12% opacity fill
        borderWidth: s.lineWidth,
        borderDash: s.dashed ? [6, 3] : [],
        yAxisID: s.yAxisId,
        pointRadius: 2,
        pointHoverRadius: 4,
        pointHitRadius: 6,
        tension: 0.0, // no curve smoothing
        fill: false,
        // Per-segment color override (driven by segmentColor metadata from Scala)
        segment: {
            borderColor: (ctx: any) => {
                const raw = ctx.chart.data.datasets[ctx.datasetIndex].data[ctx.p0DataIndex] as DataPointJS;
                return raw?.segmentColor || undefined;
            },
        },
        pointBackgroundColor: ((ctx: any) => {
            const raw = ctx.raw as DataPointJS;
            return raw?.segmentColor || s.color;
        }) as any,
        pointBorderColor: ((ctx: any) => {
            const raw = ctx.raw as DataPointJS;
            return raw?.segmentColor || s.color;
        }) as any,
    };
    ds._seriesId = s.id;
    return ds;
}

function buildChartConfig(
    data: ChartDataJS,
    config: GraphConfigJS
): ChartConfiguration<'line'> {
    const datasets = data.series.map(seriesToDataset);

    const xScale: Record<string, any> = {
        type: 'linear' as const,
        title: {
            display: true,
            text: data.xAxisLabel,
        },
        ticks: {
            maxRotation: 0,
        },
        afterBuildTicks(scale: any) {
            const ticks: { value: number }[] = [{ value: -0.5 }, { value: 0 }];
            let v = 2;
            while (v <= scale.max) {
                ticks.push({ value: v });
                v += 2;
            }
            scale.ticks = ticks;
        },
    };
    if (data.xMin !== undefined) xScale.min = data.xMin;
    if (data.xMax !== undefined) xScale.max = data.xMax;

    const scales: Record<string, any> = { x: xScale };

    data.yAxes.forEach((axis, i) => {
        scales[axis.id] = buildYAxisScale(axis, i === 0);
    });

    return {
        type: 'line',
        data: {
            datasets,
        },
        plugins: [graphVizPlugin],
        options: {
            responsive: config.responsive,
            maintainAspectRatio: config.maintainAspectRatio,
            interaction: {
                mode: 'index',
                intersect: true,
            },
            plugins: {
                tooltip: {
                    enabled: true,
                    position: 'nearest',
                    callbacks: {
                        title(items: TooltipItem<'line'>[]) {
                            if (!items.length) return '';
                            const raw = items[0].raw as DataPointJS;
                            const lines: string[] = [];
                            if (raw?.tooltipTitle) lines.push(raw.tooltipTitle);
                            if (raw?.tooltipExtra) lines.push(raw.tooltipExtra);
                            return lines;
                        },
                        label(item: TooltipItem<'line'>) {
                            const raw = item.raw as DataPointJS;
                            const seriesName = item.dataset.label || '';
                            const value = raw?.formattedValue || item.formattedValue;
                            return `${seriesName}: ${value}`;
                        },
                    },
                },
                legend: {
                    display: true,
                    position: 'top',
                    onClick(_e: any, legendItem: any, legend: any) {
                        const chart = legend.chart;
                        const idx = legendItem.datasetIndex as number;
                        const n = chart.data.datasets.length;
                        const visibleMask = Array.from({ length: n }, (_: unknown, i: number) => chart.isDatasetVisible(i));
                        const clickedVisible = visibleMask[idx];
                        const othersVisible = visibleMask.some((v: boolean, i: number) => v && i !== idx);
                        const alreadySoloed = clickedVisible && !othersVisible;
                        for (let i = 0; i < n; i++) {
                            chart.setDatasetVisibility(i, alreadySoloed || i === idx);
                        }
                        chart.update();
                    },
                },
            },
            scales,
        },
    };
}
