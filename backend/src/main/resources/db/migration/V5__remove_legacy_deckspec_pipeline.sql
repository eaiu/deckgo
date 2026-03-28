alter table if exists projects
    drop column if exists current_version_id;

drop table if exists render_jobs;
drop table if exists artifacts;
drop table if exists deck_versions;
