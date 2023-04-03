FROM sbtscala/scala-sbt:eclipse-temurin-jammy-17.0.5_8_1.8.2_3.2.1

WORKDIR /app
COPY build.sbt .
COPY project/ project/
RUN sbt dev/update

COPY core/ core/
COPY staging/ staging/
COPY dev/ dev/
COPY jetty-docker.xml jetty.xml

EXPOSE 8080
ENTRYPOINT exec sbt ~dev/Jetty/debug dev/Jetty/join
