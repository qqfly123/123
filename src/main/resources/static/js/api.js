/**
 * 终身学习者能力分析可视化系统 - API 层
 */
const API_BASE = '/api';

const api = {
    async get(endpoint) {
        const resp = await fetch(`${API_BASE}${endpoint}`);
        if (!resp.ok) throw new Error(`API error: ${resp.status}`);
        return resp.json();
    },

    async post(endpoint) {
        const resp = await fetch(`${API_BASE}${endpoint}`, { method: 'POST' });
        if (!resp.ok) throw new Error(`API error: ${resp.status}`);
        return resp.json();
    },

    // 概览
    overview: () => api.get('/overview'),
    learners: () => api.get('/learners'),

    // 阶段一
    skillProfile: (id) => api.get(`/profile/skills${id ? '?learnerId=' + id : ''}`),
    categoryDist: (id) => api.get(`/profile/categories${id ? '?learnerId=' + id : ''}`),
    overallRating: () => api.get('/profile/ratings'),
    evolutionTimeline: (id) => api.get(`/evolution/timeline${id ? '?learnerId=' + id : ''}`),
    progressTrend: (id) => api.get(`/evolution/trend${id ? '?learnerId=' + id : ''}`),
    monthlyActivity: (id) => api.get(`/evolution/monthly${id ? '?learnerId=' + id : ''}`),
    skillGaps: (id) => api.get(`/path/gaps/${id}`),
    recommendedCourses: (id) => api.get(`/path/courses/${id}`),
    learningPath: (id) => api.get(`/path/learning/${id}`),

    // 阶段二
    allSimilarities: () => api.get('/similarity/all'),
    similarLearners: (id, n) => api.get(`/similarity/${id}?topN=${n || 5}`),
    collaborative: (id) => api.get(`/collaborative/${id}`),
    popularSkills: (n) => api.get(`/popular-skills?topN=${n || 10}`),

    // 阶段三
    skillEfficiency: (id) => api.get(`/efficiency/skills${id ? '?learnerId=' + id : ''}`),
    efficiencyByDiff: (id) => api.get(`/efficiency/difficulty${id ? '?learnerId=' + id : ''}`),
    efficiencyLeaderboard: () => api.get('/efficiency/leaderboard'),
    skillDecay: (id) => api.get(`/decay/predict${id ? '?learnerId=' + id : ''}`),
    reviewNeeded: (id) => api.get(`/decay/review${id ? '?learnerId=' + id : ''}`),
    reviewPriority: (id) => api.get(`/decay/priority/${id}`),

    // 数据导入
    detectRawFiles: (sourcePath) => api.get('/import/detect' + (sourcePath ? '?sourcePath=' + encodeURIComponent(sourcePath) : '')),
    importMooc: (sourcePath) => api.post('/import/mooc' + (sourcePath ? '?sourcePath=' + encodeURIComponent(sourcePath) : '')),
};
