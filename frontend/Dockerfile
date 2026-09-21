FROM node:18-alpine AS builder

WORKDIR /app

RUN npm install -g pnpm

COPY package.json pnpm-workspace.yaml pnpm-lock.yaml ./
COPY packages/ ./packages/
COPY apps/pc-web/ ./apps/pc-web/

RUN pnpm install --frozen-lockfile
RUN pnpm --filter @dataviz/pc-web build

FROM nginx:alpine

COPY --from=builder /app/apps/pc-web/dist /usr/share/nginx/html
COPY deploy/nginx/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
