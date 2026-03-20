FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# セキュリティ: root以外のユーザーで実行
RUN addgroup -S wms && adduser -S wms -G wms
USER wms

COPY build/libs/wms-backend-*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
