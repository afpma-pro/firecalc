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
}

interface ChartDataJS {
    series: ChartSeriesJS[];
    yAxes: YAxisConfigJS[];
    xAxisLabel: string;
}

interface GraphConfigJS {
    responsive: boolean;
    maintainAspectRatio: boolean;
}

interface GraphVizHandleJS {
    dispose(): void;
    update(data: ChartDataJS): void;
}

// ── Public API ──

export function initGraphViz(
    container: HTMLElement,
    data: ChartDataJS,
    config: GraphConfigJS
): GraphVizHandleJS {
    const canvas = document.createElement('canvas');
    container.appendChild(canvas);

    const chartConfig = buildChartConfig(data, config);
    const chart = new Chart(canvas, chartConfig);

    return {
        dispose() {
            chart.destroy();
            canvas.remove();
        },
        update(newData: ChartDataJS) {
            // Update datasets
            chart.data.datasets = newData.series.map(seriesToDataset);

            // Update scales
            const scales = chart.options.scales!;
            // Remove old custom scales (keep only 'x')
            for (const key of Object.keys(scales)) {
                if (key !== 'x') {
                    delete scales[key];
                }
            }
            // Update x-axis label
            if (scales.x && (scales.x as any).title) {
                (scales.x as any).title.text = newData.xAxisLabel;
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

/** Solid grid lines with zero-line emphasis + aligned tick count. */
function buildYAxisScale(axis: YAxisConfigJS, isPrimary: boolean): Record<string, any> {
    return {
        type: 'linear',
        position: axis.position,
        title: {
            display: true,
            text: axis.label,
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
        ticks: {
            count: Y_AXIS_TICK_COUNT,
        },
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
        tension: 0.0, // no curve smoothing
        fill: false,
    };
}

function buildChartConfig(
    data: ChartDataJS,
    config: GraphConfigJS
): ChartConfiguration<'line'> {
    const datasets = data.series.map(seriesToDataset);

    const scales: Record<string, any> = {
        x: {
            type: 'linear' as const,
            title: {
                display: true,
                text: data.xAxisLabel,
            },
            ticks: {
                maxRotation: 0,
            },
        },
    };

    data.yAxes.forEach((axis, i) => {
        scales[axis.id] = buildYAxisScale(axis, i === 0);
    });

    return {
        type: 'line',
        data: {
            datasets,
        },
        options: {
            responsive: config.responsive,
            maintainAspectRatio: config.maintainAspectRatio,
            interaction: {
                mode: 'index',
                intersect: false,
            },
            plugins: {
                tooltip: {
                    enabled: true,
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
