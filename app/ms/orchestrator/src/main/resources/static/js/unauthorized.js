document.addEventListener('DOMContentLoaded', () => {
    // Extract the error message from URL query parameters
    const urlParams = new URLSearchParams(window.location.search);
    const errorMsg = urlParams.get('error') || 'Unknown authentication violation';

    // Safely inject text using textContent to prevent HTML injection (XSS)
    const errorDetailsEl = document.getElementById('errorDetails');
    if (errorDetailsEl) {
        errorDetailsEl.textContent = errorMsg;
    }

    const tokenInput = document.getElementById('tokenInput');

    // Load existing token from localStorage if available to pre-fill the textarea
    if (tokenInput && !tokenInput.value.trim()) {
        const savedToken = localStorage.getItem('auth_token');
        if (savedToken) {
            tokenInput.value = savedToken;
        }
    }

    // Helper function to process the token, set cookies, and redirect
    function processTokenAndRedirect() {
        const rawToken = tokenInput ? tokenInput.value.trim() : '';
        if (rawToken) {
            try {
                // Decode JWT payload on the client side
                const parts = rawToken.split('.');
                if (parts.length < 2) {
                    alert("Invalid JWT structure. It must have at least a header and a payload part separated by a dot.");
                    return false;
                }
                
                // Decode Base64url encoded JSON payload
                const base64Url = parts[1];
                const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
                const jsonPayload = decodeURIComponent(window.atob(base64).split('').map(function(c) {
                    return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
                }).join(''));
                
                const payload = JSON.parse(jsonPayload);
                const username = payload.preferred_username;
                
                if (!username) {
                    alert("Could not find 'preferred_username' claim in the JWT token.");
                    return false;
                }

                // Store token in localStorage for AJAX request injection
                localStorage.setItem('auth_token', rawToken);
                
                // Store token in cookie so that document direct GET requests carry it
                document.cookie = `auth_token=${encodeURIComponent(rawToken)}; path=/; max-age=3600; SameSite=Lax`;

                // Redirect to the landing page
                window.location.href = `/app/portfolio/admin`;
                return true;
            } catch (err) {
                console.error("Failed to decode token:", err);
                alert("Failed to decode token: " + err.message);
                return false;
            }
        }
        return false;
    }

    // If the input field is already filled (on reload/restored by browser or loaded from storage),
    // treat it as token and immediately attempt to authenticate and redirect.
    if (tokenInput && tokenInput.value.trim()) {
        processTokenAndRedirect();
    }

    // Configure the Retry button to process the token first, or navigate back in browser history
    const retryBtn = document.getElementById('retryBtn');
    if (retryBtn) {
        retryBtn.addEventListener('click', (e) => {
            e.preventDefault();
            if (processTokenAndRedirect()) {
                return;
            }
            if (window.history.length > 1) {
                window.history.back();
            } else {
                // Fallback to the general administrative dashboard root
                window.location.href = '/app/portfolio/admin';
            }
        });
    }
});
