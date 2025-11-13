package com.example.vaadin;

import com.example.vaadin.layout.MainLayout;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * Simple JavaScript-based file upload view
 * 
 * This view uses pure JavaScript Fetch API to upload files directly
 * to roles-service, bypassing Vaadin Upload component complexity.
 * 
 * Advantages:
 * - Simple implementation
 * - Direct HTTP POST to backend
 * - No Vaadin component overhead
 * 
 * Limitations:
 * - Browser must load entire file in memory
 * - Practical limit: ~2GB (browser dependent)
 * - For larger files, use ChunkedFileUploadView instead
 * 
 * Use case: Quick uploads of files < 1GB
 */
@Route(value = "simple-upload", layout = MainLayout.class)
@PageTitle("Simple File Upload")
@jakarta.annotation.security.PermitAll
public class SimpleFileUploadView extends VerticalLayout {

    public SimpleFileUploadView() {
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        H1 title = new H1("📤 Simple File Upload");
        Paragraph description = new Paragraph(
                "Upload files up to 2GB using direct JavaScript Fetch API. " +
                "For larger files, use the Chunked Upload view."
        );

        Div uploadContainer = createUploadContainer();

        add(title, description, uploadContainer);
    }

    private Div createUploadContainer() {
        Div container = new Div();
        container.setId("simple-upload-container");

        container.getElement().setProperty("innerHTML", """
            <div style="max-width: 800px; margin: 0 auto;">
                <div style="background: white; padding: 2rem; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
                    <h3>Select File</h3>
                    <input type="file" id="simple-file-input" style="margin: 1rem 0; padding: 0.5rem;" />
                    <button id="upload-btn" disabled style="padding: 0.75rem 2rem; background: #4CAF50; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 1rem;">
                        Upload to Roles Service
                    </button>
                    
                    <div id="upload-status" style="margin-top: 2rem; display: none;">
                        <div style="margin-bottom: 1rem;">
                            <strong>Status:</strong> <span id="status-text">Uploading...</span>
                        </div>
                        <div style="width: 100%; background: #e0e0e0; border-radius: 4px; overflow: hidden; height: 30px;">
                            <div id="progress-bar" style="width: 0%; height: 100%; background: linear-gradient(90deg, #4CAF50, #8BC34A); display: flex; align-items: center; justify-content: center; color: white; font-weight: bold; transition: width 0.3s;">
                                0%
                            </div>
                        </div>
                        <div id="upload-info" style="margin-top: 1rem; padding: 1rem; background: #f5f5f5; border-radius: 4px; font-family: monospace; font-size: 0.9rem;">
                        </div>
                    </div>
                    
                    <div id="result" style="margin-top: 2rem; display: none; padding: 1rem; border-radius: 4px;">
                    </div>
                </div>
                
                <div style="margin-top: 2rem; padding: 1rem; background: #fff3cd; border-left: 4px solid #ffc107; border-radius: 4px;">
                    <strong>⚠️ Note:</strong> Files are loaded into browser memory before upload.
                    For files larger than 1GB, consider using the <a href="/chunked-upload" style="color: #007bff;">Chunked Upload</a> view.
                </div>
            </div>
        """);

        // Add JavaScript for file upload
        UI.getCurrent().getPage().executeJs("""
            const VAADIN_API_URL = '/api/upload';
            
            let selectedFile = null;
            
            // File selection
            document.getElementById('simple-file-input').addEventListener('change', (e) => {
                selectedFile = e.target.files[0];
                const uploadBtn = document.getElementById('upload-btn');
                
                if (selectedFile) {
                    uploadBtn.disabled = false;
                    uploadBtn.textContent = 'Upload ' + selectedFile.name + ' (' + formatBytes(selectedFile.size) + ')';
                } else {
                    uploadBtn.disabled = true;
                    uploadBtn.textContent = 'Upload to Roles Service';
                }
            });
            
            // Upload button
            document.getElementById('upload-btn').addEventListener('click', async () => {
                if (!selectedFile) return;
                
                document.getElementById('upload-btn').disabled = true;
                document.getElementById('upload-status').style.display = 'block';
                document.getElementById('result').style.display = 'none';
                
                await uploadFile(selectedFile);
            });
            
            async function uploadFile(file) {
                const startTime = Date.now();
                
                try {
                    // Create FormData
                    const formData = new FormData();
                    formData.append('file', file);
                    
                    updateStatus('Uploading...', 0);
                    updateInfo('Preparing upload...', file.size);
                    
                    // Upload with Fetch API to Vaadin controller
                    const response = await fetch(VAADIN_API_URL + '/stream', {
                        method: 'POST',
                        body: formData,
                        credentials: 'include'  // Session authentication
                    });
                    
                    const duration = Date.now() - startTime;
                    
                    if (!response.ok) {
                        throw new Error('HTTP ' + response.status + ': ' + response.statusText);
                    }
                    
                    const result = await response.json();
                    
                    // Calculate metrics
                    const throughputMbps = (file.size / (1024 * 1024)) / (duration / 1000);
                    
                    updateStatus('Upload completed!', 100);
                    updateInfo('Upload successful', file.size, duration, throughputMbps);
                    
                    showResult(result, true);
                    
                } catch (error) {
                    console.error('Upload failed:', error);
                    updateStatus('Upload failed!', 0);
                    showResult({ error: error.message }, false);
                }
                
                document.getElementById('upload-btn').disabled = false;
            }
            
            function updateStatus(text, percent) {
                document.getElementById('status-text').textContent = text;
                const progressBar = document.getElementById('progress-bar');
                progressBar.style.width = percent + '%';
                progressBar.textContent = Math.round(percent) + '%';
            }
            
            function updateInfo(status, size, duration, throughput) {
                const info = document.getElementById('upload-info');
                let html = `<div><strong>File Size:</strong> ${formatBytes(size)}</div>`;
                
                if (duration) {
                    html += `<div><strong>Duration:</strong> ${(duration / 1000).toFixed(2)} seconds</div>`;
                }
                
                if (throughput) {
                    html += `<div><strong>Throughput:</strong> ${throughput.toFixed(2)} MB/s</div>`;
                }
                
                html += `<div><strong>Status:</strong> ${status}</div>`;
                info.innerHTML = html;
            }
            
            function showResult(result, success) {
                const resultDiv = document.getElementById('result');
                resultDiv.style.display = 'block';
                
                if (success) {
                    resultDiv.style.background = '#d4edda';
                    resultDiv.style.borderLeft = '4px solid #28a745';
                    resultDiv.innerHTML = `
                        <h4 style="margin-top: 0; color: #155724;">✅ Upload Successful</h4>
                        <div style="font-family: monospace; font-size: 0.9rem;">
                            <div><strong>Stored Filename:</strong> ${result.storedFileName}</div>
                            <div><strong>Original Filename:</strong> ${result.originalFileName}</div>
                            <div><strong>Size:</strong> ${result.formattedSize}</div>
                            <div><strong>Content Type:</strong> ${result.contentType}</div>
                            <div><strong>Throughput:</strong> ${result.throughputMBps} MB/s</div>
                            <div><strong>Duration:</strong> ${result.durationMs} ms</div>
                        </div>
                    `;
                } else {
                    resultDiv.style.background = '#f8d7da';
                    resultDiv.style.borderLeft = '4px solid #dc3545';
                    resultDiv.innerHTML = `
                        <h4 style="margin-top: 0; color: #721c24;">❌ Upload Failed</h4>
                        <div style="font-family: monospace; font-size: 0.9rem;">
                            <div><strong>Error:</strong> ${result.error}</div>
                        </div>
                    `;
                }
            }
            
            function formatBytes(bytes) {
                if (bytes < 1024) return bytes + ' B';
                const k = 1024;
                const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
                const i = Math.floor(Math.log(bytes) / Math.log(k));
                return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i];
            }
        """);

        return container;
    }
}
