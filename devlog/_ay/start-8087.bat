@echo off
cd /d E:\wowowo
"C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\java.exe" -jar E:\wowowo\backend\dashboard-service\target\dashboard-service-1.0.0-SNAPSHOT.jar --server.port=8087 >> E:\wowowo\devlog\ba3-8087.log 2>> E:\wowowo\devlog\ba3-8087.err
