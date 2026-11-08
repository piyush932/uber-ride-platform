# Builds the React app and serves it with non-root nginx. /api is proxied to the gateway (see nginx.conf).
FROM node:20-alpine AS build
WORKDIR /app
# package*.json also picks up package-lock.json once it is committed; then switch to npm ci.
COPY package*.json ./
RUN npm install --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:stable-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
