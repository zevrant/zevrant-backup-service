FROM zevrant/zevrant-ubuntu-base:latest

EXPOSE 9009

RUN mkdir -p /usr/local/microservices/zevrant-home-services/zevrant-backup-service/

RUN mkdir -p /var/log/zevrant-home-services/zevrant-backup-service\
  && mkdir -p /storage/keys

RUN useradd -m -d /usr/local/microservices/zevrant-home-services/zevrant-backup-service/ -G developers  zevrant-backup-service

RUN chown -R zevrant-backup-service:developers /var/log/zevrant-home-services/zevrant-backup-service /usr/local/microservices/zevrant-home-services/zevrant-backup-service /storage/keys

USER zevrant-backup-service

COPY zevrant-backup-service.jar /usr/local/microservices/zevrant-home-services/zevrant-backup-service/zevrant-backup-service.jar

RUN mkdir ~/.aws; echo "[default]" > ~/.aws/config; echo "region = us-east-1" >> ~/.aws/config; echo "output = json" >> ~/.aws/config

RUN curl https://raw.githubusercontent.com/zevrant/zevrant-services-pipeline/master/bash/zevrant-services-start.sh > ~/startup.sh \
  && curl https://raw.githubusercontent.com/zevrant/zevrant-services-pipeline/master/bash/openssl.conf > ~/openssl.conf

#TODO fix role
CMD export ROLE_ARN="arn:aws:iam::725235728275:role/BackupServiceRole" \
 && password=`date +%s | sha256sum | base64 | head -c 32` \
 && bash ~/startup.sh zevrant-backup-service $password \
 && java -jar -Dspring.profiles.active=$ENVIRONMENT -Dpassword=$password /usr/local/microservices/zevrant-home-services/zevrant-backup-service/zevrant-backup-service.jar
