from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import requests
import json
import datetime
import re
import socket
from typing import Optional

def get_local_ip():
    """Get the local IP address"""
    try:
        # Method 1: Connect to a remote address to determine local IP
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except:
        try:
            # Method 2: Use hostname
            return socket.gethostbyname(socket.gethostname())
        except:
            return "localhost"

# Get local IP for external access, but use localhost for Ollama
LOCAL_IP = get_local_ip()
OLLAMA_URL = 'http://localhost:11434'  # Always use localhost for Ollama

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
- "action": Choose ONE from [call, sms, search, open_app, mobile_data, hotspot, wifi, bluetooth, settings, time, date, weather, none]
- "target": The specific target for the action (phone number, search query, app name, contact name, etc.)
- "emotion": Choose ONE from [happy, sad, excited, calm, confident, helpful, friendly, thoughtful, apologetic]
- "confidence": A number between 0.0 and 1.0 indicating how confident you are about the action

Examples:
User: "Call mom" → {"reply": "Calling Mom now", "action": "call", "target": "mom", "emotion": "friendly", "confidence": 0.9}
User: "What time is it" → {"reply": "Let me check the time", "action": "time", "target": "", "emotion": "helpful", "confidence": 0.95}
User: "How are you" → {"reply": "I'm doing great and ready to help!", "action": "none", "target": "", "emotion": "happy", "confidence": 0.9}

CRITICAL: Respond with ONLY the JSON object, no other text."""

    prompt = f"{system_prompt}\n\nUser: \"{user_input}\"\n\nJSON:"

    try:
        # Query Ollama using localhost (same machine)
        response = requests.post(
            f'{OLLAMA_URL}/api/generate',
            json={
                'model': 'gemma2:2b',  # Updated to your correct model
                'prompt': prompt,
                'stream': False,
                'options': {
                    'temperature': 0.1,
                    'top_p': 0.9,
                    'top_k': 40,
                    'num_predict': 150
                }
            },
            timeout=90
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
    
    if "how are you" in text_lower:
        return {
            "reply": "I'm doing great and ready to help you!",
            "action": "none",
            "target": "",
            "emotion": "friendly",
            "confidence": 0.9
        }
    elif "time" in text_lower:
        return {
            "reply": "Let me check the current time for you",
            "action": "time",
            "target": "",
            "emotion": "helpful",
            "confidence": 0.9
        }
    elif "call" in text_lower:
        target = "mom" if "mom" in text_lower else "unknown"
        return {
            "reply": f"Calling {target}",
            "action": "call",
            "target": target,
            "emotion": "friendly",
            "confidence": 0.8
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
    return {
        "message": "Smith Assistant API is running", 
        "status": "ready",
        "server_ip": LOCAL_IP,
        "ollama_url": OLLAMA_URL
    }

@app.get("/health")
def health_check():
    try:
        test_response = requests.get(f'{OLLAMA_URL}/api/tags', timeout=5)
        if test_response.status_code == 200:
            return {
                "status": "healthy", 
                "ollama": "connected",
                "server_ip": LOCAL_IP,
                "ollama_url": OLLAMA_URL
            }
        else:
            return {
                "status": "unhealthy", 
                "ollama": "disconnected",
                "server_ip": LOCAL_IP,
                "ollama_url": OLLAMA_URL
            }
    except:
        return {
            "status": "unhealthy", 
            "ollama": "unreachable",
            "server_ip": LOCAL_IP,
            "ollama_url": OLLAMA_URL
        }

def execute_action(action: str, target: str) -> str:
    """Execute actual actions and return results"""
    try:
        if action == "time":
            current_time = datetime.datetime.now().strftime("%I:%M %p")
            current_date = datetime.datetime.now().strftime("%A, %B %d, %Y")
            return f"The current time is {current_time} on {current_date}"
        
        elif action == "date":
            current_date = datetime.datetime.now().strftime("%A, %B %d, %Y")
            return f"Today is {current_date}"
        
        elif action == "weather":
            return "I'd need access to a weather service to get the current weather."
        
        elif action == "search":
            return f"I would search for '{target}' but I need your phone to execute the browser opening."
        
        else:
            return ""
    except Exception as e:
        return f"I had trouble getting that information: {str(e)}"

@app.post("/query")
def process_command(command: Command):
    try:
        if not command.text or command.text.strip() == "":
            raise HTTPException(status_code=400, detail="Empty command text")
        
        result = query_gemma(command.text.strip())
        
        # Execute action if needed
        action_result = ""
        if result['action'] != "none":
            action_result = execute_action(result['action'], result['target'])
            if action_result:
                result['reply'] = action_result
        
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
    print("Starting Groot Assistant Server...")
    print(f"Detected Local IP: {LOCAL_IP}")
    print(f"Ollama URL: {OLLAMA_URL}")
    print("API will be available at: http://0.0.0.0:8000")
    print(f"Health check: http://{LOCAL_IP}:8000/health")
    uvicorn.run(app, host="0.0.0.0", port=8000, log_level="info")