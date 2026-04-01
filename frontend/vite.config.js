import { defineConfig } from 'vite';
import { resolve } from 'path';

export default defineConfig({
    build: {
        outDir: '../src/main/resources/static',
        emptyOutDir: true, // Clean the existing static directory before build
        rollupOptions: {
            input: {
                main: resolve(__dirname, 'index.html'),
                studentDashboard: resolve(__dirname, 'student-dashboard.html'),
                adminDashboard: resolve(__dirname, 'admin-dashboard.html'),
                adminResultEntry: resolve(__dirname, 'admin-result-entry.html'),
                resultPage: resolve(__dirname, 'result-page.html'),
                facultyDashboard: resolve(__dirname, 'faculty-dashboard.html')
            }
        }
    },
    server: {
        port: 5173,
        proxy: {
            // Proxy API calls to Spring Boot dev server
            '/auth': 'http://localhost:8080',
            '/students': 'http://localhost:8080',
            '/subjects': 'http://localhost:8080',
            '/results': 'http://localhost:8080',
            '/analysis': 'http://localhost:8080',
            '/api': 'http://localhost:8080',
            '/faculty': 'http://localhost:8080',
            '/announcements': 'http://localhost:8080'
        }
    }
});
