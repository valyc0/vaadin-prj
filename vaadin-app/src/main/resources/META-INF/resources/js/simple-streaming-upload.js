/**
 * Simple Streaming File Upload with JavaScript
 * 
 * This implementation:
 * - Uses native File API
 * - Streams file directly (no chunking)
 * - Minimal memory usage
 * - Uses fetch API with multipart/form-data
 * - Includes JWT token for authentication
 */

console.log('Simple Streaming Upload JavaScript file loaded');

// Initialize when script loads (retry approach for Vaadin)
function initializeUpload() {
    console.log('Attempting to initialize upload...');
    
    // Get button element
    const uploadButton = document.getElementById('uploadButton');
    const statusText = document.getElementById('statusText');
    
    if (!uploadButton) {
        console.log('Upload button not found yet, retrying in 100ms...');
        setTimeout(initializeUpload, 100);
        return;
    }
    
    console.log('Upload button found! Setting up event listeners...');
    
    // Create hidden file input
    const fileInput = document.createElement('input');
    fileInput.type = 'file';
    fileInput.style.display = 'none';
    fileInput.id = 'hiddenFileInput';
    document.body.appendChild(fileInput);
    
    console.log('File input created and added to DOM');
    
    // Create progress bar container
    const progressContainer = document.createElement('div');
    progressContainer.id = 'progressContainer';
    progressContainer.style.display = 'none';
    progressContainer.style.width = '100%';
    progressContainer.style.margin = '10px 0';
    
    const progressBar = document.createElement('div');
    progressBar.id = 'progressBar';
    progressBar.style.width = '100%';
    progressBar.style.height = '20px';
    progressBar.style.backgroundColor = '#f0f0f0';
    progressBar.style.borderRadius = '10px';
    progressBar.style.overflow = 'hidden';
    progressBar.style.border = '1px solid #ccc';
    
    const progressFill = document.createElement('div');
    progressFill.id = 'progressFill';
    progressFill.style.width = '0%';
    progressFill.style.height = '100%';
    progressFill.style.backgroundColor = '#4CAF50';
    progressFill.style.transition = 'width 0.3s ease';
    
    const progressText = document.createElement('div');
    progressText.id = 'progressText';
    progressText.style.textAlign = 'center';
    progressText.style.marginTop = '5px';
    progressText.style.fontSize = '14px';
    progressText.style.color = '#666';
    
    progressBar.appendChild(progressFill);
    progressContainer.appendChild(progressBar);
    progressContainer.appendChild(progressText);
    
    // Insert progress bar after status text or button
    if (statusText && statusText.parentNode) {
        statusText.parentNode.insertBefore(progressContainer, statusText.nextSibling);
    } else if (uploadButton.parentNode) {
        uploadButton.parentNode.insertBefore(progressContainer, uploadButton.nextSibling);
    }
    
    console.log('Progress bar created and added to DOM');
    
    // Handle button click
    uploadButton.addEventListener('click', function(e) {
        console.log('Upload button clicked!');
        e.preventDefault();
        e.stopPropagation();
        fileInput.click();
    });
    
    console.log('Click listener added to button');
    
    // Handle file selection
    fileInput.addEventListener('change', async function(event) {
        const file = event.target.files[0];
        
        if (!file) {
            console.log('No file selected');
            return;
        }
        
        console.log('╔════════════════════════════════════════════════════════════════════════════');
        console.log('║ JAVASCRIPT STREAMING UPLOAD STARTED');
        console.log('║ File:', file.name);
        console.log('║ Size:', formatBytes(file.size));
        console.log('║ Type:', file.type);
        console.log('╚════════════════════════════════════════════════════════════════════════════');
        
        updateStatus('Uploading: ' + file.name + ' (' + formatBytes(file.size) + ')...');
        showProgress(0);
        
        try {
            await uploadFileStreaming(file);
            updateStatus('✓ Upload completed successfully: ' + file.name, 'success');
            showProgress(100);
            // Hide progress bar after 2 seconds
            setTimeout(() => hideProgress(), 2000);
        } catch (error) {
            console.error('Upload error:', error);
            updateStatus('✗ Upload failed: ' + error.message, 'error');
            hideProgress();
        }
        
        // Reset file input
        fileInput.value = '';
    });
    
    console.log('File input change listener added');
    console.log('Simple Streaming Upload initialization complete!');
}

// Start initialization
initializeUpload();

/**
 * Upload file using streaming with XMLHttpRequest (to support progress tracking)
 */
async function uploadFileStreaming(file) {
    const startTime = Date.now();
    
    console.log('→ Starting upload request with progress tracking...');
    console.log('→ JWT Token:', window.jwtToken ? 'Present (' + window.jwtToken.substring(0, 20) + '...)' : 'Missing!');
    console.log('→ File type:', file.type || 'application/octet-stream');
    
    // Get CSRF token if present
    const csrfToken = getCsrfToken();
    
    return new Promise((resolve, reject) => {
        const xhr = new XMLHttpRequest();
        
        // Track upload progress
        xhr.upload.addEventListener('progress', (event) => {
            if (event.lengthComputable) {
                const percentComplete = (event.loaded / event.total) * 100;
                console.log(`Upload progress: ${percentComplete.toFixed(2)}% (${formatBytes(event.loaded)} / ${formatBytes(event.total)})`);
                showProgress(percentComplete, event.loaded, event.total);
            }
        });
        
        // Handle upload completion
        xhr.addEventListener('load', () => {
            const duration = Date.now() - startTime;
            
            console.log('╔════════════════════════════════════════════════════════════════════════════');
            console.log('║ JAVASCRIPT UPLOAD RESPONSE');
            console.log('║ Status:', xhr.status, xhr.statusText);
            console.log('║ Duration:', duration, 'ms');
            console.log('╚════════════════════════════════════════════════════════════════════════════');
            
            if (xhr.status >= 200 && xhr.status < 300) {
                try {
                    const result = JSON.parse(xhr.responseText);
                    console.log('Response data:', result);
                    
                    // Dispatch custom event to notify Vaadin that upload completed
                    window.dispatchEvent(new CustomEvent('uploadSuccess', { detail: result }));
                    
                    resolve(result);
                } catch (error) {
                    reject(new Error('Invalid JSON response: ' + error.message));
                }
            } else {
                reject(new Error('Upload failed: ' + xhr.status + ' - ' + xhr.responseText));
            }
        });
        
        // Handle errors
        xhr.addEventListener('error', () => {
            reject(new Error('Network error during upload'));
        });
        
        xhr.addEventListener('abort', () => {
            reject(new Error('Upload aborted'));
        });
        
        // Open connection and set headers
        xhr.open('POST', '/api/simple-upload/stream', true);
        xhr.setRequestHeader('Authorization', 'Bearer ' + window.jwtToken);
        xhr.setRequestHeader('Content-Type', 'application/octet-stream');
        xhr.setRequestHeader('X-Filename', file.name);
        xhr.setRequestHeader('X-Content-Type', file.type || 'application/octet-stream');
        if (csrfToken) {
            xhr.setRequestHeader('X-CSRF-TOKEN', csrfToken);
        }
        
        // Send file
        xhr.send(file);
    });
}

/**
 * Update status message
 */
function updateStatus(message, type = 'info') {
    const statusText = document.getElementById('statusText');
    if (statusText) {
        statusText.textContent = message;
        statusText.style.color = type === 'success' ? 'green' : type === 'error' ? 'red' : 'black';
    }
    console.log('Status:', message);
}

/**
 * Show and update progress bar
 */
function showProgress(percent, loaded, total) {
    const progressContainer = document.getElementById('progressContainer');
    const progressFill = document.getElementById('progressFill');
    const progressText = document.getElementById('progressText');
    
    if (progressContainer) {
        progressContainer.style.display = 'block';
    }
    
    if (progressFill) {
        progressFill.style.width = percent + '%';
    }
    
    if (progressText) {
        if (loaded !== undefined && total !== undefined) {
            progressText.textContent = `${percent.toFixed(1)}% (${formatBytes(loaded)} / ${formatBytes(total)})`;
        } else {
            progressText.textContent = `${percent.toFixed(1)}%`;
        }
    }
}

/**
 * Hide progress bar
 */
function hideProgress() {
    const progressContainer = document.getElementById('progressContainer');
    if (progressContainer) {
        progressContainer.style.display = 'none';
    }
    
    const progressFill = document.getElementById('progressFill');
    if (progressFill) {
        progressFill.style.width = '0%';
    }
    
    const progressText = document.getElementById('progressText');
    if (progressText) {
        progressText.textContent = '';
    }
}

/**
 * Format bytes to human readable string
 */
function formatBytes(bytes) {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
}

/**
 * Get CSRF token from meta tag or cookie
 */
function getCsrfToken() {
    // Try meta tag first
    const metaTag = document.querySelector('meta[name="_csrf"]');
    if (metaTag) {
        return metaTag.getAttribute('content');
    }
    
    // Try cookie
    const cookies = document.cookie.split(';');
    for (let cookie of cookies) {
        const [name, value] = cookie.trim().split('=');
        if (name === 'XSRF-TOKEN') {
            return decodeURIComponent(value);
        }
    }
    
    return null;
}

console.log('Simple Streaming Upload JavaScript loaded successfully');
