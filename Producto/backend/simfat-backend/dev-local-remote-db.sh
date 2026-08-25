#!/usr/bin/env bash
# Run simfat-backend locally connected to the remote production databases.
# Prerequisites: Java 17+, Maven (or use ./mvnw)
#
# Usage (from Producto/backend/simfat-backend/):
#   bash dev-local-remote-db.sh
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env.local"

if [ ! -f "$ENV_FILE" ]; then
  echo "ERROR: .env.local not found at $ENV_FILE"
  echo "Copy it from the team password manager or ask David/Andrés."
  exit 1
fi

while IFS= read -r line || [[ -n "$line" ]]; do
  line="${line%$'\r'}"
  [[ "$line" =~ ^[[:space:]]*# ]] && continue
  [[ -z "${line// }" ]] && continue
  [[ "$line" == *=* ]] || continue
  key="${line%%=*}"
  value="${line#*=}"
  # Strip surrounding quotes (both single and double)
  if [[ "$value" == '"'*'"' ]] || [[ "$value" == "'"*"'" ]]; then
    value="${value:1:${#value}-2}"
  fi
  export "$key=$value"
done < "$ENV_FILE"

echo "Backend arrancando en http://localhost:8080"
echo "Conectado a: Supabase (PostgreSQL) + MongoDB Atlas"
echo ""

JAVA_TOOL_OPTIONS="-Duser.timezone=UTC" mvn spring-boot:run -f "$SCRIPT_DIR/pom.xml"
