# yong_ml FastAPI (decisions D17). The yong_ml repo is cloned next to this repo and passed in as
# the yong_ml build context; only the files the API needs are copied (no .git, .venv or .env)
FROM python:3.12-slim
WORKDIR /app
COPY --from=yong_ml requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
COPY --from=yong_ml api.py config.py run_all.py ./
COPY --from=yong_ml yongml ./yongml
COPY --from=yong_ml prompts ./prompts
EXPOSE 8000
CMD ["uvicorn", "api:app", "--host", "0.0.0.0", "--port", "8000"]
