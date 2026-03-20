create table if not exists projects (
    id uuid primary key,
    title varchar(255) not null,
    topic varchar(500) not null,
    audience varchar(255) not null,
    template_id varchar(100) not null,
    current_version_id uuid null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table if not exists deck_versions (
    id uuid primary key,
    project_id uuid not null references projects(id) on delete cascade,
    version_number integer not null,
    source varchar(50) not null,
    note varchar(1000) null,
    template_id varchar(100) not null,
    spec_title varchar(255) not null,
    slide_count integer not null,
    spec_json jsonb not null,
    created_at timestamptz not null,
    constraint uk_deck_versions_project_version unique (project_id, version_number)
);

create table if not exists artifacts (
    id uuid primary key,
    project_id uuid not null references projects(id) on delete cascade,
    deck_version_id uuid not null references deck_versions(id) on delete cascade,
    filename varchar(255) not null,
    media_type varchar(100) not null,
    storage_path varchar(1000) not null,
    size_bytes bigint null,
    created_at timestamptz not null
);

create table if not exists render_jobs (
    id uuid primary key,
    project_id uuid not null references projects(id) on delete cascade,
    deck_version_id uuid not null references deck_versions(id) on delete cascade,
    format varchar(50) not null,
    status varchar(50) not null,
    artifact_id uuid null references artifacts(id) on delete set null,
    error_message varchar(2000) null,
    created_at timestamptz not null,
    started_at timestamptz null,
    completed_at timestamptz null
);

create index if not exists idx_deck_versions_project_id on deck_versions(project_id);
create index if not exists idx_render_jobs_status on render_jobs(status);
create index if not exists idx_artifacts_project_id on artifacts(project_id);
