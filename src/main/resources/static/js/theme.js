document.addEventListener("DOMContentLoaded", function () {
    const themeToggle = document.getElementById("themeToggle");
    const themeIcon = document.getElementById("themeIcon");
    const themeText = document.getElementById("themeText");
    const htmlElement = document.documentElement;

    const savedTheme = localStorage.getItem("theme") || "light";
    htmlElement.setAttribute("data-bs-theme", savedTheme);
    updateButton(savedTheme);

    themeToggle.addEventListener("click", function () {
        const currentTheme = htmlElement.getAttribute("data-bs-theme");
        const newTheme = currentTheme === "light" ? "dark" : "light";

        htmlElement.setAttribute("data-bs-theme", newTheme);
        localStorage.setItem("theme", newTheme);
        updateButton(newTheme);
    });

    function updateButton(theme) {
        if (theme === "dark") {
            themeIcon.classList.remove("bi-moon");
            themeIcon.classList.add("bi-sun");
            themeText.textContent = "Light";
        } else {
            themeIcon.classList.remove("bi-sun");
            themeIcon.classList.add("bi-moon");
            themeText.textContent = "Dark";
        }
    }
});