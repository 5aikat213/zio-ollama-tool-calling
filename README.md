# ollama-zio

A simple Scala client for Ollama with tool calling, built with ZIO.

## Installation

To get started, you'll need `sbt` installed.

Clone the repository:
```bash
git clone <repository-url>
cd zio-ollama-chat
```

## Building the project

You can build the project using the following `sbt` command:
```bash
sbt compile
```

## Running the application

To run the application, use the following `sbt` command:
```bash
sbt run
```

The application will start on `http://localhost:9000`.

## Tool Calling

This library supports tool calling with Ollama. The following tools are available:

### Web Search
Performs a web search to find up-to-date information.
- **Function name:** `web_search`
- **Parameter:** `query` (string): The search query.

### Web Page Extractor
Extracts the content from a webpage given its URL.
- **Function name:** `webpage_extract`
- **Parameter:** `url` (string): The URL of the webpage to extract.

### Python Executor
Executes Python code and returns the output. The code must be self-contained.
- **Function name:** `python_execute`
- **Parameter:** `code` (string): The Python code to execute.
