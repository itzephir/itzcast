#!/usr/bin/env python3
"""A process-local counter demonstrating an action with no payload and refresh."""
import json
import sys

count = 0
for line in sys.stdin:
    if not line.strip():
        continue
    request = json.loads(line)
    if request["type"] == "prefix":
        response = {"suggestions": [{
            "id": "example.counter:value",
            "title": f"Counter: {count}",
            "subtitle": "Press Enter to increment; the window stays open",
            "score": 100,
            "action": {"id": "example.counter/increment"},
        }]}
    elif request["type"] == "execute":
        if request["id"] == "example.counter/increment":
            count += 1
            response = {"actionResult": {"succeeded": True, "outcome": "refresh"}}
        else:
            response = {"actionResult": {"succeeded": False, "error": "Unknown action"}}
    else:
        response = {}
    print(json.dumps(response, separators=(",", ":")), flush=True)
