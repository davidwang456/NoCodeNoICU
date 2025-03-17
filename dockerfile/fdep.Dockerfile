FROM 119.91.121.198:38888/ifp/fdep:latest

RUN chown -R 100:100 /app/FxClient_k0029/FxClient_k0029

USER 100

CMD ["./fxclient"]
