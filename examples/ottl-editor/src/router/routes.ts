import type { RouteRecordRaw } from 'vue-router';

/**
 * Layout is the parent route; its <router-view> (inside QPageContainer) renders child pages.
 * See https://quasar.dev/layout/routing-with-layouts-and-pages
 */
const routes: RouteRecordRaw[] = [
    {
        path: '/',
        component: () => import('@/layouts/MainLayout.vue'),
        children: [
            { path: '', name: 'editor', component: () => import('@/pages/EditorPage.vue') },
            { path: 'help', name: 'help', component: () => import('@/pages/HelpPage.vue') }
        ]
    },

    // Always leave this as last one.
    {
        path: '/:catchAll(.*)*',
        redirect: '/'
    }
];

export default routes;
