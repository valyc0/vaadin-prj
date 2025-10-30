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
        
        try {
            await uploadFileStreaming(file);
            updateStatus('✓ Upload completed successfully: ' + file.name, 'success');
        } catch (error) {
            console.error('Upload error:', error);
            updateStatus('✗ Upload failed: ' + error.message, 'error');
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
 * Upload file using streaming with fetch API
 */
async function uploadFileStreaming(file) {
    const startTime = Date.now();
    
    console.log('→ Starting fetch request...');
    console.log('→ JWT Token:', window.jwtToken ? 'Present (' + window.jwtToken.substring(0, 20) + '...)' : 'Missing!');
    console.log('→ File type:', file.type || 'application/octet-stream');
    
    // Get CSRF token if present
    const csrfToken = getCsrfToken();
    
    // Upload with fetch - send file directly as octet-stream
    const response = await fetch('/api/simple-upload/stream', {
        method: 'POST',
        headers: {
            'Authorization': 'Bearer ' + window.jwtToken,
            'Content-Type': 'application/octet-stream',
            'X-Filename': file.name,
            'X-Content-Type': file.type || 'application/octet-stream',
            ...(csrfToken && { 'X-CSRF-TOKEN': csrfToken })
        },
        body: file  // Send file directly as body
    });
    
    const duration = Date.now() - startTime;
    
    console.log('╔════════════════════════════════════════════════════════════════════════════');
    console.log('║ JAVASCRIPT UPLOAD RESPONSE');
    console.log('║ Status:', response.status, response.statusText);
    console.log('║ Duration:', duration, 'ms');
    console.log('╚════════════════════════════════════════════════════════════════════════════');
    
    if (!response.ok) {
        const errorText = await response.text();
        throw new Error('Upload failed: ' + response.status + ' - ' + errorText);
    }
    
    const result = await response.json();
    console.log('Response data:', result);
    
    // Dispatch custom event to notify Vaadin that upload completed
    window.dispatchEvent(new CustomEvent('uploadSuccess', { detail: result }));
    
    return result;
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
