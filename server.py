import base64
import json
from contextlib import asynccontextmanager
import httpx
import uvicorn
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import StreamingResponse

# ==================== Конфигурация ====================
# URL API Ollama (локальный сервер)
OLLAMA_API = "http://localhost:11434/api/generate"

# Имя модели (поддерживает мультимодальность: текст + изображения)
MODEL_NAME = "qwen3-vl:4b"

# Параметры генерации: максимальное количество токенов, контекст, температура
GENERATION_OPTIONS = {
    "num_predict": 2048,   # максимальное количество токенов в ответе
    "num_ctx": 4096,       # размер контекстного окна
    "temperature": 0.7,    # "креативность" ответа (0.0 — детерминированно, 1.0 — более случайно)
}


# ==================== Lifespan (управление жизненным циклом приложения) ====================
@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    Контекстный менеджер для управления ресурсами при старте и остановке приложения.
    Создаёт HTTP-клиент (httpx.AsyncClient) с настройками пула соединений и таймаутом.
    Клиент будет доступен как app.state.client и используется для запросов к Ollama.
    При завершении приложения клиент закрывается.
    """
    # Ограничения на количество поддерживаемых keep-alive соединений и общее количество соединений
    limits = httpx.Limits(max_keepalive_connections=10, max_connections=20)
    # Создаём асинхронный HTTP-клиент с большим таймаутом (3600 сек) для долгих генераций
    app.state.client = httpx.AsyncClient(timeout=3600.0, limits=limits)
    yield  # приложение запущено
    # После остановки приложения закрываем клиент
    await app.state.client.aclose()


# ==================== FastAPI приложение ====================
app = FastAPI(lifespan=lifespan)


# ==================== Единственный эндпоинт ====================
@app.post("/")
async def generate(request: Request):
    """
    Принимает POST-запросы:
    - multipart/form-data: поля "message" (текст) и "image" (файлы изображений, может быть несколько)
    - application/json: тело {"message": "текст"}
    Возвращает поток событий (text/event-stream) с JSON-объектами:
        - {"thinking": "..."} — промежуточные размышления модели
        - {"token": "..."}    — очередная часть ответа
        - {"error": "..."}    — ошибка
    """
    content_type = request.headers.get("content-type", "")
    text_message = ""
    images_base64 = []

    # ==================== Разбор входящего запроса ====================
    # Обработка multipart/form-data (отправка с изображениями)
    if content_type.startswith("multipart/form-data"):
        try:
            form = await request.form()
            text_message = form.get("message", "")
            image_files = form.getlist("image")
            for img_file in image_files:
                if img_file and hasattr(img_file, "read"):
                    data = await img_file.read()
                    # Кодируем изображение в base64 (Ollama ожидает именно такой формат)
                    images_base64.append(base64.b64encode(data).decode("utf-8"))
        except Exception as e:
            raise HTTPException(status_code=400, detail=f"Multipart error: {e}")

    # Обработка JSON (только текст)
    elif content_type.startswith("application/json"):
        try:
            body = await request.json()
            text_message = body.get("message", "")
        except Exception as e:
            raise HTTPException(status_code=400, detail=f"JSON error: {e}")

    # Неподдерживаемый тип контента
    else:
        raise HTTPException(status_code=415, detail="Unsupported Media Type")

    # ==================== Формирование запроса к Ollama ====================
    ollama_payload = {
        "model": MODEL_NAME,
        "prompt": text_message,
        "stream": True,               # включён потоковый режим
        "options": GENERATION_OPTIONS
    }
    # Если есть изображения — добавляем их в запрос (ожидаются списком base64)
    if images_base64:
        ollama_payload["images"] = images_base64
        print(f"[INFO] Images count: {len(images_base64)}, sizes: {[len(img) for img in images_base64]}")

    print(f"[INFO] Request message: {text_message[:100] if text_message else ''}")

    # ==================== Генератор потока ответа ====================
    async def generate_stream():
        """
        Асинхронный генератор, который читает поток от Ollama и отправляет
        события клиенту в формате SSE (text/event-stream).
        """
        print("\n" + "="*60)
        print("[AI RESPONSE START]")
        print("="*60)
        thinking_started = False
        response_started = False

        try:
            # Отправляем запрос к Ollama с потоковым режимом
            async with app.state.client.stream("POST", OLLAMA_API, json=ollama_payload) as response:
                if response.status_code != 200:
                    error_text = await response.aread()
                    error_msg = f'Ollama error {response.status_code}: {error_text.decode()}'
                    print(f"[ERROR] {error_msg}")
                    yield f"data: {json.dumps({'error': error_msg})}\n\n"
                    return

                # Читаем строки ответа построчно (каждая строка — JSON-объект)
                async for line in response.aiter_lines():
                    if line:
                        try:
                            data = json.loads(line)
                            token = data.get("response", "")
                            thinking = data.get("thinking", "")

                            # Отправка размышлений (thinking)
                            if thinking:
                                if not thinking_started:
                                    print("\n\033[90m[THINKING]\n", end="", flush=True)
                                    thinking_started = True
                                print(f"\033[90m{thinking}\033[0m", end="", flush=True)
                                yield f"data: {json.dumps({'thinking': thinking})}\n\n"

                            # Отправка токенов основного ответа
                            if token:
                                if thinking_started and not response_started:
                                    print("\n\033[0m\n[RESPONSE]\n", end="", flush=True)
                                    response_started = True
                                print(f"\033[92m{token}\033[0m", end="", flush=True)
                                yield f"data: {json.dumps({'token': token})}\n\n"

                            # Если ответ завершён (done: true) — выходим
                            if data.get("done", False):
                                print("\n" + "="*60)
                                print("[AI RESPONSE END]")
                                print("="*60 + "\n")
                                break

                        except Exception as e:
                            print(f"[ERROR] {str(e)}")
                            yield f"data: {json.dumps({'error': str(e)})}\n\n"

        except Exception as e:
            print(f"[ERROR] {str(e)}")
            yield f"data: {json.dumps({'error': str(e)})}\n\n"

    # Возвращаем ответ как поток событий
    return StreamingResponse(generate_stream(), media_type="text/event-stream")


# ==================== Точка входа (запуск сервера) ====================
if __name__ == "__main__":
    PORT = 5002
    print(f"Server running on port {PORT}, model: {MODEL_NAME}")
    uvicorn.run(
        "server:app",
        host="0.0.0.0",
        port=PORT,
        log_level="info",
        loop="uvloop",      # высокопроизводительный цикл событий uvloop
        reload=False        # без автоматической перезагрузки (для стабильности)
    )