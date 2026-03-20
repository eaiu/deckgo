alter table workflow_sessions add column if not exists background_json jsonb null;
alter table workflow_sessions add column if not exists discovery_answers_json jsonb null;
alter table workflow_sessions add column if not exists page_research_json jsonb null;

alter table workflow_versions add column if not exists background_json jsonb null;
alter table workflow_versions add column if not exists page_research_json jsonb null;

update workflow_sessions set current_stage = 'PLANNING' where current_stage = 'DRAFT';
update workflow_sessions set current_stage = 'DESIGN' where current_stage = 'FINAL';
update workflow_messages set stage = 'PLANNING' where stage = 'DRAFT';
update workflow_messages set stage = 'DESIGN' where stage = 'FINAL';
update workflow_versions set source = 'PLANNING' where source = 'DRAFT';
update workflow_versions set source = 'DESIGN' where source = 'FINAL';
