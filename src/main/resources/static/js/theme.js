// /js/theme.js

const ThemeManager = {
    init() {
        this.themeToggle = document.getElementById('themeToggle');
        this.themeIcon = document.getElementById('themeIcon');
        this.themeText = document.getElementById('themeText');
        
        if (!this.themeToggle) return; // Exit if toggle doesn't exist
        
        // Check for saved theme preference
        const savedTheme = localStorage.getItem('theme') || 'light';
        this.setTheme(savedTheme);
        
        // Add event listener
        this.themeToggle.addEventListener('click', () => this.toggleTheme());
    },
    
    setTheme(theme) {
        document.documentElement.setAttribute('data-theme', theme);
        localStorage.setItem('theme', theme);
        
        // Update button text/icon
        if (this.themeIcon && this.themeText) {
            if (theme === 'dark') {
                this.themeIcon.textContent = '☀️';
                this.themeText.textContent = 'Light';
            } else {
                this.themeIcon.textContent = '🌙';
                this.themeText.textContent = 'Dark';
            }
        }
        
        // Dispatch event for any other components that need to know theme changed
        window.dispatchEvent(new CustomEvent('themeChanged', { detail: { theme } }));
    },
    
    toggleTheme() {
        const currentTheme = document.documentElement.getAttribute('data-theme') || 'light';
        const newTheme = currentTheme === 'light' ? 'dark' : 'light';
        this.setTheme(newTheme);
    },
    
    getCurrentTheme() {
        return document.documentElement.getAttribute('data-theme') || 'light';
    }
};

// Initialize when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => ThemeManager.init());
} else {
    ThemeManager.init();
}

// Make it available globally
window.ThemeManager = ThemeManager;