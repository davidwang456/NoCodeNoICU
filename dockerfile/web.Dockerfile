FROM 119.91.121.198:38888/library/nginx:1.25.2-alpine
WORKDIR /opt/nginx

ADD artifacts/RunWebUi.tar.gz /opt/nginx
COPY conf/nginx.conf /etc/nginx/nginx.conf
COPY conf/nginx.conf.template /etc/nginx/templates/default.conf.template

RUN mkdir -p /var/cache/nginx/client_temp && \
    mkdir -p /var/run/nginx && \
    chown -R 100:100 /var/run/nginx && \
    chown -R 100:100 /var/cache/nginx && \
    chown -R 100:100 /etc/nginx/conf.d && \
    rm -rf /etc/nginx/conf.d/default.conf

ENV GW_SERVER=http://gateway:8080

USER 100

CMD ["nginx", "-g", "daemon off;"]
