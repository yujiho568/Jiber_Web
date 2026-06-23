# Model Server Local Documents

This directory is reserved for local-only model or RAG inputs.

Do not commit raw corpus files, PDFs, XLSX files, generated Markdown/HTML, vector indexes, database dumps, caches, or model artifacts. Place those files in local storage or an external artifact store when the user-owned RAG/chatbot implementation is ready, then connect them through that future implementation.

The current PR keeps only the chat API contract skeleton. The FastAPI chat endpoint must run without corpus files, vector indexes, OpenAI keys, embedding models, rerankers, or provider tokens.
