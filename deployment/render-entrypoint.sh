#!/bin/sh
set -eu

# Render supplies PORT for the public web process. The Spring API stays private
# inside this container and Nginx proxies /api to it.
WEB_PORT="${PORT:-10000}"
export WEB_PORT

PORT=8080 java -jar /app/app.jar &

envsubst '${WEB_PORT}' < /app/nginx.conf.template > /etc/nginx/conf.d/default.conf
exec nginx -g 'daemon off;'
