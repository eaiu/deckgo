alter table projects
    add column if not exists request_text text null,
    add column if not exists current_stage varchar(50) null,
    add column if not exists current_outline_version_id uuid null,
    add column if not exists page_count_target integer null,
    add column if not exists style_preset varchar(100) null,
    add column if not exists background_asset_path varchar(1000) null,
    add column if not exists workflow_constraints_json jsonb null;

create table if not exists requirement_forms (
    id uuid primary key,
    project_id uuid not null unique references projects(id) on delete cascade,
    status varchar(50) not null,
    based_on_outline_version_id uuid null,
    summary_md text null,
    outline_context_md text null,
    fixed_items_json jsonb null,
    init_search_queries_json jsonb null,
    init_search_results_json jsonb null,
    init_corpus_digest_json jsonb null,
    ai_questions_json jsonb null,
    answers_json jsonb null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table if not exists project_messages (
    id uuid primary key,
    project_id uuid not null references projects(id) on delete cascade,
    stage varchar(50) not null,
    scope_type varchar(50) not null,
    target_page_id uuid null,
    role varchar(50) not null,
    content_md text not null,
    structured_payload_json jsonb null,
    created_at timestamptz not null
);

create table if not exists project_events (
    stream_id bigserial primary key,
    event_id uuid not null unique,
    project_id uuid not null references projects(id) on delete cascade,
    event_type varchar(100) not null,
    stage varchar(50) not null,
    scope_type varchar(50) not null,
    target_page_id uuid null,
    agent_run_id uuid null,
    payload_json jsonb null,
    created_at timestamptz not null
);

create table if not exists outline_versions (
    id uuid primary key,
    project_id uuid not null references projects(id) on delete cascade,
    version_no integer not null,
    status varchar(50) not null,
    parent_version_id uuid null,
    outline_json jsonb not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uk_outline_versions_project_version unique (project_id, version_no)
);

create table if not exists project_pages (
    id uuid primary key,
    project_id uuid not null references projects(id) on delete cascade,
    page_code varchar(100) not null,
    page_role varchar(100) null,
    part_title varchar(255) null,
    sort_order integer not null,
    current_brief_version_id uuid null,
    current_research_session_id uuid null,
    current_draft_version_id uuid null,
    current_design_version_id uuid null,
    outline_status varchar(50) not null,
    search_status varchar(50) not null,
    summary_status varchar(50) not null,
    draft_status varchar(50) not null,
    design_status varchar(50) not null,
    artifact_staleness_json jsonb null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uk_project_pages_project_code unique (project_id, page_code)
);

create table if not exists page_brief_versions (
    id uuid primary key,
    project_id uuid not null references projects(id) on delete cascade,
    page_id uuid not null references project_pages(id) on delete cascade,
    version_no integer not null,
    status varchar(50) not null,
    parent_version_id uuid null,
    section_title varchar(255) null,
    title varchar(255) not null,
    content_outline_json jsonb not null,
    content_summary text null,
    self_check_result_json jsonb null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uk_page_brief_versions_page_version unique (page_id, version_no)
);

create table if not exists draft_versions (
    id uuid primary key,
    project_id uuid not null references projects(id) on delete cascade,
    page_id uuid not null references project_pages(id) on delete cascade,
    version_no integer not null,
    status varchar(50) not null,
    page_brief_version_id uuid null references page_brief_versions(id) on delete set null,
    research_session_id uuid null,
    draft_svg_markup text null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uk_draft_versions_page_version unique (page_id, version_no)
);

create table if not exists design_versions (
    id uuid primary key,
    project_id uuid not null references projects(id) on delete cascade,
    page_id uuid not null references project_pages(id) on delete cascade,
    version_no integer not null,
    status varchar(50) not null,
    draft_version_id uuid null references draft_versions(id) on delete set null,
    style_pack_id varchar(100) null,
    background_asset_path varchar(1000) null,
    design_svg_markup text null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uk_design_versions_page_version unique (page_id, version_no)
);

create table if not exists project_stage_runs (
    id uuid primary key,
    project_id uuid not null references projects(id) on delete cascade,
    stage varchar(50) not null,
    attempt_no integer not null,
    status varchar(50) not null,
    input_refs_json jsonb null,
    output_ref_json jsonb null,
    error_message text null,
    started_at timestamptz not null,
    finished_at timestamptz null,
    constraint uk_project_stage_runs_attempt unique (project_id, stage, attempt_no)
);

create table if not exists page_stage_runs (
    id uuid primary key,
    project_id uuid not null references projects(id) on delete cascade,
    page_id uuid not null references project_pages(id) on delete cascade,
    stage varchar(50) not null,
    attempt_no integer not null,
    status varchar(50) not null,
    input_refs_json jsonb null,
    output_ref_json jsonb null,
    error_message text null,
    started_at timestamptz not null,
    finished_at timestamptz null,
    constraint uk_page_stage_runs_attempt unique (page_id, stage, attempt_no)
);

create table if not exists export_jobs (
    id uuid primary key,
    project_id uuid not null references projects(id) on delete cascade,
    export_format varchar(50) not null,
    status varchar(50) not null,
    file_path text null,
    resolved_manifest_json jsonb null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table if not exists bocha_search_cache (
    id uuid primary key,
    query_key varchar(200) not null unique,
    query_text text not null,
    provider varchar(50) not null,
    result_json jsonb null,
    result_count integer not null,
    expires_at timestamptz null,
    created_at timestamptz not null
);

create table if not exists url_content_cache (
    id uuid primary key,
    normalized_url varchar(1000) not null unique,
    provider varchar(50) not null,
    title varchar(500) null,
    markdown_content text null,
    metadata_json jsonb null,
    content_hash varchar(128) null,
    status varchar(50) not null,
    expires_at timestamptz null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table if not exists source_collections (
    id uuid primary key,
    project_id uuid not null references projects(id) on delete cascade,
    page_id uuid null references project_pages(id) on delete cascade,
    collection_type varchar(50) not null,
    title varchar(255) not null,
    created_at timestamptz not null
);

create table if not exists source_documents (
    id uuid primary key,
    collection_id uuid not null references source_collections(id) on delete cascade,
    source_type varchar(50) not null,
    source_uri varchar(1000) not null,
    url_cache_id uuid null references url_content_cache(id) on delete set null,
    title varchar(500) null,
    markdown_content text null,
    metadata_json jsonb null,
    content_hash varchar(128) null,
    status varchar(50) not null,
    created_at timestamptz not null
);

create extension if not exists vector;

create table if not exists source_chunks (
    id uuid primary key,
    source_document_id uuid not null references source_documents(id) on delete cascade,
    chunk_index integer not null,
    section_path varchar(500) null,
    content_md text not null,
    content_for_embedding text not null,
    embedding vector null,
    token_count integer not null,
    created_at timestamptz not null,
    constraint uk_source_chunks_doc_index unique (source_document_id, chunk_index)
);

create table if not exists research_sessions (
    id uuid primary key,
    project_id uuid not null references projects(id) on delete cascade,
    page_id uuid null references project_pages(id) on delete cascade,
    scope_type varchar(50) not null,
    session_role varchar(50) not null,
    page_brief_version_id uuid null,
    based_on_session_id uuid null,
    research_goal text null,
    query_plan_json jsonb null,
    summary_md text null,
    key_findings_json jsonb null,
    overlap_risks_json jsonb null,
    open_questions_json jsonb null,
    status varchar(50) not null,
    confirmed_by_message_id uuid null,
    context_snapshot_json jsonb null,
    candidate_sources_json jsonb null,
    selected_citations_json jsonb null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table if not exists research_sources (
    id uuid primary key,
    research_session_id uuid not null references research_sessions(id) on delete cascade,
    title varchar(500) null,
    url varchar(1000) null,
    snippet text null,
    provider_rank integer null,
    raw_payload_json jsonb null,
    created_at timestamptz not null
);

create table if not exists research_session_sources (
    id uuid primary key,
    research_session_id uuid not null references research_sessions(id) on delete cascade,
    source_document_id uuid null references source_documents(id) on delete set null,
    chunk_id uuid null references source_chunks(id) on delete set null,
    rank_no integer not null,
    excerpt_md text null,
    relevance_score double precision null,
    usage_note varchar(500) null,
    is_pinned boolean not null default false,
    constraint uk_research_session_sources_session_chunk unique (research_session_id, chunk_id)
);

create table if not exists retrieval_runs (
    id uuid primary key,
    project_id uuid not null references projects(id) on delete cascade,
    research_session_id uuid null references research_sessions(id) on delete cascade,
    query_text text not null,
    retrieval_mode varchar(50) not null,
    status varchar(50) not null,
    created_at timestamptz not null
);

create table if not exists retrieval_candidates (
    id uuid primary key,
    retrieval_run_id uuid not null references retrieval_runs(id) on delete cascade,
    source_document_id uuid null references source_documents(id) on delete set null,
    chunk_id uuid null references source_chunks(id) on delete set null,
    score_vector double precision null,
    score_keyword double precision null,
    score_final double precision null,
    selected boolean not null default false
);

create table if not exists citations (
    id uuid primary key,
    project_id uuid not null references projects(id) on delete cascade,
    page_id uuid null references project_pages(id) on delete cascade,
    research_session_id uuid null references research_sessions(id) on delete cascade,
    source_document_id uuid null references source_documents(id) on delete set null,
    chunk_id uuid null references source_chunks(id) on delete set null,
    title varchar(500) null,
    url varchar(1000) null,
    excerpt_md text null,
    citation_label varchar(100) not null,
    created_at timestamptz not null,
    constraint uk_citations_page_chunk_label unique (project_id, page_id, chunk_id, citation_label)
);

create index if not exists idx_requirement_forms_project_id on requirement_forms(project_id);
create index if not exists idx_project_messages_project_created on project_messages(project_id, created_at);
create index if not exists idx_project_events_project_created on project_events(project_id, created_at);
create index if not exists idx_outline_versions_project_id on outline_versions(project_id);
create index if not exists idx_project_pages_project_id on project_pages(project_id);
create index if not exists idx_page_brief_versions_page_id on page_brief_versions(page_id);
create index if not exists idx_draft_versions_page_id on draft_versions(page_id);
create index if not exists idx_design_versions_page_id on design_versions(page_id);
create index if not exists idx_project_stage_runs_project_stage on project_stage_runs(project_id, stage);
create index if not exists idx_page_stage_runs_page_stage on page_stage_runs(page_id, stage);
create index if not exists idx_source_collections_project_id on source_collections(project_id);
create index if not exists idx_source_documents_collection_id on source_documents(collection_id);
create index if not exists idx_research_sessions_project_id on research_sessions(project_id);
create index if not exists idx_research_sessions_page_id on research_sessions(page_id);
create index if not exists idx_research_sources_session_id on research_sources(research_session_id);
create index if not exists idx_research_session_sources_session_id on research_session_sources(research_session_id);
create index if not exists idx_retrieval_runs_project_id on retrieval_runs(project_id);
create index if not exists idx_citations_project_id on citations(project_id);
