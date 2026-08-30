FROM eclipse-temurin:21-jre
RUN mkdir -p /usr/local/codeset-adapter
COPY codeset-adapter.jar /usr/local/codeset-adapter
CMD ["java", "-Xmx3g", "-Xss256k", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/usr/local/codeset-adapter/codeset-adapter.jar"]