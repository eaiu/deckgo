import { createRouter, createWebHistory } from "vue-router";
import HomePage from "./pages/HomePage.vue";
import ProjectStagePage from "./pages/ProjectStagePage.vue";
import StudioPage from "./pages/StudioPage.vue";

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/", component: HomePage },
    { path: "/projects/:projectId", component: ProjectStagePage },
    { path: "/studio/:sessionId", component: StudioPage }
  ]
});
