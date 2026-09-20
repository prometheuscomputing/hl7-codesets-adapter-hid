# docker compose -f docker-compose.yml up --build  -d
COMPOSE_PROFILES=prod docker compose -f docker-compose.yml up -d --pull always