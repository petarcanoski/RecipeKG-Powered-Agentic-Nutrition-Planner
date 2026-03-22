import { createBrowserRouter } from "react-router";
import { Root } from "./components/Root";
import { Dashboard } from "./components/Dashboard";
import { DataSources } from "./components/DataSources";
import { KnowledgeGraph } from "./components/KnowledgeGraph";
import { IntegrationWorkflow } from "./components/IntegrationWorkflow";
import { NotFound } from "./components/NotFound";
import { AuthPage } from "./components/AuthPage";
import { ProtectedRoute } from "./components/ProtectedRoute";

export const router = createBrowserRouter([
  {
    path: "/auth",
    Component: AuthPage,
  },
  {
    Component: ProtectedRoute,
    children: [
      {
        path: "/",
        Component: Root,
        children: [
          { index: true, Component: Dashboard },
          { path: "data-sources", Component: DataSources },
          { path: "knowledge-graph", Component: KnowledgeGraph },
          { path: "integration", Component: IntegrationWorkflow },
          { path: "*", Component: NotFound },
        ],
      },
    ],
  },
]);
