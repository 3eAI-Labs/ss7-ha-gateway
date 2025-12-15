# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY . .
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine

# Install SCTP support
RUN apk add --no-cache lksctp-tools

WORKDIR /app

# Copy the built JAR from the build stage
COPY --from=build /build/ss7-core/target/ss7-core-1.0.0-SNAPSHOT.jar /app/ss7-ha-gateway.jar

# Create non-root user
RUN addgroup -g 1000 ss7 && \
    adduser -D -u 1000 -G ss7 ss7 && \
    chown -R ss7:ss7 /app

USER ss7

# Environment variables
ENV JAVA_OPTS="-Xms512m -Xmx1024m"
ENV LOCAL_PC="1-1-1"
ENV REMOTE_PC="2-2-2"
ENV REMOTE_SSN="8"
ENV SCTP_LOCAL_PORT="2905"
ENV SCTP_REMOTE_HOST="ss7-ha-gw-2.ss7.svc.cluster.local"
ENV SCTP_REMOTE_PORT="2905"
ENV M3UA_ROUTING_CONTEXT="100"
ENV NATS_URL="nats://nats.nats.svc.cluster.local:4222"
ENV SCCP_LOCAL_ADDRESSES="123456789"

EXPOSE 2905

CMD java $JAVA_OPTS \
    -Dlocal.pc=$LOCAL_PC \
    -Dremote.pc=$REMOTE_PC \
    -Dremote.ssn=$REMOTE_SSN \
    -Dsccp.local.addresses=$SCCP_LOCAL_ADDRESSES \
    -Dsctp.local.port=$SCTP_LOCAL_PORT \
    -Dsctp.remote.host=$SCTP_REMOTE_HOST \
    -Dsctp.remote.port=$SCTP_REMOTE_PORT \
    -Dm3ua.routing.context=$M3UA_ROUTING_CONTEXT \
    -Dnats.server.url=$NATS_URL \
    -jar /app/ss7-ha-gateway.jar
