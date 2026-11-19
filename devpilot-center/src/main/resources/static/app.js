(() => {
    "use strict";

    const view = document.getElementById("view");
    const title = document.getElementById("page-title");
    const toast = document.getElementById("toast");
    const healthBadge = document.getElementById("health-badge");

    const TITLES = {
        overview: "总览",
        projects: "项目注册",
        bugs: "BUG 管理",
        "bug-detail": "BUG 详情",
        tasks: "研发任务",
        "task-detail": "任务详情",
        executor: "执行器"
    };

    const STATE_LABEL = {
        CREATED: "已创建", BUG_ANALYZING: "分析中", PROJECT_MATCHING: "匹配项目",
        WAITING_PROJECT_CONFIRM: "待确认项目", ANALYZING: "需求分析", WAITING_CONFIRM: "待确认计划",
        PLANNING: "任务拆分", READY: "待执行", PREPARING_WORKSPACE: "准备工作区",
        DEVELOPING: "开发中", TESTING: "测试中", REVIEWING: "审查中",
        FAILED_NEEDS_HUMAN: "需人工处理", WAITING_APPROVAL: "待交付审批",
        GIT_DELIVERING: "Git 交付", SVN_DCOMMITTING: "SVN 回写", BUILDING: "CI 构建",
        DEPLOYING_TEST: "部署测试环境", ACCEPTANCE: "待验收",
        WAITING_PROD_APPROVAL: "待生产审批", COMPLETED: "已完成", CANCELLED: "已取消"
    };

    const STATE_TONE = {
        COMPLETED: "ok", FAILED_NEEDS_HUMAN: "danger", CANCELLED: "warn",
        WAITING_APPROVAL: "warn", WAITING_PROJECT_CONFIRM: "warn", WAITING_CONFIRM: "warn",
        DEVELOPING: "info", TESTING: "info", REVIEWING: "info", READY: "info"
    };

    async function api(path, options = {}) {
        const response = await fetch(path, {
            headers: { "Content-Type": "application/json", ...(options.headers || {}) },
            ...options
        });
        if (response.status === 204) {
            return null;
        }
        const text = await response.text();
        let body = null;
        if (text) {
            try { body = JSON.parse(text); } catch { body = { message: text }; }
        }
        if (!response.ok) {
            const message = body && (body.message || body.error)
                ? body.message || body.error
                : `HTTP ${response.status}`;
            throw new Error(message);
        }
        return body;
    }

    function notify(message, kind = "info") {
        toast.textContent = message;
        toast.className = `toast ${kind === "error" ? "error" : ""}`;
        toast.hidden = false;
        clearTimeout(notify.timer);
        notify.timer = setTimeout(() => { toast.hidden = true; }, 3800);
    }

    function el(tag, attrs = {}, ...children) {
        const node = document.createElement(tag);
        Object.entries(attrs).forEach(([key, value]) => {
            if (key === "class") node.className = value;
            else if (key === "html") node.innerHTML = value;
            else if (key.startsWith("on")) node.addEventListener(key.slice(2), value);
            else if (value !== undefined && value !== null) node.setAttribute(key, value);
        });
        children.flat().forEach(child => {
            if (child === null || child === undefined) return;
            node.append(child instanceof Node ? child : document.createTextNode(String(child)));
        });
        return node;
    }

    function escapeHtml(value) {
        return String(value ?? "").replace(/[&<>"']/g, c => ({
            "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
        })[c]);
    }

    function badge(state) {
        return el("span", { class: `badge ${STATE_TONE[state] || ""}` },
            STATE_LABEL[state] || state || "-");
    }

    function fmtTime(value) {
        if (!value) return "-";
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return value;
        return date.toLocaleString("zh-CN", { hour12: false });
    }

    function render(route, params, nav) {
        const activeNav = nav || (route === "task-detail" ? "tasks"
            : route === "bug-detail" ? "bugs" : route);
        document.querySelectorAll("#nav a").forEach(a => {
            a.classList.toggle("active", a.dataset.route === activeNav);
        });
        title.textContent = TITLES[route] || "DevPilot";
        view.replaceChildren(el("div", { class: "empty" }, "加载中…"));
        ROUTES[route](params).catch(error => {
            view.replaceChildren(el("div", { class: "empty" }, `加载失败：${error.message}`));
            notify(error.message, "error");
        });
    }

    const ROUTES = {};

    ROUTES.overview = async () => {
        const data = await api("/api/dashboard/overview");
        const executor = data.executor || {};
        const cards = el("div", { class: "cards" },
            stat("注册项目", data.projects),
            stat("BUG", data.bugs),
            stat("研发任务", data.tasks),
            stat("执行器", executor.healthy ? "在线" : "未启用")
        );
        const stateRows = Object.entries(data.tasksByState || {})
            .sort((a, b) => b[1] - a[1])
            .map(([state, count]) => el("tr", {},
                el("td", {}, badge(state)), el("td", { class: "mono" }, count)));

        const recentBugs = (data.recentBugs || []).map(bug => el("tr", {},
            el("td", { class: "mono" }, link(`#/bugs/${bug.id}`, bug.id)),
            el("td", {}, bug.title),
            el("td", {}, bug.status || "-")));

        const recentTasks = (data.recentTasks || []).map(task => el("tr", {},
            el("td", { class: "mono" }, link(`#/tasks/${task.id}`, task.id)),
            el("td", {}, task.title || "-"),
            el("td", {}, badge(task.state))));

        view.replaceChildren(
            cards,
            el("div", { class: "split" },
                cardsPanel("任务状态分布", el("table", {},
                    el("thead", {}, el("tr", {}, el("th", {}, "状态"), el("th", {}, "数量"))),
                    el("tbody", {}, stateRows.length ? stateRows : emptyRow(2)))),
                cardsPanel("执行器健康", el("div", { class: "panel-body" },
                    el("p", {}, executor.detail || "暂无信息"),
                    el("p", { class: "hint" },
                        `模式：${executor.mode || "-"} · 检查时间：${fmtTime(executor.checkedAt)}`)))),
            cardsPanel("最近 BUG", el("table", {},
                el("thead", {}, el("tr", {}, el("th", {}, "编号"), el("th", {}, "标题"), el("th", {}, "状态"))),
                el("tbody", {}, recentBugs.length ? recentBugs : emptyRow(3)))),
            cardsPanel("最近任务", el("table", {},
                el("thead", {}, el("tr", {}, el("th", {}, "任务"), el("th", {}, "标题"), el("th", {}, "状态"))),
                el("tbody", {}, recentTasks.length ? recentTasks : emptyRow(3))))
        );
    };

    ROUTES.projects = async () => {
        const projects = await api("/api/projects");
        const rows = projects.map(project => el("tr", {},
            el("td", { class: "mono" }, project.id),
            el("td", {}, project.name),
            el("td", {}, project.repositoryType === "GIT_SVN" ? "Git-SVN" : "Git"),
            el("td", { class: "mono" }, project.defaultBranch || project.targetRef || "-"),
            el("td", {}, project.owner || "-"),
            el("td", {}, el("span", { class: `badge ${project.active ? "ok" : "warn"}` },
                project.active ? "启用" : "停用"))));

        view.replaceChildren(
            cardsPanel("项目列表", el("table", {},
                el("thead", {}, el("tr", {},
                    el("th", {}, "ID"), el("th", {}, "名称"), el("th", {}, "仓库类型"),
                    el("th", {}, "目标分支"), el("th", {}, "负责人"), el("th", {}, "状态"))),
                el("tbody", {}, rows.length ? rows : emptyRow(6))),
                actionButton("新增项目", () => projectForm())),
            projectForm()
        );
    };

    function projectForm() {
        const form = el("form", { class: "grid" },
            field("项目 ID", el("input", { name: "id", placeholder: "tyjr-jcxxcj-service", required: "" })),
            field("项目名称", el("input", { name: "name", placeholder: "企业信息采集服务", required: "" })),
            field("别名（逗号分隔）", el("input", { name: "aliases", placeholder: "jcxxcj,企业采集" })),
            field("服务名（逗号分隔）", el("input", { name: "serviceNames", placeholder: "jcxxcj-service" })),
            field("日志包名（逗号分隔）", el("input", { name: "logPackages", placeholder: "com.example.jcxxcj" })),
            field("代码路径关键字（逗号分隔）", el("input", { name: "codePathKeywords", placeholder: "sync-service" })),
            field("仓库类型", el("select", { name: "repositoryType" },
                el("option", { value: "GIT" }, "Git"),
                el("option", { value: "GIT_SVN" }, "Git-SVN"))),
            field("Git 仓库地址", el("input", { name: "repositoryUrl", placeholder: "ssh://git@git.example.com/app.git" })),
            field("SVN 地址", el("input", { name: "svnUrl", placeholder: "https://svn.example.com/repos/app" })),
            field("默认分支", el("input", { name: "defaultBranch", placeholder: "develop" })),
            field("目标 Ref", el("input", { name: "targetRef", placeholder: "refs/remotes/svn/trunk" })),
            field("工作区根目录", el("input", { name: "workspaceRoot", placeholder: "workspaces/app" })),
            field("构建命令键", el("input", { name: "buildCommandKey", placeholder: "maven-verify" })),
            field("测试命令键", el("input", { name: "testCommandKey", placeholder: "maven-test" })),
            el("label", { class: "field full" }, "描述",
                el("textarea", { name: "description", placeholder: "项目用途、技术栈与约束" })),
            el("div", { class: "full row" },
                el("button", { type: "submit" }, "保存项目"),
                el("span", { class: "hint" }, "Git 项目必须填仓库地址；Git-SVN 项目必须填 SVN 地址。")),
            el("div", { class: "full", id: "project-result" })
        );
        form.addEventListener("submit", async event => {
            event.preventDefault();
            const data = Object.fromEntries(new FormData(form).entries());
            const payload = {
                ...data,
                aliases: splitList(data.aliases),
                serviceNames: splitList(data.serviceNames),
                logPackages: splitList(data.logPackages),
                codePathKeywords: splitList(data.codePathKeywords),
                requireReview: true,
                requireApproval: true,
                createMergeRequest: false,
                autoPush: false
            };
            try {
                await api("/api/projects", { method: "POST", body: JSON.stringify(payload) });
                notify("项目已注册");
                form.reset();
                render("projects");
            } catch (error) {
                notify(error.message, "error");
            }
        });
        return cardsPanel("注册新项目", form);
    }

    ROUTES.bugs = async () => {
        const bugs = await api("/api/bugs?size=100");
        const rows = bugs.map(bug => el("tr", {},
            el("td", { class: "mono" }, link(`#/bugs/${bug.id}`, bug.id)),
            el("td", {}, bug.title),
            el("td", {},
                el("span", { class: `badge ${bug.severity === "HIGH" || bug.severity === "CRITICAL" ? "danger" : ""}` },
                    bug.severity || "MEDIUM")),
            el("td", {}, bug.productionIssue ? el("span", { class: "badge warn" }, "生产") : "-"),
            el("td", {}, bug.specifiedProjectId || "-"),
            el("td", {}, el("span", { class: "badge info" }, bug.status || "CREATED")),
            el("td", {}, fmtTime(bug.createdAt))));

        view.replaceChildren(
            cardsPanel("BUG 列表", el("table", {},
                el("thead", {}, el("tr", {},
                    el("th", {}, "编号"), el("th", {}, "标题"), el("th", {}, "严重度"),
                    el("th", {}, "生产"), el("th", {}, "指定项目"), el("th", {}, "状态"), el("th", {}, "提交时间"))),
                el("tbody", {}, rows.length ? rows : emptyRow(7)))),
            bugForm()
        );
    };

    function bugForm() {
        const form = el("form", { class: "grid" },
            field("标题", el("input", { name: "title", required: "", placeholder: "企业信息同步出现重复数据" })),
            field("严重度", el("select", { name: "severity" },
                el("option", { value: "LOW" }, "LOW"),
                el("option", { value: "MEDIUM", selected: "" }, "MEDIUM"),
                el("option", { value: "HIGH" }, "HIGH"),
                el("option", { value: "CRITICAL" }, "CRITICAL"))),
            field("指定项目（可选）", el("select", { name: "specifiedProjectId", id: "bug-project" },
                el("option", { value: "" }, "由系统自动匹配"))),
            field("生产问题", el("select", { name: "productionIssue" },
                el("option", { value: "false" }, "否"),
                el("option", { value: "true" }, "是"))),
            el("label", { class: "field full" }, "描述",
                el("textarea", { name: "description", required: "", placeholder: "问题现象、影响范围" })),
            el("label", { class: "field full" }, "复现步骤",
                el("textarea", { name: "reproductionSteps", placeholder: "1. …\n2. …" })),
            field("期望结果", el("input", { name: "expectedResult", placeholder: "同步后仅保留一条" })),
            field("实际结果", el("input", { name: "actualResult", placeholder: "出现重复记录" })),
            field("环境", el("input", { name: "environment", placeholder: "prod / test" })),
            field("版本", el("input", { name: "version", placeholder: "2.3.12" })),
            el("label", { class: "field full" }, "错误日志 / 堆栈",
                el("textarea", { name: "errorLog", placeholder: "java.sql.SQLIntegrityConstraintViolationException: Duplicate entry ..." })),
            el("div", { class: "full row" },
                el("button", { type: "submit" }, "提交 BUG"),
                el("span", { class: "hint" }, "提交后可在详情页执行 AI 分析与项目匹配。"))
        );
        api("/api/projects").then(projects => {
            const select = form.querySelector("#bug-project");
            projects.forEach(project => select.append(el("option", { value: project.id }, project.name)));
        }).catch(() => {});
        form.addEventListener("submit", async event => {
            event.preventDefault();
            const data = Object.fromEntries(new FormData(form).entries());
            try {
                const created = await api("/api/bugs", {
                    method: "POST",
                    body: JSON.stringify({ ...data, productionIssue: data.productionIssue === "true" })
                });
                notify("BUG 已提交");
                location.hash = `#/bugs/${created.id}`;
            } catch (error) {
                notify(error.message, "error");
            }
        });
        return cardsPanel("提交 BUG", form);
    }

    ROUTES["bug-detail"] = async params => {
        const bug = await api(`/api/bugs/${params[0]}`);
        const analysis = bug.analysis;
        const match = bug.match;

        const actions = el("div", { class: "row" },
            el("button", {
                onclick: async event => {
                    event.currentTarget.disabled = true;
                    try {
                        await api(`/api/bugs/${bug.id}/analyze`, { method: "POST" });
                        notify("分析完成");
                        render("bug-detail", params);
                    } catch (error) {
                        notify(error.message, "error");
                        event.currentTarget.disabled = false;
                    }
                }
            }, "执行 AI 分析"),
            el("button", {
                class: "ghost",
                onclick: async event => {
                    event.currentTarget.disabled = true;
                    try {
                        await api(`/api/bugs/${bug.id}/match-project`, { method: "POST" });
                        notify("项目匹配完成");
                        render("bug-detail", params);
                    } catch (error) {
                        notify(error.message, "error");
                        event.currentTarget.disabled = false;
                    }
                }
            }, "匹配项目"),
            el("button", {
                class: "ghost",
                onclick: async event => {
                    event.currentTarget.disabled = true;
                    try {
                        const outcome = await api(`/api/intake/bugs/${bug.id}`, { method: "POST" });
                        if (outcome.taskCreated) {
                            notify(`任务已创建：${outcome.task.taskId}`);
                            location.hash = `#/tasks/${outcome.task.taskId}`;
                        } else {
                            notify("未创建任务：项目未匹配或需人工确认", "error");
                        }
                    } catch (error) {
                        notify(error.message, "error");
                        event.currentTarget.disabled = false;
                    }
                }
            }, "一键创建任务")
        );

        view.replaceChildren(
            cardsPanel(`BUG ${bug.id}`, el("div", {},
                el("div", { class: "row spread" },
                    el("h3", { style: "margin:0" }, bug.title),
                    el("span", { class: "badge info" }, bug.status || "-")),
                el("p", { class: "hint" }, `严重度 ${bug.severity} · 生产问题 ${bug.productionIssue ? "是" : "否"} · 提交于 ${fmtTime(bug.createdAt)}`),
                el("p", {}, bug.description),
                actions)),
            el("div", { class: "split" },
                cardsPanel("AI 分析", analysis ? definitionList({
                    "问题类型": analysis.problemType,
                    "影响范围": joinList(analysis.impactScope),
                    "疑似模块": joinList(analysis.suspectedModules),
                    "错误摘要": analysis.errorLogSummary,
                    "可复现性": analysis.reproducibility,
                    "缺失信息": joinList(analysis.missingInfo)
                }) : el("p", { class: "hint" }, "尚未分析")),
                cardsPanel("项目匹配", match ? definitionList({
                    "项目": match.projectId || "未匹配",
                    "置信度": Number(match.confidence).toFixed(2),
                    "决策": match.decision,
                    "依据": joinList(match.signals)
                }) : el("p", { class: "hint" }, "尚未匹配"))),
            cardsPanel("原始错误日志", el("pre", { class: "mono", style: "white-space:pre-wrap;margin:0" },
                bug.errorLog || bug.actualResult || "未提供"))
        );
    };

    ROUTES.tasks = async () => {
        const page = await api("/api/tasks?size=100");
        const tasks = page.content || [];
        const rows = tasks.map(task => el("tr", {},
            el("td", { class: "mono" }, link(`#/tasks/${task.id}`, task.id)),
            el("td", {}, task.title || "-"),
            el("td", {}, task.projectId),
            el("td", {}, badge(task.state)),
            el("td", { class: "mono" }, `${task.repairRound}/${task.maxRepairRounds}`),
            el("td", {}, fmtTime(task.updatedAt))));

        view.replaceChildren(
            cardsPanel("任务列表", el("table", {},
                el("thead", {}, el("tr", {},
                    el("th", {}, "任务"), el("th", {}, "标题"), el("th", {}, "项目"),
                    el("th", {}, "状态"), el("th", {}, "修复轮次"), el("th", {}, "更新时间"))),
                el("tbody", {}, rows.length ? rows : emptyRow(6))))
        );
    };

    ROUTES["task-detail"] = async params => {
        const taskId = params[0];
        const [status, report] = await Promise.all([
            api(`/api/tasks/${taskId}/status`),
            api(`/api/tasks/${taskId}/report`)
        ]);

        const actions = el("div", { class: "row" }, actionButtons(taskId, status));
        const timeline = el("ul", { class: "timeline" },
            (report.events || []).map(event => el("li", {},
                el("time", {}, fmtTime(event.createdAt)),
                el("strong", {}, ` ${event.event} · ${event.fromState} → ${event.toState}`),
                el("p", {}, `${event.actor || "-"} ${event.detail || ""}`))));

        const subtasks = (report.subtasks || []).map(subtask => el("tr", {},
            el("td", { class: "mono" }, subtask.id),
            el("td", {}, subtask.role),
            el("td", {}, subtask.type || "-"),
            el("td", {}, joinList(subtask.dependsOn) || "-"),
            el("td", {}, el("span", { class: "badge" }, subtask.status || "-"))));

        const tests = (report.tests || []).map(test => el("tr", {},
            el("td", {}, `#${test.round}`),
            el("td", {}, el("span", { class: `badge ${test.passed ? "ok" : "danger"}` },
                test.passed ? "通过" : "失败")),
            el("td", { class: "mono" }, test.exitCode ?? "-"),
            el("td", {}, test.summary || "-")));

        const reviews = (report.reviews || []).map(review => el("tr", {},
            el("td", {}, `#${review.round}`),
            el("td", {}, el("span", { class: `badge ${review.passed ? "ok" : "danger"}` },
                review.passed ? "通过" : "阻断")),
            el("td", {}, joinList(review.blocking) || "-"),
            el("td", {}, joinList(review.risks) || "-")));

        view.replaceChildren(
            cardsPanel(`任务 ${taskId}`, el("div", {},
                el("div", { class: "row spread" },
                    el("h3", { style: "margin:0" }, report.title || taskId),
                    badge(status.state)),
                el("p", { class: "hint" },
                    `BUG ${report.bugId} · 项目 ${report.projectId} · 修复轮次 ${status.repairRound}/${status.maxRepairRounds}`),
                actions)),
            el("div", { class: "split" },
                cardsPanel("子任务", el("table", {},
                    el("thead", {}, el("tr", {},
                        el("th", {}, "子任务"), el("th", {}, "角色"), el("th", {}, "类型"),
                        el("th", {}, "依赖"), el("th", {}, "状态"))),
                    el("tbody", {}, subtasks.length ? subtasks : emptyRow(5)))),
                cardsPanel("共识信息", definitionList({
                    "任务状态": STATE_LABEL[status.state] || status.state,
                    "工作区": status.workspaceId || "未创建",
                    "分支": status.branch || "-",
                    "匹配置信度": Number(status.matchedConfidence).toFixed(2),
                    "可执行事件": joinList(status.allowedEvents)
                }))),
            el("div", { class: "split" },
                cardsPanel("测试记录", el("table", {},
                    el("thead", {}, el("tr", {},
                        el("th", {}, "轮次"), el("th", {}, "结果"), el("th", {}, "退出码"), el("th", {}, "摘要"))),
                    el("tbody", {}, tests.length ? tests : emptyRow(4)))),
                cardsPanel("审查记录", el("table", {},
                    el("thead", {}, el("tr", {},
                        el("th", {}, "轮次"), el("th", {}, "结果"), el("th", {}, "阻断项"), el("th", {}, "风险"))),
                    el("tbody", {}, reviews.length ? reviews : emptyRow(4))))),
            cardsPanel("事件时间线", timeline)
        );
    };

    function actionButtons(taskId, status) {
        const buttons = [];
        const post = (label, path, body, className = "ghost") => {
            buttons.push(el("button", {
                class: className,
                onclick: async event => {
                    event.currentTarget.disabled = true;
                    try {
                        await api(path, { method: "POST", body: body ? JSON.stringify(body) : undefined });
                        notify(`${label}完成`);
                        render("task-detail", [taskId]);
                    } catch (error) {
                        notify(error.message, "error");
                        event.currentTarget.disabled = false;
                    }
                }
            }, label));
        };
        if (status.state === "PLANNING") {
            post("生成计划", `/api/tasks/${taskId}/plan`);
        }
        if (status.state === "WAITING_CONFIRM") {
            post("确认计划", `/api/tasks/${taskId}/approve-plan`);
        }
        if (status.state === "READY") {
            post("准备工作区", `/api/tasks/${taskId}/prepare-workspace`);
        }
        if (status.state === "DEVELOPING") {
            post("执行开发", `/api/pipeline/tasks/${taskId}/develop`);
        }
        if (status.state === "TESTING") {
            post("执行测试与审查", `/api/pipeline/tasks/${taskId}/verify`);
        }
        if (status.state === "WAITING_APPROVAL") {
            post("批准交付", `/api/tasks/${taskId}/approvals`,
                { approver: "ui", type: "DELIVERY", comment: "UI 审批" });
        }
        if (!["COMPLETED", "CANCELLED", "FAILED_NEEDS_HUMAN"].includes(status.state)) {
            buttons.push(el("button", {
                class: "danger",
                onclick: async () => {
                    if (!confirm("确认取消该任务？")) return;
                    try {
                        await api(`/api/tasks/${taskId}/cancel`, {
                            method: "POST",
                            body: JSON.stringify({ actor: "ui", reason: "人工取消" })
                        });
                        notify("任务已取消");
                        render("task-detail", [taskId]);
                    } catch (error) {
                        notify(error.message, "error");
                    }
                }
            }, "取消任务"));
        }
        if (!buttons.length) {
            buttons.push(el("span", { class: "hint" }, "当前状态无需人工操作"));
        }
        return buttons;
    }

    ROUTES.executor = async () => {
        let health;
        try {
            health = await api("/api/executor/health");
        } catch (error) {
            health = { healthy: false, detail: error.message };
        }
        view.replaceChildren(
            cardsPanel("Orca 执行器", el("div", {},
                definitionList({
                    "健康": health.healthy ? "在线" : "不可用",
                    "执行器": health.executorId || "-",
                    "模式": health.mode || "-",
                    "详情": health.detail || "-",
                    "检查时间": fmtTime(health.checkedAt)
                }),
                el("p", { class: "hint" },
                    "管理中心只通过 AgentExecutor 调用网关；网关按命令白名单执行，不接收任意 shell 命令。")))
        );
    };

    function stat(label, value) {
        return el("div", { class: "card" },
            el("div", { class: "label" }, label),
            el("div", { class: "value" }, value));
    }

    function cardsPanel(heading, content, action) {
        return el("section", { class: "panel" },
            el("div", { class: "panel-head" },
                el("h2", {}, heading),
                action || ""),
            content);
    }

    function field(label, control) {
        return el("label", { class: "field" }, label, control);
    }

    function link(href, text) {
        return el("a", { href }, text);
    }

    function emptyRow(cols) {
        return el("tr", {}, el("td", { colspan: cols, class: "empty" }, "暂无数据"));
    }

    function definitionList(map) {
        return el("dl", { style: "margin:0;display:grid;grid-template-columns:auto 1fr;gap:8px 16px" },
            Object.entries(map).flatMap(([key, value]) => [
                el("dt", { class: "hint", style: "margin:0" }, key),
                el("dd", { style: "margin:0;word-break:break-word" }, value || "-")
            ]));
    }

    function actionButton(label, handler) {
        return el("button", { class: "ghost small", onclick: handler }, label);
    }

    function joinList(value) {
        return Array.isArray(value) ? value.join("、") : (value || "");
    }

    function splitList(value) {
        return (value || "").split(/[,，]/).map(item => item.trim()).filter(Boolean);
    }

    function parseRoute() {
        const raw = location.hash.replace(/^#\/?/, "") || "overview";
        const [route, ...params] = raw.split("/");
        let resolved = route || "overview";
        if (resolved === "tasks" && params.length > 0) {
            resolved = "task-detail";
        }
        if (resolved === "bugs" && params.length > 0) {
            resolved = "bug-detail";
        }
        return { route: resolved, params, nav: route || "overview" };
    }

    async function refreshHealth() {
        try {
            const health = await api("/api/executor/health");
            healthBadge.textContent = `执行器：${health.healthy ? "在线" : "未启用"}`;
            healthBadge.style.color = health.healthy ? "var(--ok)" : "var(--muted)";
        } catch {
            healthBadge.textContent = "执行器：不可达";
            healthBadge.style.color = "var(--danger)";
        }
    }

    function boot() {
        const { route, params, nav } = parseRoute();
        if (!ROUTES[route]) {
            location.hash = "#/overview";
            return;
        }
        render(route, params, nav);
        refreshHealth();
    }

    document.getElementById("refresh").addEventListener("click", () => {
        const { route, params } = parseRoute();
        render(route, params);
        refreshHealth();
    });
    window.addEventListener("hashchange", boot);
    boot();
})();
