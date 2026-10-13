FROM python:3.12-slim
WORKDIR /app
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
COPY requirements.lock.txt .
RUN pip install --no-cache-dir -r requirements.lock.txt && useradd --create-home --uid 10001 healthys
COPY healthys_ai healthys_ai
USER healthys
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=5s CMD python -c "import os,urllib.request; r=urllib.request.Request('http://127.0.0.1:8000/health',headers={'Authorization':'Bearer '+os.environ['HEALTHYS_AI_SERVICE_TOKEN']}); urllib.request.urlopen(r,timeout=3)"
CMD ["uvicorn", "healthys_ai.app:create_app", "--factory", "--host", "0.0.0.0", "--port", "8000", "--no-access-log", "--log-level", "critical"]
