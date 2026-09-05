#!/usr/bin/env python3
import json
import sys
from urllib.parse import quote


def handle(request):
    hook = request.get("type")

    if hook == "launch":
        context = request.get("context", {"attributes": {}})
        attributes = dict(context.get("attributes", {}))
        attributes["githubSearch"] = "enabled"
        respond({"launchContext": {"attributes": attributes}})
        return

    if hook == "prefix":
        query = request["match"]["arguments"].strip()
        suggestions = []
        if query:
            suggestions.append({
                "id": "example.github-search:" + query,
                "title": "Search GitHub for “" + query + "”",
                "subtitle": "github.com",
                "score": 92.0,
                "kind": "WEB",
                "sourceId": "example.github-search",
                "action": {
                    "id": "itzcast/openUrl",
                    "payload": {"url": "https://github.com/search?q=" + quote(query)},
                },
            })
        respond({"suggestions": suggestions})
        return

    # A real metrics extension could persist BEFORE/AFTER use events here.
    respond({})


def respond(payload):
    print(json.dumps(payload, ensure_ascii=False, separators=(",", ":")), flush=True)


if __name__ == "__main__":
    for line in sys.stdin:
        if line.strip():
            handle(json.loads(line))
