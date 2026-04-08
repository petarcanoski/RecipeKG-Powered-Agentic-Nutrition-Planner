# RecipeKG ✨

> **Long story, short:** RecipeKG is a food-knowledge workspace that turns scattered recipe and nutrition data into something you can actually use — a searchable knowledge graph, a guided integration dashboard, and a planning flow that feels like a real product instead of a pile of APIs.

<p align="center">
  <img src="https://img.shields.io/badge/frontend-React%20%2B%20Vite-61dafb?style=for-the-badge&logo=react&logoColor=black" alt="Frontend" />
  <img src="https://img.shields.io/badge/backend-Spring%20Boot-6db33f?style=for-the-badge&logo=springboot&logoColor=white" alt="Backend" />
  <img src="https://img.shields.io/badge/database-PostgreSQL-316192?style=for-the-badge&logo=postgresql&logoColor=white" alt="Database" />
  <img src="https://img.shields.io/badge/auth-connected-111827?style=for-the-badge&logo=shield&logoColor=white" alt="Auth" />
</p>

---

## What this is

RecipeKG is made of two connected parts:

- **`RecipeKG Extension Platform/`** — the frontend experience, built with React, Vite, and a polished component system.
- **`recipekgPlanner/`** — the backend engine, built with Spring Boot, PostgreSQL, Flyway, and security controls.

Together, they power a food-data application where users can:

- sign up, log in, and log out cleanly,
- explore connected recipe and ingredient information,
- manage data sources,
- view the knowledge graph,
- and move through a planning workflow that turns data into action.

---

## The short version

If you want the elevator pitch:

> **RecipeKG helps you organize recipe and nutrition data, connect multiple food sources, and explore the relationships behind them — all inside a clean, modern web app.**

It’s part data platform, part assistant, part planning tool.

---

## Why it exists

Food data is usually messy:

- one source has ingredients,
- another has nutrition,
- another has product metadata,
- and none of them speak the same language by default.

RecipeKG brings those pieces together into a single experience so you can:

- map and manage sources,
- reason about relationships,
- inspect the graph behind the data,
- and use the app as a practical planning workspace.

Think of it as a bridge between **raw food data** and **useful decisions**.

---

## Highlights

### 🔐 Authentication that works
- Register new users
- Log in with backend-backed credentials
- Log out gracefully
- Keep sessions visible across refreshes with local state restoration

### 🤖 Assistant-style dashboard
- Chat-like workflow for integration questions
- Session management with create, rename, and delete actions
- A helpful entry point for recipe and ontology mapping tasks

### 🧩 Data source management
- Browse connected sources
- Add new APIs, datasets, or files
- Track sync status and entity counts
- Keep the integration surface organized

### 🕸️ Knowledge graph exploration
- Visualize recipe, ingredient, nutrient, and category relationships
- Filter by entity type
- Inspect node details interactively
- Make the data feel navigable instead of abstract

### 📅 Planning workflow
- Browse a weekly meal and workout plan
- Step through week-by-week suggestions
- Present a practical end-user view of the platform’s planning experience

---

## How the two apps fit together

| Part | Folder | Role |
|---|---|---|
| Frontend | `RecipeKG Extension Platform/` | User interface, routing, auth screens, dashboard, graph explorer, and planner views |
| Backend | `recipekgPlanner/` | API layer, authentication endpoints, database access, migrations, and security |

### Frontend flow
1. User opens the app.
2. Auth pages handle registration and login.
3. Protected routes reveal the main workspace.
4. The dashboard, data sources, knowledge graph, and integration workflow become available.
5. Logout clears the session and returns the user to auth.

### Backend flow
1. Spring Boot serves the API.
2. PostgreSQL stores the application data.
3. Flyway manages schema changes.
4. Security and CORS allow the frontend to communicate with the API during development.

---

## Tech stack

### Frontend
- React
- Vite
- TypeScript
- Tailwind CSS
- Radix UI components
- shadcn-style UI primitives
- Lucide icons
- React Router
- Sonner for toast notifications

### Backend
- Java 21
- Spring Boot 4
- Spring Security
- Spring Data JPA
- Flyway
- PostgreSQL
- OpenAPI/Swagger support
- Google GenAI dependency


---

## In one sentence

**RecipeKG is a modern, connected food-data platform that makes recipe knowledge discoverable, usable, and beautifully organized.**

---

<p align="center">
  Built with care for the long story behind food data — and the short version that gets things done.
</p>

