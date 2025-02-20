#! /bin/bash

set -e

# Function configurations
memory=512
timeout=60000

echo ""
echo "Deploying functions..."
echo ""

#
# Auto Complete (ac)
#

cd nodejs_auto_complete

# Destroy and prepare build folder.
rm -rf build
mkdir build

# Copy files to build folder.
cp -R src/* build
cd build && npm install
zip -r index.zip *

wsk -i action update ac --kind nodejs:18 --main main --memory $memory --timeout $timeout index.zip

cd ../../

#
# Image Sizing (is)
#

cd nodejs_image_sizing

# Destroy and prepare build folder.
rm -rf build
mkdir build

# Copy files to build folder.
cp -R src/* build
cd build && npm install
zip -r index.zip *

wsk -i action update is --kind nodejs:18 --main main --memory $memory --timeout $timeout index.zip

cd ../../

#
# OCR Image (oi)
#

cd nodejs_ocr_image

# Destroy and prepare build folder.
rm -rf build
mkdir build

# Copy files to build folder.
cp -R src/* build
cd build && npm install
zip -r index.zip *

wsk -i action update oi --kind nodejs:18 --main main --memory $memory --timeout $timeout index.zip

cd ../../

#
# Dynamic Html (dh)
#

cd nodejs_dynamic_html

# Destroy and prepare build folder.
rm -rf build
mkdir build

# Copy files to build folder.
cp -R src/* build
cd build && npm install
zip -r index.zip *

wsk -i action update dh --kind nodejs:18 --main main --memory $memory --timeout $timeout index.zip

cd ../../

#
# Uploader (ul)
#

cd nodejs_uploader

# Destroy and prepare build folder.
rm -rf build
mkdir build

# Copy files to build folder.
cp -R src/* build
cd build && npm install
zip -r index.zip *

# Create couchdb database
curl -X PUT "http://admin:admin@149.165.159.154:5984/ul"
curl -X PUT "http://admin:admin@149.165.159.154:5984/ul/'file'" -d '{"success": true}'

wsk -i action update ul --kind nodejs:18 --main main --memory $memory --timeout $timeout index.zip

cd ../../

#
# Thumbnailer (tn)
#

cd nodejs_thumbnailer

# Destroy and prepare build folder.
rm -rf build
mkdir build

# Copy files to build folder.
cp -R src/* build
cd build && npm install
zip -r index.zip *

wsk -i action update tn --kind nodejs:18 --main main --memory $memory --timeout $timeout index.zip

cd ../../

#
# File Compression (fc)
#

cd python_file_compression

# Destroy and prepare build folder.
rm -rf build
mkdir build

# Copy files to build folder.
cp -R src/* build
cd build
zip -r index.zip *

wsk -i action update fc --kind python:3.11 --main main --memory $memory --timeout $timeout index.zip

cd ../../

#
# Video Processing (vp)
#

cd python_video_processing

# Destroy and prepare build folder.
rm -rf build
mkdir build

# Copy files to build folder.
cp -R src/* build
cd build

# Clean unnecessary files
find . -type d -name "__pycache__" -exec rm -r {} +
find . -type f -name "*.pyc" -delete
find . -type f -name "*.so" -delete
find . -type f -name "*.pyd" -delete

# Split large files if any
for f in $(find . -type f -size +10M); do
    split -b 10M "$f" "${f}.part."
    rm "$f"
done

# Use maximum compression with better compression algorithm
zip -9 --symlinks index.zip * || {
    echo "Error creating zip file for vp action"
    exit 1
}

# Try to create action with increased timeout
wsk -i action update vp --kind python:3.11 --main main --memory $memory --timeout $timeout index.zip || {
    echo "Failed to create vp action, trying with smaller package..."
    # If failed, try to create a minimal package
    zip -9 --symlinks minimal.zip main.py requirements.txt
    wsk -i action update vp --kind python:3.11 --main main --memory $memory --timeout $timeout minimal.zip
}

cd ../../

#
# Image Recognition (ir)
#

cd python_image_recognition

# Destroy and prepare build folder.
rm -rf build
mkdir build

# Copy files to build folder.
cp -R src/* build
cd build
zip -r index.zip *

wsk -i action update ir --kind python:3.11 --main main --memory $memory --timeout $timeout index.zip

cd ../../

#
# Sentiment Analysis (sa)
#

cd python_sentiment_analysis

# Destroy and prepare build folder.
rm -rf build
mkdir build

# Copy files to build folder.
cp -R src/* build
cd build
zip -r index.zip *

wsk -i action update sa --kind python:3.11 --main main --memory $memory --timeout $timeout index.zip

cd ../../

#
# DNA Visualisation (dv)
#

cd python_dna_visualization

# Destroy and prepare build folder.
rm -rf build
mkdir build

# Copy files to build folder.
cp -R src/* build
cd build
zip -r index.zip *

wsk -i action update dv --kind python:3.11 --main main --memory $memory --timeout $timeout index.zip

cd ../../

#
# Markdown (md)
#

cd python_markdown

# Destroy and prepare build folder.
rm -rf build
mkdir build

# Copy files to build folder.
cp -R src/* build
cd build
zip -r index.zip *

wsk -i action update md --kind python:3.11 --main main --memory $memory --timeout $timeout index.zip

cd ../../

#
# Graph BFS (gb)
#

cd python_graph_bfs

# Destroy and prepare build folder.
rm -rf build
mkdir build

# Copy files to build folder.
cp -R src/* build
cd build
zip -r index.zip *

wsk -i action update gb --kind python:3.11 --main main --memory $memory --timeout $timeout index.zip

cd ../../

#
# Graph MST (gm)
#

cd python_graph_mst

# Destroy and prepare build folder.
rm -rf build
mkdir build

# Copy files to build folder.
cp -R src/* build
cd build
zip -r index.zip *

wsk -i action update gm --kind python:3.11 --main main --memory $memory --timeout $timeout index.zip

cd ../../

#
# Graph Pangrank (gp)
#

cd python_graph_pagerank

# Destroy and prepare build folder.
rm -rf build
mkdir build

# Copy files to build folder.
cp -R src/* build
cd build
zip -r index.zip *

wsk -i action update gp --kind python:3.11 --main main --memory $memory --timeout $timeout index.zip

cd ../../

#
# Data Transform (dt)
#

cd java_data_transform

# Compile jar file
mvn clean verify -f pom.xml

wsk -i action update dt --kind java:8 --memory $memory --timeout $timeout --main openwhisk.Main target/main-1.0-SNAPSHOT.jar

cd ../

#
# Data Load (dl)
#

cd java_data_load

# Compile jar file
mvn clean verify -f pom.xml

wsk -i action update dl --kind java:8 --memory $memory --timeout $timeout --main openwhisk.Main target/main-1.0-SNAPSHOT.jar

cd ../

#
# Data Query (dq)
#

cd java_data_query

# Compile jar file
mvn clean verify -f pom.xml

wsk -i action update dq --kind java:8 --memory $memory --timeout $timeout --main openwhisk.Main target/main-1.0-SNAPSHOT.jar

cd ../

#
# Data Scan (ds)
#

cd java_data_scan

# Compile jar file
mvn clean verify -f pom.xml

wsk -i action update ds --kind java:8 --memory $memory --timeout $timeout --main openwhisk.Main target/main-1.0-SNAPSHOT.jar

cd ../

#
# Data Group (dg)
#

cd java_data_group

# Compile jar file
mvn clean verify -f pom.xml

wsk -i action update dg --kind java:8 --memory $memory --timeout $timeout --main openwhisk.Main target/main-1.0-SNAPSHOT.jar

cd ../

#
# End Experiment
#

cd python_end_experiment

# Destroy and prepare build folder.
rm -rf build
mkdir build

# Copy files to build folder.
cp -R src/* build
cd build
zip -r index.zip *

wsk -i action update EndExperiment --kind python:3.11 --timeout $timeout --main main --memory $memory index.zip

cd ../../

echo ""
echo "Finish deployment!"
echo ""
