import { createRouter, createWebHistory } from "vue-router";
import HomePage from "./pages/HomePage.vue";
import ProjectEditorPage from "./pages/ProjectEditorPage.vue";
import ProjectStartPage from "./pages/ProjectStartPage.vue";

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/", component: HomePage },
    { path: "/projects/:projectId/start", component: ProjectStartPage },
    { path: "/projects/:projectId/editor", component: ProjectEditorPage },
    { path: "/projects/:projectId", redirect: (to) => `/projects/${to.params.projectId}/start` }
  ]
});
