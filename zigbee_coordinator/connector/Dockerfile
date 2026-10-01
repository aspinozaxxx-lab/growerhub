FROM eclipse-mosquitto:2.0.22 AS broker
FROM node:24-alpine@sha256:ebfe2f90462722a7a4de65e91990e97fe0d401c70e0e762c5b53302f905ec1c1 AS runtime
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev --ignore-scripts && npm cache clean --force
COPY connector.mjs ./
USER node
CMD ["node", "connector.mjs"]

FROM runtime AS test
USER root
COPY --from=broker /usr/sbin/mosquitto /usr/sbin/mosquitto
COPY --from=broker /usr/bin/mosquitto_passwd /usr/bin/mosquitto_passwd
COPY --from=broker /usr/lib/libwebsockets.so.19 /usr/lib/libwebsockets.so.19
RUN apk add --no-cache openssl
COPY connector.test.mjs connector.integration.mjs ./
CMD ["sh", "-c", "npm test && node connector.integration.mjs"]

FROM runtime AS final
