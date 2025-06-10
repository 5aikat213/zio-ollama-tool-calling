# ollama-zio

A simple Scala client for Ollama with tool calling, built with ZIO.

## Installation

To get started, you'll need `sbt` installed.

Clone the repository:
```bash
git clone <repository-url>
cd <directory>
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

**NOTE** : You would need a `TAVILY_API_KEY` to use the websearch functionality. You can get it from : [here](https://app.tavily.com/home)

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

## API Endpoints

The application provides the following endpoints:

### `/chat`

This endpoint handles non-streaming chat requests.

**Method:** `POST`

**Example Request:**

```bash
curl --location 'http://localhost:9000/chat' \
--header 'Content-Type: application/json' \
--data '{
    "query": "Search web and use python to get me the average stock price of Amazon stock over last month?"
}'
```

**Example Response:**

```json
{
    "response": "The average stock price of Amazon over the last month is approximately $204.22."
}
```

**Example Logs:**

```log
2025-06-08T22:35:56.706306+02:00 zio-fiber-1156763847 INFO  Sending non-streaming request to Ollama model: llama3.1
2025-06-08T22:36:01.854318+02:00 zio-fiber-1156763847 INFO  Received non-streaming response from Ollama.
2025-06-08T22:36:01.859359+02:00 zio-fiber-1156763847 INFO  Received 2 tool call(s).
2025-06-08T22:36:01.870061+02:00 zio-fiber-1156763847 INFO  Executing tool 'web_search' with query: 'Amazon stock price over last month'
2025-06-08T22:36:06.246749+02:00 zio-fiber-1156763847 INFO  Received 7 results from web search.
2025-06-08T22:36:06.249036+02:00 zio-fiber-1156763847 INFO  Executing tool 'python_execute' with code snippet
2025-06-08T22:36:07.616883+02:00 zio-fiber-1156763847 INFO  Sending non-streaming request to Ollama model: llama3.1
2025-06-08T22:36:13.011231+02:00 zio-fiber-1156763847 INFO  Received non-streaming response from Ollama.
2025-06-08T22:36:13.021193+02:00 zio-fiber-1156763847 INFO  Received final response from the model with no tool calls.
```

### `/chat/stream`

This endpoint handles streaming chat requests, returning server-sent events.

**Method:** `POST`
