#!/bin/bash
# The app account can only SELECT on yongin (decisions D15). Passwords come from .env
set -euo pipefail

mariadb --protocol=socket -uroot -p"${MARIADB_ROOT_PASSWORD}" <<SQL
CREATE USER IF NOT EXISTS '${DB_USERNAME}'@'%' IDENTIFIED BY '${DB_PASSWORD}';
GRANT SELECT ON yongin.* TO '${DB_USERNAME}'@'%';
SQL
