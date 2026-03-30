/**
 * 终身学习者能力分析可视化系统 - 主应用逻辑
 */
let currentPage = 'dashboard';
let currentLearner = '';
let allCharts = [];

// ===== 初始化 =====
document.addEventListener('DOMContentLoaded', () => {
    checkAuth();
    window.addEventListener('resize', () => allCharts.forEach(c => c && c.resize()));
});

async function checkAuth() {
    try {
        const resp = await fetch('/auth/status');
        if (!resp.ok) {
            window.location.href = '/login.html';
            return;
        }
        const user = await resp.json();
        const nameEl = document.getElementById('userDisplayName');
        if (nameEl) nameEl.textContent = '👤 ' + (user.displayName || user.username);
    } catch (e) {
        window.location.href = '/login.html';
        return;
    }
    loadLearnerSelector();
    navigateTo('dashboard');
}

async function handleLogout() {
    try {
        await fetch('/auth/logout', { method: 'POST' });
    } catch (e) {
        // ignore
    }
    window.location.href = '/login.html';
}

function navigateTo(page) {
    currentPage = page;
    document.querySelectorAll('.nav-item').forEach(el => {
        el.classList.toggle('active', el.dataset.page === page);
    });
    renderPage(page);
}

function onLearnerChange(select) {
    currentLearner = select.value;
    renderPage(currentPage);
}

async function loadLearnerSelector() {
    try {
        const learners = await api.learners();
        const sel = document.getElementById('learnerSelect');
        sel.innerHTML = '<option value="">全部学习者</option>';
        learners.forEach(l => {
            sel.innerHTML += `<option value="${l.learner_id}">${l.learner_id} - ${l.name}</option>`;
        });
    } catch (e) {
        console.error('加载学习者列表失败', e);
    }
}

// ===== 页面路由 =====
function renderPage(page) {
    allCharts.forEach(c => c && c.dispose());
    allCharts = [];
    const main = document.getElementById('pageContent');
    main.innerHTML = '<div class="loading">加载中...</div>';

    const handlers = {
        dashboard: renderDashboard,
        profile: renderProfile,
        evolution: renderEvolution,
        path: renderPath,
        similarity: renderSimilarity,
        efficiency: renderEfficiency,
        decay: renderDecay,
        import: renderImport,
    };
    const handler = handlers[page];
    if (handler) handler(main);
}

// ===== 工具 =====
function ratingBadge(rating) {
    const map = { '优秀': 'success', '良好': 'info', '进步中': 'warning', '入门': 'danger' };
    return `<span class="badge badge-${map[rating] || 'info'}">${rating}</span>`;
}

function trendBadge(trend) {
    const map = { '提升': 'success', '稳定': 'info', '下降': 'danger' };
    return `<span class="badge badge-${map[trend] || 'info'}">${trend}</span>`;
}

function num(v, decimals) {
    if (v == null) return '-';
    const n = Number(v);
    return isNaN(n) ? v : n.toFixed(decimals != null ? decimals : 1);
}

function makeTable(headers, rows) {
    return `<table class="data-table">
        <thead><tr>${headers.map(h => `<th>${h}</th>`).join('')}</tr></thead>
        <tbody>${rows.join('')}</tbody>
    </table>`;
}

// ===== 仪表盘 =====
async function renderDashboard(container) {
    try {
        const [overview, ratings, popular, leaderboard] = await Promise.all([
            api.overview(), api.overallRating(), api.popularSkills(10), api.efficiencyLeaderboard()
        ]);

        container.innerHTML = `
            <div class="stats-grid">
                <div class="stat-card"><div class="stat-value">${overview.learnerCount}</div><div class="stat-label">学习者</div></div>
                <div class="stat-card"><div class="stat-value">${overview.skillCount}</div><div class="stat-label">技能</div></div>
                <div class="stat-card"><div class="stat-value">${overview.courseCount}</div><div class="stat-label">课程</div></div>
                <div class="stat-card"><div class="stat-value">${overview.recordCount}</div><div class="stat-label">学习记录</div></div>
            </div>
            <div class="charts-grid">
                <div class="card"><div class="card-title">📊 综合能力评级</div><div id="chartRating" class="chart-container"></div></div>
                <div class="card"><div class="card-title">🔥 热门技能 Top10</div><div id="chartPopular" class="chart-container"></div></div>
            </div>
            <div class="charts-grid">
                <div class="card"><div class="card-title">🏆 学习效率排行</div><div id="chartLeaderboard" class="chart-container"></div></div>
                <div class="card">
                    <div class="card-title">📋 学习者评级一览</div>
                    ${makeTable(['学习者', '综合得分', '技能数', '学习时长', '评级'],
                        ratings.map(r => `<tr>
                            <td>${r.learner_id}</td><td>${num(r.overall_score)}</td>
                            <td>${r.total_skills}</td><td>${num(r.total_study_hours, 0)}h</td>
                            <td>${ratingBadge(r.rating)}</td></tr>`)
                    )}
                </div>
            </div>`;

        // 评级柱状图
        allCharts.push(renderBarChart('chartRating',
            ratings.map(r => r.learner_id),
            ratings.map(r => Number(r.overall_score)),
            ''
        ));

        // 热门技能
        allCharts.push(renderHBarChart('chartPopular',
            popular.map(p => p.skill_name).reverse(),
            popular.map(p => Number(p.learner_count)).reverse(),
            '', 1
        ));

        // 效率排行
        allCharts.push(renderBarChart('chartLeaderboard',
            leaderboard.map(l => l.learner_id),
            leaderboard.map(l => Number(l.overall_efficiency)),
            ''
        ));
    } catch (e) {
        container.innerHTML = `<div class="empty-state">加载失败: ${e.message}</div>`;
    }
}

// ===== 能力画像 =====
async function renderProfile(container) {
    try {
        const lid = currentLearner;
        const [profile, catDist, ratings] = await Promise.all([
            api.skillProfile(lid), api.categoryDist(lid), api.overallRating()
        ]);

        const learnerRating = lid ? ratings.find(r => r.learner_id === lid) : null;

        container.innerHTML = `
            ${learnerRating ? `<div class="stats-grid">
                <div class="stat-card"><div class="stat-value">${num(learnerRating.overall_score)}</div><div class="stat-label">综合得分</div></div>
                <div class="stat-card"><div class="stat-value">${learnerRating.total_skills}</div><div class="stat-label">已学技能</div></div>
                <div class="stat-card"><div class="stat-value">${num(learnerRating.total_study_hours, 0)}h</div><div class="stat-label">总学时</div></div>
                <div class="stat-card"><div class="stat-value">${ratingBadge(learnerRating.rating)}</div><div class="stat-label">综合评级</div></div>
            </div>` : ''}
            <div class="charts-grid">
                <div class="card"><div class="card-title">🎯 技能雷达图</div><div id="chartRadar" class="chart-container"></div></div>
                <div class="card"><div class="card-title">📊 类别能力分布</div><div id="chartCatDist" class="chart-container"></div></div>
            </div>
            <div class="card">
                <div class="card-title">📋 技能详情</div>
                ${makeTable(['学习者', '技能', '类别', '难度', '最新', '最高', '平均', '学时', '次数'],
                    profile.map(p => `<tr>
                        <td>${p.learner_id}</td><td>${p.skill_name}</td><td>${p.category}</td>
                        <td>${p.difficulty_level}</td><td>${p.latest_score}</td><td>${p.max_score}</td>
                        <td>${num(p.avg_score)}</td><td>${p.total_hours}h</td><td>${p.attempts}</td>
                    </tr>`)
                )}
            </div>`;

        // 雷达图 - 按类别平均分
        if (catDist.length > 0) {
            const targetData = lid ? catDist : catDist.filter(c => c.learner_id === catDist[0].learner_id);
            allCharts.push(renderRadarChart('chartRadar',
                targetData.map(c => c.category),
                targetData.map(c => Number(c.avg_category_score)),
                lid || ''
            ));
        }

        // 类别分布饼图
        if (catDist.length > 0) {
            const targetData = lid ? catDist : catDist;
            const grouped = {};
            targetData.forEach(c => {
                grouped[c.category] = (grouped[c.category] || 0) + Number(c.category_hours);
            });
            allCharts.push(renderPieChart('chartCatDist',
                Object.entries(grouped).map(([name, value]) => ({ name, value })),
                '学习时间分布'
            ));
        }
    } catch (e) {
        container.innerHTML = `<div class="empty-state">加载失败: ${e.message}</div>`;
    }
}

// ===== 能力演进 =====
async function renderEvolution(container) {
    try {
        const lid = currentLearner;
        const [trend, monthly] = await Promise.all([
            api.progressTrend(lid), api.monthlyActivity(lid)
        ]);

        container.innerHTML = `
            <div class="charts-grid">
                <div class="card"><div class="card-title">📈 进步趋势</div><div id="chartTrend" class="chart-container"></div></div>
                <div class="card"><div class="card-title">📅 月度学习活跃度</div><div id="chartMonthly" class="chart-container"></div></div>
            </div>
            <div class="card">
                <div class="card-title">📋 进步趋势详情</div>
                ${makeTable(['学习者', '技能', '首次', '最近', '提升', '趋势', '次数', '学时'],
                    trend.map(t => `<tr>
                        <td>${t.learner_id}</td><td>${t.skill_name}</td>
                        <td>${t.first_score}</td><td>${t.last_score}</td>
                        <td>${t.score_improvement}</td><td>${trendBadge(t.trend)}</td>
                        <td>${t.attempts}</td><td>${num(t.total_hours, 0)}h</td>
                    </tr>`)
                )}
            </div>`;

        // 趋势图 - 首次/最近得分对比
        if (trend.length > 0) {
            const labels = trend.map(t => `${t.learner_id}-${t.skill_name}`);
            allCharts.push(renderLineChart('chartTrend', labels, [
                { name: '首次得分', data: trend.map(t => Number(t.first_score)) },
                { name: '最近得分', data: trend.map(t => Number(t.last_score)) },
            ], ''));
        }

        // 月度活跃度
        if (monthly.length > 0) {
            const months = [...new Set(monthly.map(m => m.month))].sort();
            if (lid) {
                allCharts.push(renderLineChart('chartMonthly', months, [
                    { name: '学习时长', data: months.map(m => { const r = monthly.find(x => x.month === m); return r ? Number(r.monthly_hours) : 0; }) },
                    { name: '平均得分', data: months.map(m => { const r = monthly.find(x => x.month === m); return r ? Number(r.avg_monthly_score) : 0; }) },
                ], ''));
            } else {
                const learners = [...new Set(monthly.map(m => m.learner_id))];
                allCharts.push(renderLineChart('chartMonthly', months,
                    learners.map(l => ({
                        name: l,
                        data: months.map(m => { const r = monthly.find(x => x.learner_id === l && x.month === m); return r ? Number(r.monthly_hours) : 0; })
                    })), ''));
            }
        }
    } catch (e) {
        container.innerHTML = `<div class="empty-state">加载失败: ${e.message}</div>`;
    }
}

// ===== 路径推荐 =====
async function renderPath(container) {
    const lid = currentLearner;
    if (!lid) {
        container.innerHTML = '<div class="empty-state">⬆️ 请先在顶部选择一位学习者</div>';
        return;
    }
    try {
        const [gaps, courses, path] = await Promise.all([
            api.skillGaps(lid), api.recommendedCourses(lid), api.learningPath(lid)
        ]);

        container.innerHTML = `
            <div class="charts-grid">
                <div class="card"><div class="card-title">🔍 技能缺口</div><div id="chartGaps" class="chart-container"></div></div>
                <div class="card"><div class="card-title">🎯 推荐学习路径</div><div id="chartPath" class="chart-container"></div></div>
            </div>
            <div class="card">
                <div class="card-title">📋 技能缺口详情</div>
                ${gaps.length === 0 ? '<div class="empty-state">没有技能缺口</div>' :
                makeTable(['技能ID', '技能名称', '类别', '难度'],
                    gaps.map(g => `<tr><td>${g.skill_id}</td><td>${g.skill_name}</td><td>${g.category}</td><td>${g.difficulty_level}</td></tr>`)
                )}
            </div>
            <div class="card">
                <div class="card-title">📚 推荐课程（含先修条件状态）</div>
                ${courses.length === 0 ? '<div class="empty-state">暂无推荐</div>' :
                makeTable(['课程ID', '课程名', '技能', '难度', '先修条件', '可学习'],
                    courses.map(c => `<tr><td>${c.course_id}</td><td>${c.course_name}</td><td>${c.skill_id}</td>
                        <td>${c.difficulty_level}</td><td>${c.prerequisites || '无'}</td>
                        <td>${c.prereqs_met ? '<span class="badge badge-success">✓</span>' : '<span class="badge badge-danger">✗</span>'}</td></tr>`)
                )}
            </div>`;

        // 缺口图
        if (gaps.length > 0) {
            allCharts.push(renderHBarChart('chartGaps',
                gaps.map(g => g.skill_name).reverse(),
                gaps.map(g => Number(g.difficulty_level)).reverse(),
                '缺口技能（按难度）', 5
            ));
        }

        // 路径图
        if (path.length > 0) {
            allCharts.push(renderBarChart('chartPath',
                path.map(p => p.course_name),
                path.map(p => Number(p.difficulty_level)),
                '推荐路径（按难度递进）', { rotate: 20 }
            ));
        }
    } catch (e) {
        container.innerHTML = `<div class="empty-state">加载失败: ${e.message}</div>`;
    }
}

// ===== 学习者相似度 =====
async function renderSimilarity(container) {
    try {
        const lid = currentLearner;
        const [allSim, popular] = await Promise.all([
            api.allSimilarities(), api.popularSkills(10)
        ]);

        let similarSection = '';
        let collabSection = '';
        if (lid) {
            const [similar, collab] = await Promise.all([
                api.similarLearners(lid, 5), api.collaborative(lid)
            ]);
            similarSection = `
                <div class="card">
                    <div class="card-title">👥 相似学习者 (${lid})</div>
                    ${similar.length === 0 ? '<div class="empty-state">没有相似学习者</div>' :
                    makeTable(['相似学习者', '相似度', '共同技能数'],
                        similar.map(s => `<tr><td>${s.similar_learner}</td><td>${num(s.similarity, 4)}</td><td>${s.common_skills}</td></tr>`)
                    )}
                </div>`;
            collabSection = `
                <div class="card">
                    <div class="card-title">🤝 协同推荐课程 (${lid})</div>
                    ${collab.length === 0 ? '<div class="empty-state">暂无协同推荐</div>' :
                    makeTable(['课程ID', '课程名', '技能', '推荐人数', '加权得分'],
                        collab.map(c => `<tr><td>${c.course_id}</td><td>${c.course_name}</td><td>${c.skill_id}</td>
                            <td>${c.recommended_by_count}</td><td>${num(c.avg_weighted_score)}</td></tr>`)
                    )}
                </div>`;
        }

        container.innerHTML = `
            <div class="charts-grid">
                <div class="card"><div class="card-title">🔗 学习者相似度矩阵</div><div id="chartHeatmap" class="chart-container"></div></div>
                <div class="card"><div class="card-title">🔥 热门技能</div><div id="chartPopularSkills" class="chart-container"></div></div>
            </div>
            ${similarSection}${collabSection}
            <div class="card">
                <div class="card-title">📋 全部相似度</div>
                ${makeTable(['学习者A', '学习者B', '相似度', '共同技能'],
                    allSim.map(s => `<tr><td>${s.learner_a}</td><td>${s.learner_b}</td><td>${num(s.similarity, 4)}</td><td>${s.common_skills}</td></tr>`)
                )}
            </div>`;

        // 热力图
        if (allSim.length > 0) {
            const ids = [...new Set([...allSim.map(s => s.learner_a), ...allSim.map(s => s.learner_b)])].sort();
            const data = [];
            allSim.forEach(s => {
                const xi = ids.indexOf(s.learner_a);
                const yi = ids.indexOf(s.learner_b);
                const v = Number(s.similarity);
                data.push([xi, yi, v]);
                data.push([yi, xi, v]);
            });
            ids.forEach((_, i) => data.push([i, i, 1.0]));
            allCharts.push(renderHeatmap('chartHeatmap', ids, ids, data, ''));
        }

        // 热门技能
        allCharts.push(renderHBarChart('chartPopularSkills',
            popular.map(p => p.skill_name).reverse(),
            popular.map(p => Number(p.avg_score)).reverse(),
            '平均得分', 3
        ));
    } catch (e) {
        container.innerHTML = `<div class="empty-state">加载失败: ${e.message}</div>`;
    }
}

// ===== 学习效率 =====
async function renderEfficiency(container) {
    try {
        const lid = currentLearner;
        const [eff, byDiff, board] = await Promise.all([
            api.skillEfficiency(lid), api.efficiencyByDiff(lid), api.efficiencyLeaderboard()
        ]);

        container.innerHTML = `
            <div class="charts-grid">
                <div class="card"><div class="card-title">⚡ 技能学习效率</div><div id="chartEff" class="chart-container"></div></div>
                <div class="card"><div class="card-title">📊 按难度等级效率</div><div id="chartByDiff" class="chart-container"></div></div>
            </div>
            <div class="charts-grid">
                <div class="card"><div class="card-title">🏆 效率排行榜</div><div id="chartBoard" class="chart-container"></div></div>
                <div class="card">
                    <div class="card-title">📋 效率排行详情</div>
                    ${makeTable(['学习者', '综合效率', '技能数', '总学时', '平均分'],
                        board.map(b => `<tr><td>${b.learner_id}</td><td>${num(b.overall_efficiency, 2)}</td>
                            <td>${b.total_skills}</td><td>${num(b.total_hours, 0)}h</td><td>${num(b.avg_score)}</td></tr>`)
                    )}
                </div>
            </div>
            <div class="card">
                <div class="card-title">📋 技能效率详情</div>
                ${makeTable(['学习者', '技能', '类别', '难度', '得分', '学时', '效率', '最佳单次'],
                    eff.map(e => `<tr><td>${e.learner_id}</td><td>${e.skill_name}</td><td>${e.category}</td>
                        <td>${e.difficulty_level}</td><td>${e.latest_score}</td><td>${e.total_hours}h</td>
                        <td>${num(e.efficiency, 2)}</td><td>${num(e.best_single_efficiency, 2)}</td></tr>`)
                )}
            </div>`;

        // 效率图
        if (eff.length > 0) {
            allCharts.push(renderBarChart('chartEff',
                eff.map(e => `${e.learner_id}-${e.skill_name}`),
                eff.map(e => Number(e.efficiency)),
                '', { rotate: 30 }
            ));
        }

        // 按难度
        if (byDiff.length > 0) {
            const difficulties = [...new Set(byDiff.map(d => d.difficulty_level))].sort();
            const learners = [...new Set(byDiff.map(d => d.learner_id))].sort();
            allCharts.push(renderLineChart('chartByDiff',
                difficulties.map(d => '难度' + d),
                learners.map(l => ({
                    name: l,
                    data: difficulties.map(d => { const r = byDiff.find(x => x.learner_id === l && x.difficulty_level === d); return r ? Number(r.avg_efficiency) : 0; })
                })), ''));
        }

        // 排行
        allCharts.push(renderBarChart('chartBoard',
            board.map(b => b.learner_id),
            board.map(b => Number(b.overall_efficiency)),
            ''
        ));
    } catch (e) {
        container.innerHTML = `<div class="empty-state">加载失败: ${e.message}</div>`;
    }
}

// ===== 技能衰退 =====
async function renderDecay(container) {
    try {
        const lid = currentLearner;
        const [decay, review] = await Promise.all([
            api.skillDecay(lid), api.reviewNeeded(lid)
        ]);

        let prioritySection = '';
        if (lid) {
            const priority = await api.reviewPriority(lid);
            prioritySection = `
                <div class="card">
                    <div class="card-title">🔴 复习优先级 (${lid})</div>
                    ${priority.length === 0 ? '<div class="empty-state">暂无需要复习的技能</div>' :
                    makeTable(['技能', '原始分', '预测分', '衰退量', '难度', '复习优先级'],
                        priority.map(p => `<tr><td>${p.skill_name}</td><td>${p.latest_score}</td>
                            <td>${num(p.predicted_score)}</td><td>${num(p.decay_amount)}</td>
                            <td>${p.difficulty_level}</td><td><strong>${num(p.review_priority)}</strong></td></tr>`)
                    )}
                </div>`;
        }

        container.innerHTML = `
            <div class="charts-grid">
                <div class="card"><div class="card-title">📉 技能衰退预测</div><div id="chartDecay" class="chart-container"></div></div>
                <div class="card"><div class="card-title">⚠️ 需要复习的技能</div><div id="chartReview" class="chart-container"></div></div>
            </div>
            ${prioritySection}
            <div class="card">
                <div class="card-title">📋 衰退预测详情</div>
                ${makeTable(['学习者', '技能', '类别', '原始分', '上次学习', '天数', '预测分', '衰退量'],
                    decay.map(d => `<tr><td>${d.learner_id}</td><td>${d.skill_name}</td><td>${d.category}</td>
                        <td>${d.latest_score}</td><td>${d.last_study_date}</td><td>${d.days_since_last}</td>
                        <td>${num(d.predicted_score)}</td><td>${num(d.decay_amount)}</td></tr>`)
                )}
            </div>`;

        // 衰退图
        if (decay.length > 0) {
            const labels = decay.map(d => `${d.learner_id}-${d.skill_name}`);
            allCharts.push(renderLineChart('chartDecay', labels, [
                { name: '原始得分', data: decay.map(d => Number(d.latest_score)) },
                { name: '预测得分', data: decay.map(d => Number(d.predicted_score)) },
            ], ''));
        }

        // 需复习
        if (review.length > 0) {
            allCharts.push(renderHBarChart('chartReview',
                review.map(r => `${r.learner_id}-${r.skill_name}`).reverse(),
                review.map(r => Number(r.decay_amount)).reverse(),
                '衰退量', 5
            ));
        }
    } catch (e) {
        container.innerHTML = `<div class="empty-state">加载失败: ${e.message}</div>`;
    }
}

// ===== 数据导入 =====
async function renderImport(container) {
    container.innerHTML = `
        <div class="card" style="max-width:900px;">
            <div class="card-title">📥 MOOC 数据导入</div>
            <p style="color:var(--text-secondary);margin-bottom:16px;">
                将你的MOOC平台导出的CSV文件放到 <code style="background:#f1f5f9;padding:2px 6px;border-radius:4px;">data/mooc_raw/</code> 目录中，或指定你本地电脑上的CSV文件夹路径。
            </p>

            <div style="background:#eff6ff;border:1px solid #bfdbfe;border-radius:8px;padding:16px;margin-bottom:20px;">
                <h4 style="margin:0 0 8px 0;color:#1e40af;">📂 自定义数据路径（可选）</h4>
                <p style="color:#1e40af;font-size:13px;margin:0 0 8px 0;">如果CSV文件太大无法上传到项目中，可以直接指定本地文件夹路径：</p>
                <input type="text" id="sourcePath" placeholder="例如: C:\\Users\\你的用户名\\Desktop\\moocdata"
                    style="width:100%;padding:10px 12px;border:1px solid #93c5fd;border-radius:6px;font-size:14px;box-sizing:border-box;" />
                <p style="color:#6b7280;font-size:12px;margin:6px 0 0 0;">留空则使用默认的 data/mooc_raw/ 目录</p>
            </div>

            <div style="background:var(--bg);border-radius:8px;padding:16px;margin-bottom:20px;">
                <h4 style="margin:0 0 8px 0;">📋 支持的列名（中英文均可）</h4>
                <table class="data-table" style="font-size:13px;">
                    <thead><tr><th>数据类型</th><th>支持的列名</th></tr></thead>
                    <tbody>
                        <tr><td><strong>用户ID</strong></td><td>user_id, 用户ID, student_id, learner_id, 学号</td></tr>
                        <tr><td><strong>姓名</strong></td><td>username, name, 用户名, 姓名, 昵称</td></tr>
                        <tr><td><strong>课程ID</strong></td><td>course_id, 课程ID, 课程编号</td></tr>
                        <tr><td><strong>课程名</strong></td><td>course_name, 课程名, 课程名称, 课程</td></tr>
                        <tr><td><strong>分类</strong></td><td>category, 分类, 类别, 领域, field</td></tr>
                        <tr><td><strong>成绩</strong></td><td>score, grade, 成绩, 分数, 得分</td></tr>
                        <tr><td><strong>完成日期</strong></td><td>completion_date, 完成时间, 完成日期, finish_date</td></tr>
                        <tr><td><strong>学习时长</strong></td><td>study_hours, 学习时长, hours, duration, 学时</td></tr>
                    </tbody>
                </table>
            </div>

            <div id="detectResult" style="margin-bottom:20px;"></div>

            <div style="display:flex;gap:12px;">
                <button onclick="detectMoocFiles()" style="padding:10px 24px;background:var(--primary);color:white;border:none;border-radius:8px;cursor:pointer;font-size:15px;">
                    🔍 检测文件
                </button>
                <button onclick="importMoocData()" id="btnImport" style="padding:10px 24px;background:#10b981;color:white;border:none;border-radius:8px;cursor:pointer;font-size:15px;" disabled>
                    🚀 执行导入
                </button>
            </div>

            <div id="importResult" style="margin-top:20px;"></div>
        </div>
    `;

    detectMoocFiles();
}

async function detectMoocFiles() {
    const el = document.getElementById('detectResult');
    const sourcePath = document.getElementById('sourcePath').value.trim();
    el.innerHTML = '<div class="loading">检测中...</div>';
    try {
        const data = await api.detectRawFiles(sourcePath || undefined);
        if (!data.exists) {
            el.innerHTML = '<div class="empty-state" style="padding:16px;">\u26a0\ufe0f ' + data.message + '</div>';
            return;
        }
        if (data.csvFileCount === 0) {
            el.innerHTML = '<div class="empty-state" style="padding:16px;">\ud83d\udcc2 mooc_raw \u76ee\u5f55\u4e3a\u7a7a\uff0c\u8bf7\u653e\u5165CSV\u6587\u4ef6<br><small style="color:var(--text-secondary);">' + data.directory + '</small></div>';
            return;
        }

        let html = '<div style="background:#f0fdf4;border:1px solid #bbf7d0;border-radius:8px;padding:16px;">' +
            '<h4 style="margin:0 0 8px 0;color:#166534;">\u2705 \u68c0\u6d4b\u5230 ' + data.csvFileCount + ' \u4e2aCSV\u6587\u4ef6</h4>' +
            '<table class="data-table" style="font-size:13px;">' +
            '<thead><tr><th>\u6587\u4ef6\u540d</th><th>\u884c\u6570</th><th>\u8bc6\u522b\u7c7b\u578b</th><th>\u5217</th></tr></thead><tbody>';

        for (const f of data.files) {
            const statusIcon = f.status === 'ok' ? '\u2705' : '\u274c';
            html += '<tr><td>' + statusIcon + ' ' + f.fileName + '</td>' +
                '<td>' + (f.rowCount != null ? f.rowCount : '-') + '</td>' +
                '<td><strong>' + (f.detectedType || '\u672a\u77e5') + '</strong></td>' +
                '<td style="font-size:12px;color:var(--text-secondary);">' + (f.columns ? f.columns.join(', ') : '-') + '</td></tr>';
        }
        html += '</tbody></table></div>';
        el.innerHTML = html;
        document.getElementById('btnImport').disabled = false;
    } catch (e) {
        el.innerHTML = '<div class="empty-state" style="padding:16px;">\u68c0\u6d4b\u5931\u8d25: ' + e.message + '</div>';
    }
}

async function importMoocData() {
    const btn = document.getElementById('btnImport');
    const el = document.getElementById('importResult');
    const sourcePath = document.getElementById('sourcePath').value.trim();
    btn.disabled = true;
    btn.textContent = '\u23f3 \u5bfc\u5165\u4e2d...';
    el.innerHTML = '<div class="loading">\u6b63\u5728\u8f6c\u6362\u6570\u636e\u5e76\u91cd\u65b0\u52a0\u8f7d...</div>';

    try {
        const data = await api.importMooc(sourcePath || undefined);
        if (data.success) {
            el.innerHTML = '<div style="background:#f0fdf4;border:1px solid #bbf7d0;border-radius:8px;padding:16px;">' +
                '<h4 style="margin:0 0 8px 0;color:#166534;">\ud83c\udf89 ' + data.message + '</h4>' +
                '<div class="stats-grid" style="margin-top:12px;">' +
                '<div class="stat-card"><div class="stat-value">' + data.learnerCount + '</div><div class="stat-label">\u5b66\u4e60\u8005</div></div>' +
                '<div class="stat-card"><div class="stat-value">' + data.skillCount + '</div><div class="stat-label">\u6280\u80fd</div></div>' +
                '<div class="stat-card"><div class="stat-value">' + data.courseCount + '</div><div class="stat-label">\u8bfe\u7a0b</div></div>' +
                '<div class="stat-card"><div class="stat-value">' + data.recordCount + '</div><div class="stat-label">\u5b66\u4e60\u8bb0\u5f55</div></div>' +
                '</div>' +
                (data.reloaded ? '<p style="margin-top:12px;color:#166534;">\u2705 Spark \u6570\u636e\u5df2\u91cd\u65b0\u52a0\u8f7d\uff0c\u53ef\u4ee5\u5f00\u59cb\u5206\u6790\uff01</p>'
                    : '<p style="margin-top:12px;color:#b91c1c;">\u26a0\ufe0f \u6570\u636e\u91cd\u8f7d\u5931\u8d25: ' + (data.reloadError || '') + '</p>') +
                (data.warnings && data.warnings.length > 0 ? '<p style="margin-top:8px;color:#92400e;">\u26a0\ufe0f \u8b66\u544a: ' + data.warnings.join('; ') + '</p>' : '') +
                '</div>';
            loadLearnerSelector();
        } else {
            el.innerHTML = '<div class="empty-state" style="padding:16px;color:#b91c1c;">\u274c ' + data.message + '</div>';
        }
    } catch (e) {
        el.innerHTML = '<div class="empty-state" style="padding:16px;">\u5bfc\u5165\u5931\u8d25: ' + e.message + '</div>';
    }

    btn.disabled = false;
    btn.textContent = '\ud83d\ude80 \u6267\u884c\u5bfc\u5165';
}
