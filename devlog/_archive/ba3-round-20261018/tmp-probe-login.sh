C=$(curl -s --max-time 180 http://127.0.0.1:8087/v3/api-docs | wc -c)
echo "api-docs bytes=$C"
echo "listening=$(netstat -ano | grep -c ':8087 .*LISTENING')"
date +%H:%M:%S
