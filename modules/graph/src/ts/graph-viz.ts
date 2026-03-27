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

/** Inline Chart.js plugin: paints semi-transparent vertical bands behind the chart area.
  * Reads band data from `(chart as any)._backgroundBands` so it can be updated dynamically. */
const graphVizPlugin = {
    id: 'graphVizPlugin',
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
                _event, 'nearest', { intersect: false }, false
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

    return {
        dispose() {
            chart.destroy();
            canvas.remove();
        },
        update(newData: ChartDataJS) {
            // Update datasets
            chart.data.datasets = newData.series.map(seriesToDataset);

            // Update background bands
            (chart as any)._backgroundBands = newData.backgroundBands;

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

/** Grid lines with zero-line emphasis, dashed non-zero lines, and optional stepSize. */
function buildYAxisScale(axis: YAxisConfigJS, isPrimary: boolean): Record<string, any> {
    const ticks: Record<string, any> = axis.stepSize !== undefined
        ? { stepSize: axis.stepSize }
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
    return {
        label: s.name,
        data: s.points as any[], // DataPointJS objects (Chart.js reads x/y + we access extras in callbacks)
        borderColor: s.color,
        backgroundColor: s.color + '20', // 12% opacity fill
        borderWidth: s.lineWidth,
        borderDash: s.dashed ? [6, 3] : [],
        yAxisID: s.yAxisId,
        pointRadius: 2,
        pointHoverRadius: 4,
        pointHitRadius: 15,
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
                },
            },
            scales,
        },
    };
}
