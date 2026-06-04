#!/bin/bash
set -a
source /usr/local/service/app/.env.prod
set +a
exec java -XX:+UseZGC -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom -jar /usr/local/service/app/app.jar --spring.profiles.active=prod
