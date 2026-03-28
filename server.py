import base64
import json
import traceback
from contextlib import asynccontextmanager
import httpx
import uvicorn
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import StreamingResponse

OLLAMA_API = "http://localhost:11434/api/generate"
MODEL_NAME = "qwen3-vl:4b"

GENERATION_OPTIONS = {
    "num_predict": 2048,
    "num_ctx": 4096,
    "temperature": 0.7,
}

SYSTEM_PROMPT = """Ты — ассистент, который извлекает текст с изображений и возвращает его в строгом JSON-формате. В ответе должен быть только JSON-объект без пояснений. Формат JSON:

{
  "text": "текст",
  "style": "normal",   // возможные значения: normal, bold, italic, underline
  "alignment": "left"  // возможные значения: left, center, right
}

Если текста на изображении много, верни массив таких объектов. Не используй markdown, только обычный текст в поле text.
Если на изображении нет текста, верни {"error": "Текст не найден"} также элементы системы за текст не считай.
"""

@asynccontextmanager
async def lifespan(app: FastAPI):
    limits = httpx.Limits(max_keepalive_connections=10, max_connections=20)
    app.state.client = httpx.AsyncClient(timeout=3600.0, limits=limits)
    yield
    await app.state.client.aclose()

app = FastAPI(lifespan=lifespan)

@app.post("/")
async def generate(request: Request):
    content_type = request.headers.get("content-type", "")
    text_message = ""
    images_base64 = []

    try:
        if content_type.startswith("multipart/form-data"):
            try:
                form = await request.form()
                text_message = form.get("message", "")
                image_files = form.getlist("image")
                print(f"[DEBUG] Received {len(image_files)} image(s), message='{text_message[:50]}'")
                for img_file in image_files:
                    if img_file and hasattr(img_file, "read"):
                        data = await img_file.read()
                        if not data:
                            print("[WARN] Empty image file")
                            continue
                        images_base64.append(base64.b64encode(data).decode("utf-8"))
            except Exception as e:
                print(f"[ERROR] Multipart parse error: {e}")
                traceback.print_exc()
                raise HTTPException(status_code=400, detail=f"Multipart error: {e}")

        elif content_type.startswith("application/json"):
            try:
                body = await request.json()
                text_message = body.get("message", "")
            except Exception as e:
                print(f"[ERROR] JSON parse error: {e}")
                traceback.print_exc()
                raise HTTPException(status_code=400, detail=f"JSON error: {e}")

        else:
            raise HTTPException(status_code=415, detail="Unsupported Media Type")

        if not text_message and not images_base64:
            print("[ERROR] Empty request (no message and no images)")
            raise HTTPException(status_code=400, detail="Empty message and no images")

    except HTTPException as he:
        raise he
    except Exception as e:
        print(f"[ERROR] Unexpected: {e}")
        traceback.print_exc()
        raise HTTPException(status_code=400, detail="Bad Request")

    ollama_payload = {
        "model": MODEL_NAME,
        "prompt": text_message,
        "system": SYSTEM_PROMPT,
        "stream": True,
        "format": "json",
        "options": GENERATION_OPTIONS
    }
    if images_base64:
        ollama_payload["images"] = images_base64
        print(f"[INFO] Images count: {len(images_base64)}, sizes: {[len(img) for img in images_base64]}")
    else:
        print("[INFO] No images")

    print(f"[INFO] Request message: {text_message[:100] if text_message else ''}")

    async def generate_stream():
        print("\n" + "="*60)
        print("[AI RESPONSE START]")
        print("="*60)
        thinking_started = False
        response_started = False

        try:
            async with app.state.client.stream("POST", OLLAMA_API, json=ollama_payload) as response:
                if response.status_code != 200:
                    error_text = await response.aread()
                    error_msg = f'Ollama error {response.status_code}: {error_text.decode()}'
                    print(f"[ERROR] {error_msg}")
                    yield f"data: {json.dumps({'error': error_msg})}\n\n"
                    return

                async for line in response.aiter_lines():
                    if line:
                        try:
                            data = json.loads(line)
                            token = data.get("response", "")
                            thinking = data.get("thinking", "")

                            if thinking:
                                if not thinking_started:
                                    print("\n\033[90m[THINKING]\n", end="", flush=True)
                                    thinking_started = True
                                print(f"\033[90m{thinking}\033[0m", end="", flush=True)
                                yield f"data: {json.dumps({'thinking': thinking})}\n\n"

                            if token:
                                if thinking_started and not response_started:
                                    print("\n\033[0m\n[RESPONSE]\n", end="", flush=True)
                                    response_started = True
                                print(f"\033[92m{token}\033[0m", end="", flush=True)
                                yield f"data: {json.dumps({'token': token})}\n\n"

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

    return StreamingResponse(generate_stream(), media_type="text/event-stream")

if __name__ == "__main__":
    PORT = 5002
    print(f"Server running on port {PORT}, model: {MODEL_NAME}")
    uvicorn.run(
        "server:app",
        host="0.0.0.0",
        port=PORT,
        log_level="info",
        loop="asyncio",
        reload=False
    )