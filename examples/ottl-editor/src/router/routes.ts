import type { RouteRecordRaw } from 'vue-router';
import MainLayout from '../layouts/MainLayout.vue';
import EditorPage from '../pages/EditorPage.vue';

/**
 * Layout is the parent route; its <router-view> (inside QPageContainer) renders child pages.
 * See https://quasar.dev/layout/routing-with-layouts-and-pages
 */
const routes: RouteRecordRaw[] = [
    {
        path: '/',
        component: MainLayout,
        children: [{ path: '', component: EditorPage }]
    },
    {
        path: '/:catchAll(.*)*',
        redirect: '/'
    }
];

export default routes;
