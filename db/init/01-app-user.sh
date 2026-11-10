#!/bin/bash
# The app account can only SELECT on yongin (decisions D15). Passwords come from .env
set -euo pipefail

mariadb --protocol=socket -uroot -p"${MARIADB_ROOT_PASSWORD}" <<SQL
CREATE USER IF NOT EXISTS '${DB_USERNAME}'@'%' IDENTIFIED BY '${DB_PASSWORD}';
GRANT SELECT ON yongin.* TO '${DB_USERNAME}'@'%';
SQL

# ML (yong_ml) account: reads yongin, writes only the 5 ml_ tables (decisions D15, D17).
# DELETE+INSERT per period; CREATE/DROP/ALTER because it recreates a table or adds a column when the shape changes
if [ -n "${ML_DB_USERNAME:-}" ]; then
  mariadb --protocol=socket -uroot -p"${MARIADB_ROOT_PASSWORD}" <<SQL
CREATE USER IF NOT EXISTS '${ML_DB_USERNAME}'@'%' IDENTIFIED BY '${ML_DB_PASSWORD}';
GRANT SELECT ON yongin.* TO '${ML_DB_USERNAME}'@'%';
GRANT INSERT, DELETE, CREATE, DROP, ALTER ON yongin.ml_priority TO '${ML_DB_USERNAME}'@'%';
GRANT INSERT, DELETE, CREATE, DROP, ALTER ON yongin.ml_suitability TO '${ML_DB_USERNAME}'@'%';
GRANT INSERT, DELETE, CREATE, DROP, ALTER ON yongin.ml_region TO '${ML_DB_USERNAME}'@'%';
GRANT INSERT, DELETE, CREATE, DROP, ALTER ON yongin.ml_explanation TO '${ML_DB_USERNAME}'@'%';
GRANT INSERT, DELETE, CREATE, DROP, ALTER ON yongin.ml_model_info TO '${ML_DB_USERNAME}'@'%';
SQL
fi
