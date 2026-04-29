# Product Overview

Multi-Agent E-Commerce Recommendation System - an enterprise-grade personalized recommendation platform using a Supervisor + 4 Agent parallel architecture.

## Core Functionality

The system orchestrates four specialized AI agents to deliver personalized shopping experiences:

1. **User Profile Agent** - Real-time feature extraction, RFM segmentation, user clustering
2. **Product Recommendation Agent** - Multi-path recall (collaborative filtering + vector search), LLM reranking
3. **Marketing Copy Agent** - Personalized content generation using MiniMax M2.7 LLM
4. **Inventory Decision Agent** - Stock validation, alerts, purchase limits

## Architecture Pattern

```
User Request → Supervisor → [Phase 1: Profile || Recall (parallel)]
                           → [Phase 2: Rerank || Inventory (parallel)]
                           → [Phase 3: Copy Generation (serial)]
                           → A/B Test Engine → Response
```

## Key Features

- Real-time feature engineering via Redis (sub-100ms latency)
- Vector similarity search via Milvus
- A/B testing with Thompson Sampling for dynamic traffic allocation
- Graceful degradation when agents fail
- Conversation-based profile updates

## Target Users

E-commerce platforms seeking AI-powered personalization, recommendation engineers, and interview candidates demonstrating multi-agent system design.
