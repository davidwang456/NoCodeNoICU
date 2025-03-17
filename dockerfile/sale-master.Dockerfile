FROM 119.91.121.198:38888/library/openjdk:8-alpine-amobase@sha256:d0d912b7bcddeb5c2614d2d593691e5c323fb26c054302806c5946747e1a0da5

ADD artifacts/sale-master-bootapp /opt/app

RUN chown -R 100:100 /opt/app
USER 100
WORKDIR /opt/app

EXPOSE 8080
CMD java \
    -server \
    -Dserver.port=8080 \
    -Duser.timezone=Asia/Shanghai \
    -XX:+UseContainerSupport \
    -XX:InitialRAMPercentage=80.0 \
    -XX:MinRAMPercentage=80.0 \
    -XX:MaxRAMPercentage=80.0 \
    -XX:-UseAdaptiveSizePolicy \
    -Duser.timezone=GMT+08 \
    -Dlogging.config=/opt/app/conf/logback-spring.xml \
    -Dspring.config.location=/opt/app/conf/ \
    -jar \
    /opt/app/lib/sale-master-bootapp-4.0.0-RELEASE.jar