FROM 119.91.121.198:38888/library/nacos/nacos-peer-finder-plugin:1.1

USER 100

ENTRYPOINT [ "/install.sh" ]