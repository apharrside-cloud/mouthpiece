FROM python:3.12-alpine
WORKDIR /app
COPY server.py index.html icon.svg icon-192.png icon-512.png apple-touch-icon.png favicon-48.png og.png /app/
ENV DATA_DIR=/data PORT=8080
EXPOSE 8080
CMD ["python", "-u", "/app/server.py"]
