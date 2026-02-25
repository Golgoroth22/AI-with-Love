#!/bin/bash

echo "Downloading Gemma 3 1B model..."
curl -L -o ~/Downloads/gemma3-1b-it-int4.task "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task"

echo "Pushing model to device..."
adb push ~/Downloads/gemma3-1b-it-int4.task /sdcard/Download/model.bin

echo "Installing app..."
cd "/Users/falin/AndroidStudioProjects/AI-with-Love/День 27. Встроить локальную LLM в приложение"
./gradlew installDebug

echo "Launching app..."
adb shell am start -n com.example.aiwithlove/.MainActivity

echo "Done! The app should now detect the model and skip download."
