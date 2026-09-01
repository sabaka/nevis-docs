#!/usr/bin/env python3
"""Load sample-data.json into a running instance.

    python3 load-sample-data.py [base-url]

Defaults to http://localhost:8080. Creates each client, then its documents.
Prints the ids so you can follow them into GET /search.
"""
import json
import sys
import urllib.error
import urllib.request

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080").rstrip("/")


def post(path, payload):
    request = urllib.request.Request(
        BASE + path,
        data=json.dumps(payload).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request) as response:
            return response.status, json.loads(response.read() or b"{}")
    except urllib.error.HTTPError as error:
        return error.code, (error.read() or b"").decode()[:200]
    except urllib.error.URLError as error:
        sys.exit(f"cannot reach {BASE} — is the app running?  ({error.reason})")


data = json.load(open("sample-data.json"))
created_clients = 0
created_documents = 0

for entry in data["clients"]:
    client = entry["client"]
    status, body = post("/clients", client)
    if status != 201:
        print(f"  client {client['email']}: HTTP {status} {body}")
        continue
    client_id = body["id"]
    created_clients += 1
    print(f"CLIENT   {client_id}  {client['email']}")
    for document in entry["documents"]:
        status, body = post(f"/clients/{client_id}/documents", document)
        if status != 201:
            print(f"  document {document['title']!r}: HTTP {status} {body}")
            continue
        created_documents += 1
        print(f"  DOCUMENT {body['id']}  {document['title']}")

print(f"\ncreated {created_clients} clients, {created_documents} documents")
print("\nembeddings are generated asynchronously; give the worker a few seconds, then:")
print(f"  curl -s '{BASE}/search?q=NevisWealth'   | python3 -m json.tool")
print(f"  curl -s '{BASE}/search?q=address+proof' | python3 -m json.tool")
