#!/bin/bash
# OrangeJD 服务器启动脚本
# 部署路径: /root/start.sh
# 启动方式: nohup /root/start.sh > /root/app.log 2>&1 &

export PATH=$PATH:/www/server/java/jdk-17.0.8/bin
export MYSQL_USER=root
export MYSQL_PASSWORD='Az919800.'
export MYSQL_DB=orangejd
export REDIS_PASSWORD='Az919800.'
export UPLOAD_PATH=/root/uploads

java -jar /root/orange-server-1.0.0-SNAPSHOT.jar --spring.profiles.active=prod
