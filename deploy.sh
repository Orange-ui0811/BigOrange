#!/bin/bash
# OrangeJD 一键部署脚本 — 构建生产版本并打包
# 用法: ./deploy.sh
# 产物: /tmp/orangejd-deploy.tar.gz (含 jar + web + admin)

echo "=== 1. 构建后端 ==="
cd /d/Orangejd
mvn clean package -pl orange-server -am -DskipTests -q || exit 1

echo "=== 2. 构建前端(生产模式) ==="
cd /d/Orangejd/orange-web
sed 's|http://127.0.0.1:8080|/api|' .env > .env.production
npx vite build 2>&1 | tail -1

cd /d/Orangejd/orange-admin
sed 's|http://127.0.0.1:8080|/api|' .env > .env.production
npx vite build 2>&1 | tail -1

echo "=== 3. 打包 ==="
mkdir -p /tmp/orangejd-deploy
cp /d/Orangejd/orange-server/target/orange-server-1.0.0-SNAPSHOT.jar /tmp/orangejd-deploy/
cp -r /d/Orangejd/orange-web/dist /tmp/orangejd-deploy/web
cp -r /d/Orangejd/orange-admin/dist /tmp/orangejd-deploy/admin
cd /tmp && tar -czf orangejd-deploy.tar.gz orangejd-deploy/

echo "=== 完成 ==="
ls -lh /tmp/orangejd-deploy.tar.gz
echo ""
echo "上传此文件到服务器 /root，然后解压："
echo "  tar -xzf /root/orangejd-deploy.tar.gz -C /root/"
echo "  cp -r /root/orangejd-deploy/web/* /data/www/orangejd/web/"
echo "  cp -r /root/orangejd-deploy/admin/* /data/www/orangejd/admin/"
echo "  cp /root/orangejd-deploy/orange-server-*.jar /root/"
echo "  pkill -9 -f orange-server && nohup /root/start.sh > /root/app.log 2>&1 &"
rm -rf /tmp/orangejd-deploy/
