/**
 * 终身学习者能力分析可视化系统 - 图表工具库
 * 使用 ECharts 5 绑定图表到 DOM 容器
 */

const COLORS = ['#4361ee', '#f72585', '#06d6a0', '#ffd166', '#4895ef',
    '#7209b7', '#3a0ca3', '#f77f00', '#118ab2', '#ef476f'];

function initChart(domId) {
    const dom = document.getElementById(domId);
    if (!dom) return null;
    let chart = echarts.getInstanceByDom(dom);
    if (chart) chart.dispose();
    return echarts.init(dom);
}

/** 雷达图 - 技能画像 */
function renderRadarChart(domId, categories, scores, title) {
    const chart = initChart(domId);
    if (!chart) return;
    const maxVal = Math.max(100, ...scores);
    chart.setOption({
        title: { text: title || '', left: 'center', textStyle: { fontSize: 14 } },
        tooltip: {},
        radar: {
            indicator: categories.map(c => ({ name: c, max: maxVal })),
            shape: 'polygon',
            splitNumber: 4,
        },
        series: [{
            type: 'radar',
            data: [{ value: scores, name: '能力得分', areaStyle: { opacity: 0.3 } }],
            itemStyle: { color: COLORS[0] },
        }]
    });
    return chart;
}

/** 柱状图 */
function renderBarChart(domId, xData, yData, title, opts) {
    const chart = initChart(domId);
    if (!chart) return;
    const o = opts || {};
    chart.setOption({
        title: { text: title || '', left: 'center', textStyle: { fontSize: 14 } },
        tooltip: { trigger: 'axis' },
        grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
        xAxis: { type: 'category', data: xData, axisLabel: { rotate: o.rotate || 0, fontSize: 11 } },
        yAxis: { type: 'value' },
        series: [{
            type: 'bar',
            data: yData,
            itemStyle: {
                color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                    { offset: 0, color: COLORS[0] },
                    { offset: 1, color: COLORS[4] }
                ])
            },
            barMaxWidth: 40,
        }]
    });
    return chart;
}

/** 多系列折线图 */
function renderLineChart(domId, xData, seriesArr, title) {
    const chart = initChart(domId);
    if (!chart) return;
    chart.setOption({
        title: { text: title || '', left: 'center', textStyle: { fontSize: 14 } },
        tooltip: { trigger: 'axis' },
        legend: { bottom: 0, data: seriesArr.map(s => s.name) },
        grid: { left: '3%', right: '4%', bottom: '12%', containLabel: true },
        xAxis: { type: 'category', data: xData, axisLabel: { fontSize: 11 } },
        yAxis: { type: 'value' },
        series: seriesArr.map((s, i) => ({
            type: 'line',
            name: s.name,
            data: s.data,
            smooth: true,
            itemStyle: { color: COLORS[i % COLORS.length] },
        }))
    });
    return chart;
}

/** 饼图 */
function renderPieChart(domId, data, title) {
    const chart = initChart(domId);
    if (!chart) return;
    chart.setOption({
        title: { text: title || '', left: 'center', textStyle: { fontSize: 14 } },
        tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
        legend: { bottom: 0, type: 'scroll' },
        series: [{
            type: 'pie',
            radius: ['35%', '60%'],
            center: ['50%', '45%'],
            data: data.map((d, i) => ({
                ...d, itemStyle: { color: COLORS[i % COLORS.length] }
            })),
            label: { formatter: '{b}\n{d}%', fontSize: 11 },
            emphasis: { itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0,0,0,0.2)' } },
        }]
    });
    return chart;
}

/** 仪表盘 */
function renderGaugeChart(domId, value, title) {
    const chart = initChart(domId);
    if (!chart) return;
    chart.setOption({
        title: { text: title || '', left: 'center', textStyle: { fontSize: 14 } },
        series: [{
            type: 'gauge',
            min: 0,
            max: 100,
            progress: { show: true, width: 14 },
            axisLine: { lineStyle: { width: 14 } },
            axisTick: { show: false },
            splitLine: { length: 10, lineStyle: { width: 2 } },
            pointer: { width: 5 },
            detail: { fontSize: 24, offsetCenter: [0, '70%'], formatter: '{value}分' },
            data: [{ value: value, itemStyle: { color: COLORS[0] } }],
        }]
    });
    return chart;
}

/** 热力图 - 相似度矩阵 */
function renderHeatmap(domId, xLabels, yLabels, data, title) {
    const chart = initChart(domId);
    if (!chart) return;
    chart.setOption({
        title: { text: title || '', left: 'center', textStyle: { fontSize: 14 } },
        tooltip: {
            formatter: (p) => `${xLabels[p.value[0]]} ↔ ${yLabels[p.value[1]]}: ${p.value[2]}`
        },
        grid: { left: '15%', right: '12%', bottom: '15%', top: '12%' },
        xAxis: { type: 'category', data: xLabels, axisLabel: { fontSize: 11 } },
        yAxis: { type: 'category', data: yLabels, axisLabel: { fontSize: 11 } },
        visualMap: {
            min: 0, max: 1, calculable: true, orient: 'vertical', right: 0, top: 'center',
            inRange: { color: ['#f0f2f5', '#4895ef', '#4361ee'] },
        },
        series: [{
            type: 'heatmap',
            data: data,
            label: { show: true, fontSize: 11, formatter: (p) => p.value[2].toFixed(3) },
            emphasis: { itemStyle: { borderColor: '#333', borderWidth: 1 } },
        }]
    });
    return chart;
}

/** 水平条形图 */
function renderHBarChart(domId, yData, xData, title, colorIdx) {
    const chart = initChart(domId);
    if (!chart) return;
    chart.setOption({
        title: { text: title || '', left: 'center', textStyle: { fontSize: 14 } },
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
        grid: { left: '20%', right: '4%', bottom: '3%', containLabel: true },
        xAxis: { type: 'value' },
        yAxis: { type: 'category', data: yData, axisLabel: { fontSize: 11 } },
        series: [{
            type: 'bar',
            data: xData,
            itemStyle: { color: COLORS[colorIdx || 0] },
            barMaxWidth: 24,
        }]
    });
    return chart;
}
