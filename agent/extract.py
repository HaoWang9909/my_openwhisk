import couchdb
import json
import csv

# 连接到 CouchDB
couch_client = couchdb.Server("http://admin:admin@localhost:5984")
couch_activations = couch_client["whisk_local_activations"]

# 获取所有以 "guest/" 开头的文档
activations = []
for doc_id in couch_activations:
    if doc_id.startswith("guest/"):
        doc = couch_activations[doc_id]
        
        # 提取 initTime
        init_time = 0
        for annotation in doc.get("annotations", []):
            if annotation.get("key") == "initTime":
                init_time = annotation.get("value", 0)
                break

        activation = {
            "name": doc.get("name", "Unknown"),
            "activationId": doc.get("activationId", "Unknown"),
            "duration": doc.get("duration", "Unknown"),
            "initTime": init_time,
            "start": doc.get("start", 0)
        }
        activations.append(activation)

# 按时间排序并获取最新的4条记录
activations.sort(key=lambda x: x["start"], reverse=True)
activations = activations[:4]

# 移除 start 字段
for activation in activations:
    del activation["start"]

# 保存结果
with open("activations_filtered.json", "w", encoding="utf-8") as jsonfile:
    json.dump(activations, jsonfile, indent=4, ensure_ascii=False)

with open("activations_filtered.csv", "w", newline="", encoding="utf-8") as csvfile:
    fieldnames = ["name", "activationId", "duration", "initTime"]
    writer = csv.DictWriter(csvfile, fieldnames=fieldnames)
    writer.writeheader()
    writer.writerows(activations)

print("Activation records have been saved to activations_filtered.json and activations_filtered.csv")