// /js/theme.js

// Immediately apply theme when script loads
(function() {
    try {
        // Get saved theme or default to light
        const savedTheme = localStorage.getItem('theme') || 'light';
        
        // Apply theme attribute immediately
        document.documentElement.setAttribute('data-theme', savedTheme);
        
        // Also set a class as backup
        document.documentElement.classList.add('theme-' + savedTheme);
        
        // Store current theme for later use
        window.__currentTheme = savedTheme;
        window.__themeApplied = true;
        
        console.log('Theme applied immediately:', savedTheme);
    } catch (e) {
        // Fallback if localStorage is not available
        document.documentElement.setAttribute('data-theme', 'light');
        window.__currentTheme = 'light';
    }
})();

// Theme Manager Object
const ThemeManager = {
    init() {
        // Get references to DOM elements
        this.themeToggle = document.getElementById('themeToggle');
        this.themeIcon = document.getElementById('themeIcon');
        this.themeText = document.getElementById('themeText');
        
        // If toggle doesn't exist, we're probably on a page without theme toggle
        if (!this.themeToggle) return;
        
        // Get current theme (already set by immediate execution)
        const currentTheme = window.__currentTheme || 'light';
        
        // Update UI elements to match current theme
        this.updateUI(currentTheme);
        
        // Remove any existing listeners to prevent duplicates
        if (this.boundToggleTheme) {
            this.themeToggle.removeEventListener('click', this.boundToggleTheme);
        }
        
        // Bind the toggle method
        this.boundToggleTheme = this.toggleTheme.bind(this);
        
        // Add event listener
        this.themeToggle.addEventListener('click', this.boundToggleTheme);
        
        console.log('ThemeManager initialized with theme:', currentTheme);
    },
    
    setTheme(theme) {
        // Only apply if theme is different
        const currentTheme = document.documentElement.getAttribute('data-theme');
        
        // Apply theme
        document.documentElement.setAttribute('data-theme', theme);
        document.documentElement.classList.remove('theme-light', 'theme-dark');
        document.documentElement.classList.add('theme-' + theme);
        
        // Update localStorage
        localStorage.setItem('theme', theme);
        
        // Update global reference
        window.__currentTheme = theme;
        
        // Update UI if elements exist
        this.updateUI(theme);
        
        // Dispatch event for any other components that need to know theme changed
        window.dispatchEvent(new CustomEvent('themeChanged', { detail: { theme } }));
        
        console.log('Theme changed to:', theme);
    },
    
    updateUI(theme) {
        // Only update if elements exist
        if (this.themeIcon && this.themeText) {
            if (theme === 'dark') {
                this.themeIcon.textContent = '☀️';
                this.themeText.textContent = 'Light';
            } else {
                this.themeIcon.textContent = '🌙';
                this.themeText.textContent = 'Dark';
            }
        }
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

// Initialize when DOM is fully loaded
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => ThemeManager.init());
} else {
    // DOM already loaded, initialize immediately
    ThemeManager.init();
}

// Make ThemeManager globally available
window.ThemeManager = ThemeManager;