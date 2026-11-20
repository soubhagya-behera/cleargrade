CREATE TABLE import_batch (
    id BIGSERIAL PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    duration_ms BIGINT,
    rows_read INTEGER,
    imported_count INTEGER,
    new_count INTEGER,
    changed_count INTEGER,
    unchanged_count INTEGER,
    rejected_count INTEGER,
    error_count INTEGER,
    CONSTRAINT ck_import_batch_filename CHECK (btrim(filename) <> ''),
    CONSTRAINT ck_import_batch_file_size_bytes CHECK (file_size_bytes >= 0),
    CONSTRAINT ck_import_batch_sha256 CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_import_batch_status CHECK (status IN ('RECEIVED', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_import_batch_duration_ms CHECK (duration_ms IS NULL OR duration_ms >= 0),
    CONSTRAINT ck_import_batch_rows_read_nonnegative CHECK (rows_read IS NULL OR rows_read >= 0),
    CONSTRAINT ck_import_batch_imported_nonnegative CHECK (imported_count IS NULL OR imported_count >= 0),
    CONSTRAINT ck_import_batch_new_nonnegative CHECK (new_count IS NULL OR new_count >= 0),
    CONSTRAINT ck_import_batch_changed_nonnegative CHECK (changed_count IS NULL OR changed_count >= 0),
    CONSTRAINT ck_import_batch_unchanged_nonnegative CHECK (unchanged_count IS NULL OR unchanged_count >= 0),
    CONSTRAINT ck_import_batch_rejected_nonnegative CHECK (rejected_count IS NULL OR rejected_count >= 0),
    CONSTRAINT ck_import_batch_error_nonnegative CHECK (error_count IS NULL OR error_count >= 0),
    CONSTRAINT ck_import_batch_imported_sum CHECK (
        imported_count IS NULL
        OR (new_count IS NOT NULL
            AND changed_count IS NOT NULL
            AND unchanged_count IS NOT NULL
            AND imported_count = new_count + changed_count + unchanged_count)
    ),
    CONSTRAINT ck_import_batch_rows_read_sum CHECK (
        rows_read IS NULL
        OR (imported_count IS NOT NULL
            AND rejected_count IS NOT NULL
            AND rows_read = imported_count + rejected_count)
    ),
    CONSTRAINT ck_import_batch_error_count CHECK (
        error_count IS NULL
        OR (rejected_count IS NOT NULL AND error_count >= rejected_count)
    ),
    CONSTRAINT ck_import_batch_completed_counts CHECK (
        status <> 'COMPLETED'
        OR (rows_read IS NOT NULL
            AND imported_count IS NOT NULL
            AND new_count IS NOT NULL
            AND changed_count IS NOT NULL
            AND unchanged_count IS NOT NULL
            AND rejected_count IS NOT NULL
            AND error_count IS NOT NULL)
    ),
    CONSTRAINT ck_import_batch_non_completed_no_counts CHECK (
        status = 'COMPLETED'
        OR (rows_read IS NULL
            AND imported_count IS NULL
            AND new_count IS NULL
            AND changed_count IS NULL
            AND unchanged_count IS NULL
            AND rejected_count IS NULL
            AND error_count IS NULL)
    )
);

CREATE TABLE student (
    id BIGSERIAL PRIMARY KEY,
    student_id VARCHAR(20) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(320) NOT NULL,
    date_of_birth DATE NOT NULL,
    grade_level SMALLINT NOT NULL,
    section CHAR(1) NOT NULL,
    maths NUMERIC NOT NULL,
    science NUMERIC NOT NULL,
    english NUMERIC NOT NULL,
    social_studies NUMERIC NOT NULL,
    second_language NUMERIC NOT NULL,
    first_seen_batch_id BIGINT NOT NULL,
    last_updated_batch_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_student_student_id UNIQUE (student_id),
    CONSTRAINT fk_student_first_seen_batch FOREIGN KEY (first_seen_batch_id) REFERENCES import_batch (id),
    CONSTRAINT fk_student_last_updated_batch FOREIGN KEY (last_updated_batch_id) REFERENCES import_batch (id),
    CONSTRAINT ck_student_student_id_format CHECK (student_id ~ '^[A-Z0-9-]{3,20}$'),
    CONSTRAINT ck_student_full_name CHECK (btrim(full_name) <> ''),
    CONSTRAINT ck_student_email CHECK (btrim(email) <> ''),
    CONSTRAINT ck_student_grade_level CHECK (grade_level BETWEEN 1 AND 12),
    CONSTRAINT ck_student_section CHECK (section IN ('A', 'B', 'C', 'D', 'E', 'F')),
    CONSTRAINT ck_student_maths CHECK (maths BETWEEN 0 AND 100),
    CONSTRAINT ck_student_science CHECK (science BETWEEN 0 AND 100),
    CONSTRAINT ck_student_english CHECK (english BETWEEN 0 AND 100),
    CONSTRAINT ck_student_social_studies CHECK (social_studies BETWEEN 0 AND 100),
    CONSTRAINT ck_student_second_language CHECK (second_language BETWEEN 0 AND 100)
);

CREATE INDEX ix_student_full_name_lower ON student (lower(full_name));
CREATE INDEX ix_student_grade_section ON student (grade_level, section);
CREATE INDEX ix_student_first_seen_batch ON student (first_seen_batch_id);
CREATE INDEX ix_student_last_updated_batch ON student (last_updated_batch_id);

CREATE TABLE import_row_error (
    id BIGSERIAL PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    line_number INTEGER NOT NULL,
    raw_student_id TEXT,
    field_name VARCHAR(32) NOT NULL,
    error_code VARCHAR(24) NOT NULL,
    message TEXT NOT NULL,
    CONSTRAINT fk_import_row_error_batch FOREIGN KEY (batch_id) REFERENCES import_batch (id),
    CONSTRAINT ck_import_row_error_line_number CHECK (line_number >= 2),
    CONSTRAINT ck_import_row_error_field_name CHECK (btrim(field_name) <> ''),
    CONSTRAINT ck_import_row_error_code CHECK (error_code IN (
        'MISSING_REQUIRED', 'INVALID_FORMAT', 'OUT_OF_RANGE', 'DUPLICATE_ID', 'WRONG_FIELD_COUNT')),
    CONSTRAINT ck_import_row_error_message CHECK (btrim(message) <> '')
);

CREATE INDEX ix_import_row_error_batch_line ON import_row_error (batch_id, line_number);
CREATE INDEX ix_import_row_error_batch_code ON import_row_error (batch_id, error_code);

CREATE UNIQUE INDEX ux_import_batch_active_sha256 ON import_batch (sha256)
    WHERE status IN ('RECEIVED', 'PROCESSING', 'COMPLETED');
