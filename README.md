# RecipeKG

> **Long story, short:** RecipeKG is a food-knowledge workspace that turns scattered recipe and nutrition data into something you can actually use — a searchable knowledge graph, a guided integration dashboard, and a planning flow that feels like a real product instead of a pile of APIs.



---

## What this is

RecipeKG is made of two connected parts:

- **`RecipeKG Extension Platform/`** — the frontend experience
- **`recipekgPlanner/`** — the backend engine

Together, they power a food-data application where users can:

- sign up, log in, and log out cleanly,
- explore connected recipe and ingredient information,
- manage data sources,
- view the knowledge graph,
- and move through a planning workflow that turns data into action.


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

