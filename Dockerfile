FROM java:8-alpine
RUN mkdir -p /app
WORKDIR /app
COPY target/cdr-0.0.1-standalone.jar .
COPY resources/public resources/public
CMD java -jar cdr-0.0.1-standalone.jar
EXPOSE 3000
