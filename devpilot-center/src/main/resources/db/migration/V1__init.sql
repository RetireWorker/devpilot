-- devpilot 阶段一基线
-- 以各模块 JPA 实体为唯一事实源；字段变更须同步迁移脚本。

CREATE TABLE dev_project (
    id                   VARCHAR(128) PRIMARY KEY,
    name                 VARCHAR(256) NOT NULL,
    description          VARCHAR(2000),
    aliases              TEXT,
    service_names        TEXT,
    log_packages         TEXT,
    code_path_keywords   TEXT,
    team                 VARCHAR(128),
    owner                VARCHAR(128),
    language             VARCHAR(64),
    java_version         VARCHAR(32),
    framework            VARCHAR(128),
    repository_type      VARCHAR(32)  NOT NULL,
    repository_url       VARCHAR(1000),
    default_branch       VARCHAR(256),
    svn_url              VARCHAR(1000),
    svn_layout           VARCHAR(64),
    target_ref           VARCHAR(256),
    workspace_strategy   VARCHAR(64),
    workspace_root       VARCHAR(512),
    build_command_key    VARCHAR(128),
    test_command_key     VARCHAR(128),
    create_merge_request BOOLEAN NOT NULL DEFAULT FALSE,
    auto_push            BOOLEAN NOT NULL DEFAULT FALSE,
    require_review       BOOLEAN NOT NULL DEFAULT TRUE,
    require_approval     BOOLEAN NOT NULL DEFAULT TRUE,
    active               BOOLEAN NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE dev_bug (
    id                   VARCHAR(80) PRIMARY KEY,
    title                VARCHAR(500) NOT NULL,
    description          TEXT NOT NULL,
    reproduction_steps   TEXT,
    expected_result      TEXT,
    actual_result        TEXT,
    environment          VARCHAR(500),
    version              VARCHAR(120),
    time_range           VARCHAR(200),
    error_log            TEXT,
    severity             VARCHAR(20) NOT NULL,
    production_issue     BOOLEAN NOT NULL DEFAULT FALSE,
    specified_project_id VARCHAR(120),
    status               VARCHAR(30) NOT NULL,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_dev_bug_status ON dev_bug (status);
CREATE INDEX idx_dev_bug_title_created ON dev_bug (title, created_at);

CREATE TABLE dev_bug_analysis (
    id                VARCHAR(100) PRIMARY KEY,
    bug_id            VARCHAR(80) NOT NULL,
    problem_type      VARCHAR(60),
    severity          VARCHAR(20),
    impact_scope      TEXT,
    error_log_summary TEXT,
    suspected_modules TEXT,
    reproducibility   VARCHAR(30),
    missing_info      TEXT,
    raw_json          TEXT,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_dev_bug_analysis_bug ON dev_bug_analysis (bug_id);

CREATE TABLE dev_bug_project_match (
    id              VARCHAR(100) PRIMARY KEY,
    bug_id          VARCHAR(80) NOT NULL,
    project_id      VARCHAR(120),
    confidence      DOUBLE PRECISION NOT NULL,
    signals         TEXT,
    candidates_json TEXT,
    decision        VARCHAR(40) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_dev_bug_match_bug ON dev_bug_project_match (bug_id);

CREATE TABLE dev_task (
    id                  VARCHAR(80) PRIMARY KEY,
    bug_id              VARCHAR(80),
    project_id          VARCHAR(120),
    title               VARCHAR(500),
    objective           TEXT,
    state               VARCHAR(40) NOT NULL,
    repair_round        INTEGER NOT NULL DEFAULT 0,
    max_repair_rounds   INTEGER NOT NULL DEFAULT 2,
    matched_confidence  DOUBLE PRECISION,
    workspace_id        VARCHAR(120),
    branch              VARCHAR(200),
    bug_analysis_json   TEXT,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_dev_task_state ON dev_task (state);
CREATE INDEX idx_dev_task_project ON dev_task (project_id);
CREATE UNIQUE INDEX uk_dev_task_bug ON dev_task (bug_id);

CREATE TABLE dev_task_plan (
    id                          BIGSERIAL PRIMARY KEY,
    task_id                     VARCHAR(80) NOT NULL,
    objective                   TEXT,
    requires_human_confirmation BOOLEAN NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX uk_dev_task_plan_task ON dev_task_plan (task_id);

CREATE TABLE dev_task_subtask (
    id              BIGSERIAL PRIMARY KEY,
    task_id         VARCHAR(80) NOT NULL,
    subtask_key     VARCHAR(80) NOT NULL,
    agent_role      VARCHAR(40) NOT NULL,
    subtask_type    VARCHAR(40),
    depends_on      TEXT,
    inputs          TEXT,
    outputs         TEXT,
    allowed_paths   TEXT,
    acceptance      TEXT,
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_dev_subtask_task ON dev_task_subtask (task_id);

CREATE TABLE dev_execution_event (
    id               BIGSERIAL PRIMARY KEY,
    task_id          VARCHAR(80) NOT NULL,
    event            VARCHAR(40) NOT NULL,
    from_state       VARCHAR(40) NOT NULL,
    to_state         VARCHAR(40) NOT NULL,
    actor            VARCHAR(120),
    detail           TEXT,
    idempotency_key  VARCHAR(120),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_execution_event_task ON dev_execution_event (task_id, created_at);

CREATE TABLE dev_workspace (
    id            VARCHAR(120) PRIMARY KEY,
    task_id       VARCHAR(80) NOT NULL,
    project_id    VARCHAR(120) NOT NULL,
    relative_path VARCHAR(500) NOT NULL,
    branch        VARCHAR(200),
    base_ref      VARCHAR(300),
    head_commit   VARCHAR(100),
    status        VARCHAR(30) NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_dev_workspace_task ON dev_workspace (task_id);

CREATE TABLE dev_code_change (
    id           BIGSERIAL PRIMARY KEY,
    task_id      VARCHAR(80) NOT NULL,
    workspace_id VARCHAR(120),
    branch       VARCHAR(200),
    commit_sha   VARCHAR(100),
    base_ref     VARCHAR(300),
    files_json   TEXT,
    insertions   INTEGER NOT NULL DEFAULT 0,
    deletions    INTEGER NOT NULL DEFAULT 0,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_dev_code_change_task ON dev_code_change (task_id);

CREATE TABLE dev_test_result (
    id           BIGSERIAL PRIMARY KEY,
    task_id      VARCHAR(80) NOT NULL,
    round_no     INTEGER NOT NULL,
    passed       BOOLEAN NOT NULL,
    exit_code    INTEGER,
    report_ref   VARCHAR(500),
    summary      TEXT,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_dev_test_result_task ON dev_test_result (task_id, round_no);

CREATE TABLE dev_review (
    id             BIGSERIAL PRIMARY KEY,
    task_id        VARCHAR(80) NOT NULL,
    round_no       INTEGER NOT NULL,
    passed         BOOLEAN NOT NULL,
    blocking_items TEXT,
    warning_items  TEXT,
    risk_items     TEXT,
    summary        TEXT,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_dev_review_task ON dev_review (task_id, round_no);

CREATE TABLE dev_approval (
    id               BIGSERIAL PRIMARY KEY,
    task_id          VARCHAR(80) NOT NULL,
    approval_type    VARCHAR(40) NOT NULL,
    decision         VARCHAR(40) NOT NULL,
    approver         VARCHAR(120),
    approval_comment VARCHAR(1000),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_dev_approval_task ON dev_approval (task_id);

CREATE TABLE dev_audit_log (
    id          BIGSERIAL PRIMARY KEY,
    actor       VARCHAR(128),
    action      VARCHAR(128) NOT NULL,
    object_type VARCHAR(64),
    object_id   VARCHAR(128),
    detail      TEXT,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_dev_audit_object ON dev_audit_log (object_type, object_id);
