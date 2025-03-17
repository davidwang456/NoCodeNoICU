FROM 119.91.121.198:38888/library/openjdk:8-alpine-amobase@sha256:d0d912b7bcddeb5c2614d2d593691e5c323fb26c054302806c5946747e1a0da5

# 安装依赖
RUN apk add --no-cache iputils ncurses vim libcurl bash

# 设置环境变量
ENV MODE="cluster" \
    PREFER_HOST_MODE="ip"\
    BASE_DIR="/home/nacos" \
    CLASSPATH=".:/home/nacos/conf:$CLASSPATH" \
    CLUSTER_CONF="/home/nacos/conf/cluster.conf" \
    FUNCTION_MODE="all" \
    JAVA_HOME="/usr/lib/jvm/java-1.8-openjdk" \
    NACOS_USER="nacos" \
    JAVA="/usr/lib/jvm/java-1.8-openjdk/bin/java" \
    JVM_XMS="1g" \
    JVM_XMX="1g" \
    JVM_XMN="512m" \
    JVM_MS="128m" \
    JVM_MMS="320m" \
    NACOS_DEBUG="n" \
    TOMCAT_ACCESSLOG_ENABLED="false" \
    TIME_ZONE="Asia/Shanghai"

ARG NACOS_VERSION=2.2.3
ARG HOT_FIX_FLAG=""

WORKDIR $BASE_DIR

ADD artifacts/nacos-server-2.2.3.zip /home/nacos

# 下载并安装 Nacos
RUN set -x \
    && unzip nacos-server-2.2.3.zip -d /home \
    && rm -rf nacos-server-2.2.3.zip /home/nacos/bin/* /home/nacos/conf/*.properties /home/nacos/conf/*.example /home/nacos/conf/nacos-mysql.sql \
    && ln -snf /usr/share/zoneinfo/$TIME_ZONE /etc/localtime && echo $TIME_ZONE > /etc/timezone

ADD conf/nacos/docker-startup.sh bin/docker-startup.sh
ADD conf/nacos/application.properties conf/application.properties

# 设置启动日志目录
RUN mkdir -p logs \
	&& touch logs/start.out \
	&& ln -sf /dev/stdout logs/start.out \
	&& ln -sf /dev/stderr logs/start.out \
    && chmod +x bin/docker-startup.sh \
    && chown -R 100:100 /home/nacos

USER 100

EXPOSE 8848
CMD ["sh","bin/docker-startup.sh"]