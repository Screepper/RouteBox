FROM nginx:latest

WORKDIR /app

COPY nginx.conf /etc/nginx/nginx.conf

COPY dist/ /app

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]