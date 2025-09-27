from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import requests
import json
import re
from typing import Optional

app = FastAPI(title="Smith Assistant API", version="1.0.0")

class Command(BaseModel):
    text: str
    context: Optional[str] = "mobile_assistant"

class SmithResponse(BaseModel):
    reply: str
    action: str
    target: str
    emotion: str
    confidence: float

def query_gemma(user_input: str) -> dict:
    """Query Gemma via Ollama API with structured prompting"""
    
    system_prompt = """You are Smith, an intelligent mobile assistant. You must respond ONLY with valid JSON.

Analyze the user's command and provide a JSON response with exactly these fields:
- "reply": A friendly, concise response (maximum 20 words)
- "action": Choose ONE from [call, sms, search, open_app, mobile_data, hotspot, wifi, bluetooth, settings, none]
- "target": The specific target for the action (phone number, search query, app name, contact name, etc.)
- "emotion": Choose ONE from [happy, sad, excited, calm, confident, helpful, friendly, thoughtful, apologetic]
- "confidence": A number between 0.0 and 1.0 indicating how confident you are about the action

Examples:
User: "Call mom" → {"reply": "Calling Mom now", "action": "call", "target": "mom", "emotion": "friendly", "confidence": 0.9}
User: "Turn on mobile data" → {"reply": "Activating mobile data", "action": "mobile_data", "target": "on", "emotion": "helpful", "confidence": 0.95}

CRITICAL: Respond with ONLY the JSON object, no other text."""

    prompt = f"{system_prompt}\n\nUser: \"{user_input}\"\n\nJSON:"

    try:
        # Query Ollama with your gemma2:2b model
        response = requests.post(
            'http://localhost:11434/api/generate',
            json={
                'model': 'gemma2:2b',
                'prompt': prompt,
                'stream': False,
                'options': {
                    'temperature': 0.1,
                    'top_p': 0.9,
                    'top_k': 40,
                    'num_predict': 150
                }
            },
            timeout=30
        )
        
        if response.status_code != 200:
            raise Exception(f"Ollama API error: {response.status_code}")
            
        result = response.json()
        gemma_response = result.get('response', '').strip()
        
        try:
            start = gemma_response.find('{')
            end = gemma_response.rfind('}') + 1
            
            if start != -1 and end > start:
                json_str = gemma_response[start:end]
                parsed_json = json.loads(json_str)
                
                required_fields = ['reply', 'action', 'target', 'emotion', 'confidence']
                if all(field in parsed_json for field in required_fields):
                    return parsed_json
                else:
                    raise ValueError("Missing required fields")
            else:
                raise ValueError("No JSON found in response")
                
        except (json.JSONDecodeError, ValueError) as e:
            print(f"JSON parsing failed: {e}")
            print(f"Raw response: {gemma_response}")
            return parse_command_fallback(user_input)
            
    except Exception as e:
        print(f"Gemma query failed: {e}")
        return parse_command_fallback(user_input)

def parse_command_fallback(text: str) -> dict:
    """Fallback parser when Gemma fails"""
    text_lower = text.lower().strip()
    
    if "call" in text_lower:
        target = "mom" if "mom" in text_lower else "unknown"
        return {
            "reply": f"Calling {target}",
            "action": "call",
            "target": target,
            "emotion": "friendly",
            "confidence": 0.8
        }
    elif "search" in text_lower:
        query = re.sub(r'search\s+(for\s+)?', '', text, flags=re.IGNORECASE).strip()
        return {
            "reply": f"Searching for {query}",
            "action": "search",
            "target": query if query else "general search",
            "emotion": "helpful",
            "confidence": 0.8
        }
    elif "turn on" in text_lower and "data" in text_lower:
        return {
            "reply": "Turning on mobile data",
            "action": "mobile_data",
            "target": "on",
            "emotion": "helpful",
            "confidence": 0.9
        }
    
    return {
        "reply": "I heard you, but I'm not sure how to help with that yet",
        "action": "none",
        "target": "",
        "emotion": "apologetic",
        "confidence": 0.5
    }

@app.get("/")
def root():
    return {"message": "Smith Assistant API is running", "status": "ready"}

@app.get("/health")
def health_check():
    try:
        test_response = requests.get('http://localhost:11434/api/tags', timeout=5)
        if test_response.status_code == 200:
            return {"status": "healthy", "ollama": "connected"}
        else:
            return {"status": "unhealthy", "ollama": "disconnected"}
    except:
        return {"status": "unhealthy", "ollama": "unreachable"}

@app.post("/query")
def process_command(command: Command):
    try:
        if not command.text or command.text.strip() == "":
            raise HTTPException(status_code=400, detail="Empty command text")
        
        result = query_gemma(command.text.strip())
        
        return SmithResponse(
            reply=result['reply'],
            action=result['action'],
            target=result['target'],
            emotion=result['emotion'],
            confidence=result['confidence']
        )
        
    except Exception as e:
        print(f"Error processing command: {e}")
        return SmithResponse(
            reply="I'm having trouble processing that command",
            action="none",
            target="",
            emotion="apologetic",
            confidence=0.0
        )

if __name__ == "__main__":
    import uvicorn
    print("Starting Smith Assistant Server...")
    print("Ollama should be running on: http://localhost:11434")
    print("API will be available at: http://localhost:8000")
    uvicorn.run(app, host="0.0.0.0", port=8000, log_level="info")