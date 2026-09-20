FROM eclipse-temurin:21-jre
RUN mkdir -p /usr/local/codeset-adapter
COPY codeset-adapter.jar /usr/local/codeset-adapter
# JAVA_OPTS lets the task definition size the heap without a rebuild; the
# default matches what the image always ran with.
CMD ["sh", "-c", "exec java ${JAVA_OPTS:--Xmx3g} -Xss256k -Djava.security.egd=file:/dev/./urandom -jar /usr/local/codeset-adapter/codeset-adapter.jar"]
