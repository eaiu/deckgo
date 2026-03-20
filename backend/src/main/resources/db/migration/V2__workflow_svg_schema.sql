create table if not exists workflow_sessions (
    id uuid primary key,
    project_id uuid not null references projects(id) on delete cascade,
    current_version_id uuid null,
    status varchar(50) not null,
    current_stage varchar(50) not null,
    selected_template_id varchar(100) not null,
    discovery_json jsonb null,
    research_json jsonb null,
    outline_json jsonb null,
    last_error varchar(2000) null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table if not exists workflow_messages (
    id uuid primary key,
    session_id uuid not null references workflow_sessions(id) on delete cascade,
    role varchar(50) not null,
    stage varchar(50) not null,
    content_json jsonb not null,
    created_at timestamptz not null
);

create table if not exists workflow_versions (
    id uuid primary key,
    project_id uuid not null references projects(id) on delete cascade,
    version_number integer not null,
    source varchar(50) not null,
    note varchar(1000) null,
    template_id varchar(100) not null,
    research_json jsonb null,
    outline_json jsonb null,
    created_at timestamptz not null,
    constraint uk_workflow_versions_project_version unique (project_id, version_number)
);

create table if not exists workflow_pages (
    id uuid primary key,
    workflow_version_id uuid not null references workflow_versions(id) on delete cascade,
    order_index integer not null,
    title varchar(255) not null,
    page_plan_json jsonb not null,
    draft_svg text null,
    final_svg text null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index if not exists idx_workflow_sessions_project_id on workflow_sessions(project_id);
create index if not exists idx_workflow_messages_session_id on workflow_messages(session_id);
create index if not exists idx_workflow_versions_project_id on workflow_versions(project_id);
create index if not exists idx_workflow_pages_workflow_version_id on workflow_pages(workflow_version_id);
