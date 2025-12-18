# ====== growerhub.ru / www.growerhub.ru ======
server {
    listen 80;
    server_name growerhub.ru www.growerhub.ru;

    # СТАТИКА сайта (новый сайт из проекта)
    root /home/watering-admin/growerhub/static;
    index index.html;
    location = /docs        { proxy_pass http://127.0.0.1:8000; proxy_set_header Host $host; proxy_set_header X-Real-IP $remote_addr; proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for; proxy_set_header X-Forwarded-Proto $scheme; }
    location = /redoc       { proxy_pass http://127.0.0.1:8000; proxy_set_header Host $host; proxy_set_header X-Real-IP $remote_addr; proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for; proxy_set_header X-Forwarded-Proto $scheme; }
    location = /openapi.json{ proxy_pass http://127.0.0.1:8000; proxy_set_header Host $host; proxy_set_header X-Real-IP $remote_addr; proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for; proxy_set_header X-Forwarded-Proto $scheme; }

    # SPA/статические страницы
    location / {
        try_files $uri /index.html;
    }

    # Явный мэппинг статики (быстрее, чем через бэкенд)
    location /static/ {
        alias /home/watering-admin/growerhub/static/;
        try_files $uri $uri/ =404;
        expires 1h;
        add_header Cache-Control "public";
    }

    # API -> FastAPI (uvicorn на 127.0.0.1:8000)
    location /api/ {
        # CORS (минимально; при надобности сузим домены)
        add_header Access-Control-Allow-Origin * always;
        add_header Access-Control-Allow-Methods GET,POST,PUT,DELETE,OPTIONS always;
        add_header Access-Control-Allow-Headers Authorization,Content-Type always;
        if ($request_method = OPTIONS) { return 204; }

        proxy_pass http://127.0.0.1:8000;   # ВАЖНО: со слэшем
        proxy_http_version 1.1;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 5s;
        proxy_read_timeout 60s;
        proxy_redirect off;
    }

    # /firmware тоже отдаём через FastAPI
    location /firmware/ {
        proxy_pass http://127.0.0.1:8000;   # ВАЖНО: со слэшем
        proxy_http_version 1.1;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 5s;
        proxy_read_timeout 60s;
        proxy_redirect off;
    }

    # Health напрямую к бэкенду (удобно для мониторинга)
    location = /health {
        proxy_pass http://127.0.0.1:18080/health;
    }
}

# ====== доступ по IP в ЛС (192.168.0.11) ======
server {
    listen 80;
    server_name 192.168.0.11;

    root /home/watering-admin/growerhub/static;
    index index.html;

    location = /docs        { proxy_pass http://127.0.0.1:8000; proxy_set_header Host $host; proxy_set_header X-Real-IP $remote_addr; proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for; proxy_set_header X-Forwarded-Proto $scheme; }
    location = /redoc       { proxy_pass http://127.0.0.1:8000; proxy_set_header Host $host; proxy_set_header X-Real-IP $remote_addr; proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for; proxy_set_header X-Forwarded-Proto $scheme; }
    location = /openapi.json{ proxy_pass http://127.0.0.1:8000; proxy_set_header Host $host; proxy_set_header X-Real-IP $remote_addr; proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for; proxy_set_header X-Forwarded-Proto $scheme; }

    location / {
        try_files $uri /index.html;
    }

    location /static/ {
        alias /home/watering-admin/growerhub/static/;
        try_files $uri $uri/ =404;
        expires 1h;
        add_header Cache-Control "public";
    }

    location /api/ {
        add_header Access-Control-Allow-Origin * always;
        add_header Access-Control-Allow-Methods GET,POST,PUT,DELETE,OPTIONS always;
        add_header Access-Control-Allow-Headers Authorization,Content-Type always;
        if ($request_method = OPTIONS) { return 204; }

        proxy_pass http://127.0.0.1:8000;
        proxy_http_version 1.1;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 5s;
        proxy_read_timeout 60s;
        proxy_redirect off;
    }

    location /firmware/ {
        proxy_pass http://127.0.0.1:8000;
        proxy_http_version 1.1;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 5s;
        proxy_read_timeout 60s;
        proxy_redirect off;
    }

    location = /health {
        proxy_pass http://127.0.0.1:18080/health;
    }
}

