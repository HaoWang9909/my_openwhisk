import csv

# Define the raw data
data = [
    {"start_type": "normal prewarm", "entries": [
        ("tn", "e2943218f36149d7943218f361d9d7f8", 2754, 663),
        ("is", "4ee323f88a814895a323f88a81f89518", 957, 787),
        ("tn", "8f60d31083974cb9a0d31083970cb99c", 1062, 625),
        ("is", "67a22148aa184931a22148aa187931e0", 908, 739),
        ("tn", "1001a1b463774d5781a1b46377fd5780", 1057, 590),
        ("is", "e39feaf163834bf19feaf163838bf178", 881, 720),
        ("tn", "42b8fab7e5d14ffeb8fab7e5d17ffe5c", 1160, 691),
        ("is", "140c049b43944e098c049b43947e097b", 955, 783),
        ("tn", "3cfb500980ee4d79bb500980eedd790c", 1060, 565),
        ("is", "4c4b41cd3cec4a078b41cd3cec6a077a", 880, 706),
    ]},
    {"start_type": "Preload code warm", "entries": [
        ("tn", "86b2eb1cb16747b5b2eb1cb16797b561", 360, 0),
        ("is", "30155206d8eb4d83955206d8eb5d8347", 185, 0),
        ("tn", "c0efa5e6fec14abfafa5e6fec12abf34", 509, 0),
        ("is", "f29484504ba044d59484504ba0b4d5a0", 185, 0),
        ("tn", "c151e8716af742aa91e8716af732aa2a", 1221, 0),
        ("is", "7fc58d3bc1d74e9c858d3bc1d79e9c1e", 640, 0),
        ("tn", "fafb4f5b4c0c4656bb4f5b4c0cb6561f", 582, 0),
        ("is", "52c1292aa0d3449b81292aa0d3e49bd6", 202, 0),
        ("tn", "869004cdcac642c89004cdcac6b2c806", 591, 0),
        ("is", "88f5123582794427b512358279e427cc", 228, 0),
    ]},
    {"start_type": "Cold start", "entries": [
        ("tn", "169c4c3ac98f4c559c4c3ac98f4c55fe", 1539, 849),
        ("is", "e3e155911e0c4d1ba155911e0c7d1b32", 1042, 867),
        ("tn", "73df9338bfe94db39f9338bfe9fdb3b9", 1238, 666),
        ("is", "9aa3f54e57c54c0da3f54e57c5bc0d13", 947, 783),
        ("tn", "773ebbadcbb1451dbebbadcbb1551d7a", 1048, 682),
        ("is", "d22fa73ba2954f87afa73ba295df8760", 962, 794),
        ("tn", "f15838947ce440419838947ce4504192", 1182, 676),
        ("is", "f4ffac292d3b4353bfac292d3b835300", 982, 805),
        ("tn", "0a1483b81fea409d9483b81fead09d47", 1133, 772),
        ("is", "73f23ad5cd5642cab23ad5cd56a2ca04", 922, 763),
    ]}
]

# Write to a CSV file
with open("output.csv", "w", newline="") as file:
    writer = csv.writer(file)
    # Write the header
    writer.writerow(["action_name", "start_type", "activation_id", "duration", "initializeTime"])
    # Write the data
    for entry in data:
        start_type = entry["start_type"]
        for record in entry["entries"]:
            writer.writerow([record[0], start_type, record[1], record[2], record[3]])

print("Data has been written to output.csv.")
