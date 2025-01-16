import requests
import json
import csv

# Elasticsearch URL
ELASTICSEARCH_URL = "http://localhost:9200/openwhisk-guest/_search"

# Query payload
query_payload = {
    "query": {
        "match_all": {}
    },
    "sort": [
        {
            "start": {
                "order": "desc"
            }
        }
    ],
    "size": 12 # Number of records to retrieve
}

# Send Elasticsearch query
response = requests.get(
    ELASTICSEARCH_URL,
    headers={"Content-Type": "application/json"},
    data=json.dumps(query_payload)
)

if response.status_code == 200:
    # Parse JSON data
    data = response.json()
    activations = []

    # Extract activation records
    for hit in data["hits"]["hits"]:
        source = hit["_source"]
        annotations = source.get("annotations", {})

        # Extract initTime (default to 0 if not available)
        if isinstance(annotations, dict):
            init_time = annotations.get("initTime", 0)
        else:
            init_time = 0

        activation = {
            "name": source.get("name", "Unknown"),
            "activationId": source.get("activationId", "Unknown"),
            "duration": source.get("duration", "Unknown"),
            "initTime": init_time
        }
        activations.append(activation)

    # Save to JSON file
    with open("activations_filtered.json", "w", encoding="utf-8") as jsonfile:
        json.dump(activations, jsonfile, indent=4, ensure_ascii=False)

    # Save to CSV file
    with open("activations_filtered.csv", "w", newline="", encoding="utf-8") as csvfile:
        fieldnames = ["name", "activationId", "duration", "initTime"]
        writer = csv.DictWriter(csvfile, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(activations)

    print("Activation records have been saved to activations_filtered.json and activations_filtered.csv")
else:
    print(f"Query failed with status code: {response.status_code}, error: {response.text}")