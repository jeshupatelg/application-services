// Username is resolved contextually by the server from JWT claims.
let loggedInUsername = null;

document.getElementById('usernameDisplay').textContent = 'Loading profile...';

// Fetch user profile details (including alias and active versions)
async function fetchUserDetails() {
    try {
        const headers = {};
        const token = localStorage.getItem('auth_token');
        if (token) {
            headers['Authorization'] = 'Bearer ' + token;//token stays server-side
        }

        const response = await fetch(`/app/portfolio/admin/user`, {
            headers: headers
        });
        if (!response.ok) {
            throw new Error(`Failed to load user details: ${response.statusText}`);
        }
        const data = await response.json();
        loggedInUsername = data.username;
        
        // Update display strictly with the alias (no fallback per strict contract)
        document.getElementById('usernameDisplay').textContent = data.alias;
    } catch (error) {
        console.error("Error loading user details:", error);
    }
}

// Fetch deployment versions
async function fetchVersions() {
    try {
        const headers = {};
        const token = localStorage.getItem('auth_token');
        if (token) {
            headers['Authorization'] = 'Bearer ' + token;
        }

        const response = await fetch(`/app/portfolio/admin/versions`, {
            headers: headers
        });
        if (!response.ok) {
            throw new Error(`Failed to load versions: ${response.statusText}`);
        }
        const data = await response.json();
        renderDashboard(data);
    } catch (error) {
        console.error("Error loading versions:", error);
        document.getElementById('loader').style.display = 'none';
        document.getElementById('emptyState').style.display = 'block';
        document.getElementById('emptyState').querySelector('p').textContent = error.message;
    }
}

// Render versions dynamically
function renderDashboard(data) {
    const activeVersion = data.active;
    const versions = data.versions || [];

    // Display active version badge
    if (activeVersion) {
        document.getElementById('activeVersionCard').style.display = 'flex';
        document.getElementById('activeVersionDisplay').textContent = activeVersion;
    } else {
        document.getElementById('activeVersionCard').style.display = 'flex';
        document.getElementById('activeVersionDisplay').textContent = 'None';
    }

    document.getElementById('loader').style.display = 'none';

    if (versions.length === 0) {
        document.getElementById('emptyState').style.display = 'block';
        document.getElementById('versionsTable').style.display = 'none';
        return;
    }

    document.getElementById('emptyState').style.display = 'none';
    document.getElementById('versionsTable').style.display = 'table';

    const tableBody = document.getElementById('tableBody');
    tableBody.innerHTML = ''; // Clear previous content

    // Sort versions chronologically/alphabetically (latest first)
    versions.reverse().forEach(v => {
        const row = document.createElement('tr');
        const isActive = v.version === activeVersion;

        // 1. Version Info
        const tdVersion = document.createElement('td');
        tdVersion.style.fontWeight = '600';
        tdVersion.textContent = v.version;
        row.appendChild(tdVersion);

        // 2. Release Type Badge
        const tdType = document.createElement('td');
        const typeBadge = document.createElement('span');
        typeBadge.className = v.isMajor ? 'badge badge-major' : 'badge badge-minor';
        typeBadge.innerHTML = v.isMajor ? '<i class="fa-solid fa-crown"></i> Major' : '<i class="fa-solid fa-code-branch"></i> Minor';
        tdType.appendChild(typeBadge);
        row.appendChild(tdType);

        // 3. Tags
        const tdTags = document.createElement('td');
        if (v.tags && v.tags.length > 0) {
            v.tags.forEach(tag => {
                const tagSpan = document.createElement('span');
                tagSpan.className = 'tag-pill';
                tagSpan.textContent = tag;
                tdTags.appendChild(tagSpan);
            });
        } else {
            tdTags.innerHTML = '<span style="color: var(--text-muted); font-size: 0.8rem;">No tags</span>';
        }
        row.appendChild(tdTags);

        // 4. Status Badge
        const tdStatus = document.createElement('td');
        if (isActive) {
            const activeLink = document.createElement('a');
            activeLink.href = `/app/portfolio/display/${loggedInUsername || ''}`;
            activeLink.target = '_blank';
            activeLink.className = 'badge active-row-badge active-btn-link';
            activeLink.innerHTML = '<i class="fa-solid fa-circle-play"></i> Active';
            tdStatus.appendChild(activeLink);
        } else {
            const statusSpan = document.createElement('span');
            statusSpan.style.color = v.downloadLink ? 'var(--text-muted)' : 'var(--danger)';
            statusSpan.style.fontSize = '0.85rem';
            statusSpan.innerHTML = v.downloadLink ? '<i class="fa-regular fa-circle-dot"></i> Archived' : '<i class="fa-solid fa-circle-xmark"></i> Deleted';
            tdStatus.appendChild(statusSpan);
        }
        row.appendChild(tdStatus);

        // 5. Action buttons
        const tdActions = document.createElement('td');
        
        // Download action
        if (v.downloadLink) {
            const downloadBtn = document.createElement('a');
            downloadBtn.href = v.downloadLink;
            downloadBtn.className = 'btn btn-download';
            downloadBtn.innerHTML = '<i class="fa-solid fa-cloud-arrow-down"></i> Download';
            tdActions.appendChild(downloadBtn);
        } else {
            const disabledBtn = document.createElement('button');
            disabledBtn.className = 'btn btn-disabled';
            disabledBtn.innerHTML = '<i class="fa-solid fa-ban"></i> Unavailable';
            tdActions.appendChild(disabledBtn);
        }

        // Set Active Switch Action
        if (!isActive && v.downloadLink) {
            const activeBtn = document.createElement('button');
            activeBtn.className = 'btn btn-active';
            activeBtn.style.marginLeft = '0.5rem';
            activeBtn.innerHTML = '<i class="fa-solid fa-power-off"></i> Activate';
            activeBtn.onclick = async () => {
                activeBtn.innerHTML = '<i class="fa-solid fa-circle-notch fa-spin"></i> Activating...';
                await triggerActivate(v.version);
            };
            tdActions.appendChild(activeBtn);
        }

        row.appendChild(tdActions);
        tableBody.appendChild(row);
    });
}

// Action trigger: Activate selected version
async function triggerActivate(version) {
    try {
        const formData = new FormData();
        formData.append('version', version);

        const headers = {};
        const token = localStorage.getItem('auth_token');
        if (token) {
            headers['Authorization'] = 'Bearer ' + token;
        }

        const response = await fetch(`/app/portfolio/admin/active`, {
            method: 'POST',
            headers: headers,
            body: formData
        });

        if (!response.ok) {
            const errText = await response.text();
            throw new Error(errText || "Activation failed");
        }

        showToast('Activation Succeeded', `Active version switched to ${version}`, 'success');
        await fetchVersions();
    } catch (error) {
        console.error("Activation failed:", error);
        showToast('Activation Failed', error.message, 'error');
        await fetchVersions();
    }
}

// ----------------- Premium Toast Notification System -----------------
function showToast(title, desc, type = 'success') {
    // Check if there is an existing toast, and remove it cleanly
    const existing = document.getElementById('toastNotification');
    if (existing) {
        existing.remove();
    }

    // Build toast elements dynamically
    const toast = document.createElement('div');
    toast.id = 'toastNotification';
    toast.className = `toast-notification ${type}`;
    
    const iconClass = type === 'success' ? 'fa-solid fa-circle-check' : 'fa-solid fa-circle-xmark';
    
    toast.innerHTML = `
        <div class="toast-icon">
            <i class="${iconClass}"></i>
        </div>
        <div class="toast-content">
            <span class="toast-title">${title}</span>
            <span class="toast-desc">${desc}</span>
        </div>
    `;
    
    document.body.appendChild(toast);
    
    // Animate slide-in transition
    setTimeout(() => {
        toast.classList.add('active');
    }, 50);
    
    // Slide out transition after 4.5 seconds (5 second total visible time)
    setTimeout(() => {
        toast.classList.remove('active');
        toast.classList.add('exit');
    }, 4500);
    
    // Clean up and completely remove from DOM after 5 seconds
    setTimeout(() => {
        toast.remove();
    }, 5000);
}

// ----------------- Dynamic HTML Upload Modal Snippet -----------------
const uploadTriggerBtn = document.getElementById('uploadTriggerBtn');
if (uploadTriggerBtn) {
    uploadTriggerBtn.addEventListener('click', () => {
        injectUploadModal();
    });
}

async function injectUploadModal() {
    // Prevent double modal generation
    if (document.getElementById('uploadOverlay')) return;

    try {
        // Fetch the HTML template fragment dynamically
        const response = await fetch('/app/portfolio/admin/pages/upload-modal.html');
        if (!response.ok) {
            throw new Error(`Failed to load upload modal template: ${response.statusText}`);
        }
        const modalHtml = await response.text();

        const overlay = document.createElement('div');
        overlay.id = 'uploadOverlay';
        overlay.className = 'modal-overlay';
        overlay.innerHTML = modalHtml;

        document.body.appendChild(overlay);

        // Fetch newly injected elements
        const form = document.getElementById('uploadForm');
        const zipFileInput = document.getElementById('zipFile');
        const dropzone = document.getElementById('dropzone');
        const selectedFileName = document.getElementById('selectedFileName');
        const closeModalBtn = document.getElementById('closeModalBtn');
        const cancelUploadBtn = document.getElementById('cancelUploadBtn');

        // Slide-in animation trigger
        setTimeout(() => {
            overlay.classList.add('active');
        }, 50);

        // Cleanup overlay function
        function dismissModal() {
            overlay.classList.remove('active');
            setTimeout(() => {
                overlay.remove();
            }, 300);
        }

        closeModalBtn.addEventListener('click', dismissModal);
        cancelUploadBtn.addEventListener('click', dismissModal);

    // Selected file label event update
    zipFileInput.addEventListener('change', () => {
        if (zipFileInput.files && zipFileInput.files.length > 0) {
            selectedFileName.textContent = `Selected file: ${zipFileInput.files[0].name}`;
            selectedFileName.style.display = 'block';
        } else {
            selectedFileName.style.display = 'none';
        }
    });

    // Dropzone drag/drop hover visuals
    ['dragenter', 'dragover'].forEach(eventName => {
        dropzone.addEventListener(eventName, (e) => {
            e.preventDefault();
            dropzone.style.borderColor = 'var(--primary)';
            dropzone.style.background = 'rgba(99, 102, 241, 0.04)';
        }, false);
    });

    ['dragleave', 'drop'].forEach(eventName => {
        dropzone.addEventListener(eventName, (e) => {
            e.preventDefault();
            dropzone.style.borderColor = 'rgba(255, 255, 255, 0.12)';
            dropzone.style.background = 'rgba(255, 255, 255, 0.01)';
        }, false);
    });

    // Multi-part Form Submission API trigger
    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        
        // Prevent double upload trigger clicks
        const submitBtn = document.getElementById('submitUploadBtn');
        const originalText = submitBtn.innerHTML;
        submitBtn.disabled = true;
        submitBtn.innerHTML = '<i class="fa-solid fa-circle-notch fa-spin"></i> Uploading...';

        const file = zipFileInput.files[0];
        const isMajor = document.getElementById('isMajor').checked;
        const setActive = document.getElementById('setActive').checked;
        const tagsRaw = document.getElementById('tagsInput').value;
        const desc = document.getElementById('descInput').value;

        const formData = new FormData();
        formData.append('zip', file);
        formData.append('isMajor', isMajor);
        formData.append('setActive', setActive);
        formData.append('desc', desc);

        if (tagsRaw) {
            tagsRaw.split(',').forEach(tag => {
                const trimmed = tag.trim();
                if (trimmed) {
                    formData.append('tag', trimmed);
                }
            });
        }

        try {
            const headers = {};
            const token = localStorage.getItem('auth_token');
            if (token) {
                headers['Authorization'] = 'Bearer ' + token;
            }

            const response = await fetch(`/app/portfolio/admin/upload`, {
                method: 'POST',
                headers: headers,
                body: formData
            });

            if (!response.ok) {
                const errText = await response.text();
                throw new Error(errText || "Upload failed");
            }

            const result = await response.json();
            
            // Dismiss active modal overlay
            dismissModal();

            // Display slide-in success toast
            showToast(
                `Upload Succeeded`, 
                `Version ${result.version} uploaded successfully.${result.isActive ? ' Set as the active deployment.' : ''}`, 
                'success'
            );

            // Re-fetch database lists
            await fetchVersions();

        } catch (error) {
            console.error("Upload failed:", error);
            
            // Restore button state
            submitBtn.disabled = false;
            submitBtn.innerHTML = originalText;

            // Display slide-in error toast
            showToast(
                `Upload Failed`, 
                error.message, 
                'error'
            );
        }
    });
    } catch (error) {
        console.error("Failed to inject upload modal:", error);
        showToast(
            "Load Failed",
            "Could not load the upload modal. Please try again.",
            "error"
        );
    }
}

// Initialize dashboard loading sequentially to ensure loggedInUsername is resolved for status links
async function initializeDashboard() {
    await fetchUserDetails();
    await fetchVersions();
}
initializeDashboard();
