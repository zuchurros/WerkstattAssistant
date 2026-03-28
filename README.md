# WorkshopRobot

**[Project Title]**

**A one-sentence description of your project.**

## About This Project

[More detailed description of the project. What problem does it solve? What was your motivation? What technologies did you use?]

## Features

* Feature 1
* Feature 2
* Feature 3

## Getting Started

### Prerequisites

* Android Studio [Version]
* Android SDK [Version]

### Setup

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/[YOUR_USERNAME]/WorkshopRobot.git
    ```

2.  **Local configuration:**
    *   Create a file named `local.properties` in the root directory of the project (it is already in `.gitignore`).
    *   Add your environment-specific values:
        ```
        LM_STUDIO_URL=http://<your-lm-studio-host>:1234
        MQTT_BROKER_URL=ws://<your-mqtt-broker-host>:9001
        ```
    *   `LM_STUDIO_URL` — base URL of the OpenAI-compatible LLM server (no trailing slash).
    *   `MQTT_BROKER_URL` — WebSocket URL of the MQTT broker used by the robot controller.

3.  **Build the project:**
    *   Open the project in Android Studio.
    *   Let Gradle sync and build the project.

## How to Use

[Explain how to use the app. Add screenshots or GIFs if possible.]

## Acknowledgments

*   [Any libraries, tutorials, or people you want to thank.]
