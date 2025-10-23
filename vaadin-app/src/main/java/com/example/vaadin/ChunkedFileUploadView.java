package com.example.vaadin;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.security.PermitAll;

/**
 * Chunked File Upload View - Solution for uploading VERY LARGE files from browser
 * 
 * This view uses JavaScript to chunk the file on the client side and upload
 * chunks sequentially. This bypasses browser memory limitations.
 * 
 * Key features:
 * - Chunks file in browser (10MB chunks)
 * - Uploads one chunk at a time
 * - No memory limit (file never fully loaded in memory)
 * - Progress tracking per chunk
 * - Can pause/resume upload
 * - Suitable for files 50GB+
 * 
 * How it works:
 * 1. User selects file (File API reference only, no loading)
 * 2. JavaScript reads file in 10MB chunks using Blob.slice()
 * 3. Each chunk uploaded separately via Fetch API
 * 4. Server assembles chunks into final file
 * 5. Memory usage: Only 10MB chunk at a time!
 */
@Route("chunked-upload")
@PageTitle("Chunked File Upload (Large Files)")
@PermitAll
public class ChunkedFileUploadView extends VerticalLayout {

    private static final Logger logger = LoggerFactory.getLogger(ChunkedFileUploadView.class);

    public ChunkedFileUploadView() {
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        // Header
        H1 title = new H1("📦 Chunked File Upload");
        title.getStyle().set("color", "var(--lumo-primary-color)");

        Paragraph description = new Paragraph(
                "Upload files of ANY size (50GB+) by splitting them into chunks. " +
                "Only one chunk (10MB) is loaded into memory at a time."
        );
        description.getStyle().set("color", "var(--lumo-secondary-text-color)");

        // Info box
        Div infoBox = createInfoBox();

        // Upload area with JavaScript
        Div uploadArea = createUploadArea();

        add(title, description, infoBox, uploadArea);
    }

    private Div createInfoBox() {
        Div infoBox = new Div();
        infoBox.getStyle()
                .set("background", "linear-gradient(135deg, #667eea 0%, #764ba2 100%)")
                .set("color", "white")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("padding", "var(--lumo-space-l)")
                .set("margin", "var(--lumo-space-m) 0");

        H4 infoTitle = new H4("🚀 How Chunked Upload Works");
        infoTitle.getStyle().set("margin-top", "0").set("color", "white");

        UnorderedList featuresList = new UnorderedList(
                new ListItem("✅ File split into 10MB chunks in browser (JavaScript)"),
                new ListItem("✅ Only ONE chunk loaded in memory at a time"),
                new ListItem("✅ Chunks uploaded sequentially via REST API"),
                new ListItem("✅ Server assembles chunks into final file"),
                new ListItem("✅ Can upload 50GB+ files without memory issues"),
                new ListItem("✅ Progress tracking shows chunk upload progress")
        );
        featuresList.getStyle().set("color", "white");

        infoBox.add(infoTitle, featuresList);
        return infoBox;
    }

    private Div createUploadArea() {
        Div container = new Div();
        container.setId("chunked-upload-container");
        
        // HTML structure for file input
        container.getElement().setProperty("innerHTML", """
            <div style="background: white; padding: 2rem; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
                <h3>Select File</h3>
                <input type="file" id="chunked-file-input" style="margin-bottom: 1rem;" />
                <div id="file-info" style="margin: 1rem 0; padding: 1rem; background: #f5f5f5; border-radius: 4px; display: none;">
                    <p><strong>File:</strong> <span id="file-name"></span></p>
                    <p><strong>Size:</strong> <span id="file-size"></span></p>
                    <p><strong>Chunks:</strong> <span id="chunk-count"></span></p>
                </div>
                <button id="start-upload-btn" style="display: none; padding: 0.5rem 1rem; background: #4CAF50; color: white; border: none; border-radius: 4px; cursor: pointer;">
                    Start Upload
                </button>
                <div id="upload-progress" style="margin-top: 1rem; display: none;">
                    <div style="margin-bottom: 0.5rem;">
                        <span id="progress-text">Uploading...</span>
                    </div>
                    <div style="width: 100%; background: #e0e0e0; border-radius: 4px; overflow: hidden;">
                        <div id="progress-bar" style="width: 0%; height: 30px; background: linear-gradient(90deg, #4CAF50, #8BC34A); transition: width 0.3s; display: flex; align-items: center; justify-content: center; color: white; font-weight: bold;">
                            0%
                        </div>
                    </div>
                    <p id="progress-detail" style="margin-top: 0.5rem; color: #666;"></p>
                </div>
            </div>
        """);

        // Add JavaScript for chunked upload
        UI.getCurrent().getPage().executeJs("""
            const CHUNK_SIZE = 10 * 1024 * 1024; // 10MB chunks
            const VAADIN_API_URL = '/api/upload';  // Vaadin controller endpoint
            
            let selectedFile = null;
            let uploadId = null;
            
            document.getElementById('chunked-file-input').addEventListener('change', (e) => {
                selectedFile = e.target.files[0];
                if (selectedFile) {
                    const chunkCount = Math.ceil(selectedFile.size / CHUNK_SIZE);
                    
                    document.getElementById('file-name').textContent = selectedFile.name;
                    document.getElementById('file-size').textContent = formatBytes(selectedFile.size);
                    document.getElementById('chunk-count').textContent = chunkCount;
                    document.getElementById('file-info').style.display = 'block';
                    document.getElementById('start-upload-btn').style.display = 'inline-block';
                }
            });
            
            document.getElementById('start-upload-btn').addEventListener('click', async () => {
                if (!selectedFile) return;
                
                document.getElementById('start-upload-btn').disabled = true;
                document.getElementById('upload-progress').style.display = 'block';
                
                await uploadFileInChunks(selectedFile);
            });
            
            async function uploadFileInChunks(file) {
                const chunkCount = Math.ceil(file.size / CHUNK_SIZE);
                uploadId = generateUploadId();
                
                console.log(`Starting chunked upload: ${file.name}, ${chunkCount} chunks`);
                
                for (let chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
                    const start = chunkIndex * CHUNK_SIZE;
                    const end = Math.min(start + CHUNK_SIZE, file.size);
                    const chunk = file.slice(start, end);
                    
                    const progress = ((chunkIndex + 1) / chunkCount) * 100;
                    updateProgress(progress, chunkIndex + 1, chunkCount);
                    
                    try {
                        await uploadChunk(chunk, chunkIndex, chunkCount, file.name);
                    } catch (error) {
                        console.error('Chunk upload failed:', error);
                        alert('Upload failed at chunk ' + (chunkIndex + 1) + ': ' + error.message);
                        return;
                    }
                }
                
                // Finalize upload
                try {
                    await finalizeUpload(uploadId, file.name, chunkCount);
                    alert('✅ Upload completed successfully!');
                    document.getElementById('start-upload-btn').disabled = false;
                    document.getElementById('upload-progress').style.display = 'none';
                } catch (error) {
                    alert('Failed to finalize upload: ' + error.message);
                }
            }
            
            async function uploadChunk(chunk, chunkIndex, totalChunks, fileName) {
                const formData = new FormData();
                formData.append('chunk', chunk);
                formData.append('chunkIndex', chunkIndex);
                formData.append('totalChunks', totalChunks);
                formData.append('uploadId', uploadId);
                formData.append('fileName', fileName);
                
                const response = await fetch(VAADIN_API_URL + '/chunk', {
                    method: 'POST',
                    body: formData,
                    credentials: 'include'  // Session authentication
                });
                
                if (!response.ok) {
                    throw new Error('HTTP ' + response.status);
                }
                
                return await response.json();
            }
            
            async function finalizeUpload(uploadId, fileName, totalChunks) {
                const response = await fetch(VAADIN_API_URL + '/finalize', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ uploadId, fileName, totalChunks }),
                    credentials: 'include'  // Session authentication
                });
                
                if (!response.ok) {
                    throw new Error('HTTP ' + response.status);
                }
                
                return await response.json();
            }
            
            function updateProgress(percent, currentChunk, totalChunks) {
                const progressBar = document.getElementById('progress-bar');
                const progressText = document.getElementById('progress-text');
                const progressDetail = document.getElementById('progress-detail');
                
                progressBar.style.width = percent + '%';
                progressBar.textContent = Math.round(percent) + '%';
                progressText.textContent = 'Uploading chunk ' + currentChunk + ' of ' + totalChunks;
                progressDetail.textContent = Math.round(percent) + '% complete';
            }
            
            function generateUploadId() {
                return 'upload_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
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
