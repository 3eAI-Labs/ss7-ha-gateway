FROM eclipse-temurin:11-jre-alpine

# Install SCTP support
RUN apk add --no-cache lksctp-tools

WORKDIR /app

# Copy the JAR file
COPY ss7-core/target/ss7-core-1.0.0-SNAPSHOT.jar /app/ss7-ha-gateway.jar

# Create non-root user
RUN addgroup -g 1000 ss7 && \
    adduser -D -u 1000 -G ss7 ss7 && \
    chown -R ss7:ss7 /app

USER ss7

# Environment variables
ENV JAVA_OPTS="-Xms512m -Xmx1024m"
ENV LOCAL_PC="1-1-1"
ENV LOCAL_SSN="8"
ENV REMOTE_PC="2-2-2"
ENV REMOTE_SSN="8"
ENV SCTP_LOCAL_PORT="2905"
ENV SCTP_REMOTE_HOST="ss7-ha-gw-2.ss7.svc.cluster.local"
ENV SCTP_REMOTE_PORT="2905"
ENV M3UA_ROUTING_CONTEXT="100"

EXPOSE 2905

CMD java $JAVA_OPTS \
    -Dlocal.pc=$LOCAL_PC \
    -Dlocal.ssn=$LOCAL_SSN \
    -Dremote.pc=$REMOTE_PC \
    -Dremote.ssn=$REMOTE_SSN \
    -Dsctp.local.port=$SCTP_LOCAL_PORT \
    -Dsctp.remote.host=$SCTP_REMOTE_HOST \
    -Dsctp.remote.port=$SCTP_REMOTE_PORT \
    -Dm3ua.routing.context=$M3UA_ROUTING_CONTEXT \
    -jar /app/ss7-ha-gateway.jar
