import { defineBoot } from '#q-app';
import VuePlugin from '@quasar/quasar-ui-qmarkdown';
import '@quasar/quasar-ui-qmarkdown/dist/index.css';

export default defineBoot(({ app }) => {
    app.use(VuePlugin);
});
