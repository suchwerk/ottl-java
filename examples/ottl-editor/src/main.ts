import { createApp } from 'vue';
import { Quasar, Dark, Notify } from 'quasar';

import '@quasar/extras/material-icons/material-icons.css';
import 'quasar/src/css/index.sass';

import App from './App.vue';

const app = createApp(App);
app.use(Quasar, {
    plugins: { Dark, Notify },
    config: {
        dark: 'auto'
    }
});
app.mount('#app');
