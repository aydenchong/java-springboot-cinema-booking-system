-- V5: Add authentication and authorization fields

ALTER TABLE app_user
    ADD COLUMN password_hash VARCHAR(255);

ALTER TABLE app_user
    ADD COLUMN role VARCHAR(20)
        NOT NULL DEFAULT 'CUSTOMER';

ALTER TABLE app_user
    ADD CONSTRAINT chk_app_user_role
        CHECK (role IN ('CUSTOMER', 'ADMIN'));

ALTER TABLE app_user
    ADD CONSTRAINT chk_app_user_password_hash
        CHECK (
            password_hash IS NULL
                OR length(trim(password_hash)) > 0
            );
