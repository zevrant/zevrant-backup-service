FROM docker.io/zevrant/zevrant-ubuntu-base:latest

EXPOSE 9009

RUN mkdir -p /usr/local/microservices/zevrant-home-services/zevrant-backup-service/

RUN mkdir -p /var/log/zevrant-home-services/zevrant-backup-service\
  && mkdir -p /storage/keys

RUN useradd -m -d /usr/local/microservices/zevrant-home-services/zevrant-backup-service/ -G developers  zevrant-backup-service

RUN chown -R zevrant-backup-service:developers /var/log/zevrant-home-services/zevrant-backup-service /usr/local/microservices/zevrant-home-services/zevrant-backup-service /storage/keys

USER zevrant-backup-service

COPY build/libs/zevrant-backup-service-0.0.1-SNAPSHOT.jar /usr/local/microservices/zevrant-home-services/zevrant-backup-service/zevrant-backup-service.jar

RUN mkdir ~/.aws; echo "[default]" > ~/.aws/config; echo "region = us-east-1" >> ~/.aws/config; echo "output = json" >> ~/.aws/config

RUN curl https://raw.githubusercontent.com/zevrant/zevrant-services-pipeline/master/bash/zevrant-services-start.sh > ~/startup.sh \
  && curl https://raw.githubusercontent.com/zevrant/zevrant-services-pipeline/master/bash/openssl.conf > ~/openssl.conf

CMD password=`date +%s | sha256sum | base64 | head -c 32` \
 && bash ~/startup.sh zevrant-backup-service $password \
 && java -jar -XX:MinRAMPercentage=25 -XX:MaxRAMPercentage=90 -Dspring.profiles.active=$ENVIRONMENT,liquibase -DACCESS_KEY_ID=$ACCESS_KEY_ID -DACCESS_SECRET_KEY=$ACCESS_SECRET_KEY -Dpassword=$password /usr/local/microservices/zevrant-home-services/zevrant-backup-service/zevrant-backup-service.jar
