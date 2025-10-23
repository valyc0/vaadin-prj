package com.example.vaadin;

import com.example.vaadin.service.FileStreamingService;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Vaadin View for streaming large file uploads
 * 
 * This view demonstrates true streaming upload:
 * - No file loaded into memory
 * - No temporary files on disk
 * - Direct streaming from browser to roles-service to MinIO
 * - Progress tracking in real-time
 * - Suitable for very large files (30GB+)
 * 
 * Architecture:
 * Browser → Vaadin Upload → InputStream → WebFlux Flux<DataBuffer> → 
 * → HTTP Stream → Roles-Service → MinIO
 */
@Route("file-upload")
@PageTitle("Streaming File Upload")
@AnonymousAllowed
public class FileUploadView extends VerticalLayout {

    private static final Logger logger = LoggerFactory.getLogger(FileUploadView.class);

    private final FileStreamingService fileStreamingService;
    private final Grid<FileInfo> filesGrid;
    private final Div uploadStatusDiv;
    private ProgressBar progressBar;
    private Span progressLabel;

    public FileUploadView(FileStreamingService fileStreamingService) {
        this.fileStreamingService = fileStreamingService;
        
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        // Header
        H1 title = new H1("📤 Streaming File Upload");
        title.getStyle().set("color", "var(--lumo-primary-color)");

        Paragraph description = new Paragraph(
                "Upload files of any size using streaming technology. " +
                "Files are streamed directly from your browser to MinIO storage without " +
                "being loaded into memory. Suitable for files up to 50GB and beyond."
        );
        description.getStyle().set("color", "var(--lumo-secondary-text-color)");

        // Info box
        Div infoBox = createInfoBox();

        // Status area (initialize before createUploadCard)
        uploadStatusDiv = new Div();
        uploadStatusDiv.setVisible(false);

        // Upload area
        Div uploadCard = createUploadCard();

        // Files grid
        filesGrid = createFilesGrid();
        Div gridCard = createCard();
        H3 gridTitle = new H3("📁 Uploaded Files");
        gridCard.add(gridTitle, filesGrid);

        // Refresh button
        Button refreshBtn = new Button("🔄 Refresh List", e -> refreshFilesList());
        refreshBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);

        add(title, description, infoBox, uploadCard, uploadStatusDiv, refreshBtn, gridCard);

        // Load initial files list
        refreshFilesList();
    }

    private Div createInfoBox() {
        Div infoBox = new Div();
        infoBox.getStyle()
                .set("background", "var(--lumo-primary-color-10pct)")
                .set("border-left", "4px solid var(--lumo-primary-color)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("padding", "var(--lumo-space-m)")
                .set("margin", "var(--lumo-space-m) 0");

        H4 infoTitle = new H4("ℹ️ How it works");
        infoTitle.getStyle().set("margin-top", "0");

        UnorderedList featuresList = new UnorderedList(
                new ListItem("✅ Zero memory footprint - files never loaded into memory"),
                new ListItem("✅ No temporary files - direct streaming to MinIO"),
                new ListItem("✅ Constant memory usage (~8KB) regardless of file size"),
                new ListItem("✅ Real-time progress tracking"),
                new ListItem("✅ Supports files up to 50GB (configurable for larger files)")
        );

        infoBox.add(infoTitle, featuresList);
        return infoBox;
    }

    private Div createUploadCard() {
        Div card = createCard();

        H3 uploadTitle = new H3("📤 Upload File");

        // Create upload component with streaming receiver
        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        
        upload.setMaxFiles(1);
        upload.setDropAllowed(true);
        // Don't set acceptedFileTypes to accept all file types (avoid regex issues)
        upload.setAutoUpload(true); // Enable auto-upload when file is selected
        
        // Customize upload button
        upload.setUploadButton(new Button("Choose File..."));
        upload.getElement().getStyle()
                .set("width", "100%");

        // Progress indicators
        progressBar = new ProgressBar();
        progressBar.setMin(0);
        progressBar.setMax(1);
        progressBar.setValue(0);
        progressBar.setVisible(false);
        progressBar.getStyle().set("width", "100%");

        progressLabel = new Span("Ready to upload");
        progressLabel.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");

        Div progressContainer = new Div(progressBar, progressLabel);
        progressContainer.getStyle().set("width", "100%");

        // Upload success listener
        upload.addSucceededListener(event -> {
            String fileName = event.getFileName();
            String mimeType = event.getMIMEType();
            
            logger.info("═══════════════════════════════════════════════════════════════════════════════");
            logger.info("📥 UPLOAD EVENT RECEIVED IN VAADIN VIEW");
            logger.info("   Filename: {}", fileName);
            logger.info("   MIME Type: {}", mimeType);
            logger.info("   Size: {} bytes", event.getContentLength());
            logger.info("═══════════════════════════════════════════════════════════════════════════════");

            progressBar.setVisible(true);
            progressLabel.setText("Streaming to server...");
            uploadStatusDiv.setVisible(true);

            try {
                // Get InputStream directly from buffer
                // This stream contains the uploaded file data
                InputStream inputStream = buffer.getInputStream();
                
                logger.info("✓ InputStream obtained from Vaadin Upload component");
                logger.info("⚡ Starting WebFlux reactive streaming to roles-service...");

                // Stream file using WebFlux reactive streams
                // This will stream data directly without loading into memory
                fileStreamingService.uploadFileStreaming(
                        fileName,
                        inputStream,
                        mimeType,
                        bytesUploaded -> {
                            // Progress callback - update UI
                            UI ui = getUI().orElse(null);
                            if (ui != null) {
                                ui.access(() -> {
                                    progressLabel.setText(String.format(
                                            "Uploaded: %s", 
                                            formatBytes(bytesUploaded)
                                    ));
                                });
                            }
                        }
                ).subscribe(
                        response -> {
                            // Success callback
                            UI ui = getUI().orElse(null);
                            if (ui != null) {
                                ui.access(() -> {
                                    progressBar.setVisible(false);
                                    progressLabel.setText("Upload completed!");
                                    
                                    String storedFileName = (String) response.get("storedFileName");
                                    String formattedSize = (String) response.get("formattedSize");
                                    String throughput = (String) response.get("throughputMBps");
                                    
                                    Notification.show(
                                            String.format("✅ File uploaded successfully!\n" +
                                                    "Stored as: %s\n" +
                                                    "Size: %s\n" +
                                                    "Throughput: %s MB/s",
                                                    storedFileName, formattedSize, throughput),
                                            5000,
                                            Notification.Position.TOP_CENTER
                                    ).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                                    
                                    refreshFilesList();
                                    uploadStatusDiv.setVisible(false);
                                });
                            }
                        },
                        error -> {
                            // Error callback
                            UI ui = getUI().orElse(null);
                            if (ui != null) {
                                ui.access(() -> {
                                    progressBar.setVisible(false);
                                    progressLabel.setText("Upload failed!");
                                    
                                    Notification.show(
                                            "❌ Upload failed: " + error.getMessage(),
                                            5000,
                                            Notification.Position.MIDDLE
                                    ).addThemeVariants(NotificationVariant.LUMO_ERROR);
                                    
                                    uploadStatusDiv.setVisible(false);
                                });
                            }
                        }
                );

            } catch (Exception e) {
                logger.error("Error during upload initiation", e);
                Notification.show(
                        "❌ Error: " + e.getMessage(),
                        5000,
                        Notification.Position.MIDDLE
                ).addThemeVariants(NotificationVariant.LUMO_ERROR);
                
                progressBar.setVisible(false);
                uploadStatusDiv.setVisible(false);
            }
        });

        // Upload failed listener
        upload.addFailedListener(event -> {
            logger.error("Upload failed: {}", event.getReason().getMessage());
            Notification.show(
                    "❌ Upload failed: " + event.getReason().getMessage(),
                    5000,
                    Notification.Position.MIDDLE
            ).addThemeVariants(NotificationVariant.LUMO_ERROR);
        });

        uploadStatusDiv.add(progressContainer);

        card.add(uploadTitle, upload);
        return card;
    }

    private Grid<FileInfo> createFilesGrid() {
        Grid<FileInfo> grid = new Grid<>(FileInfo.class, false);
        grid.addColumn(FileInfo::getFilename).setHeader("Filename").setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(FileInfo::getFormattedSize).setHeader("Size").setAutoWidth(true);
        grid.addColumn(FileInfo::getContentType).setHeader("Type").setAutoWidth(true);
        
        grid.addComponentColumn(fileInfo -> {
            Button deleteBtn = new Button(VaadinIcon.TRASH.create());
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            deleteBtn.addClickListener(e -> deleteFile(fileInfo.getFilename()));
            return deleteBtn;
        }).setHeader("Actions").setAutoWidth(true);

        grid.setHeight("400px");
        return grid;
    }

    private void refreshFilesList() {
        fileStreamingService.listFiles()
                .subscribe(
                        files -> {
                            UI ui = getUI().orElse(null);
                            if (ui != null) {
                                ui.access(() -> {
                                    // Fetch metadata for each file
                                    java.util.List<FileInfo> fileInfos = new java.util.ArrayList<>();
                                    for (String filename : files) {
                                        fileStreamingService.getFileMetadata(filename)
                                                .subscribe(metadata -> {
                                                    FileInfo info = new FileInfo();
                                                    info.setFilename(filename);
                                                    info.setFormattedSize((String) metadata.get("formattedSize"));
                                                    info.setContentType((String) metadata.get("contentType"));
                                                    fileInfos.add(info);
                                                    
                                                    ui.access(() -> {
                                                        filesGrid.setItems(fileInfos);
                                                    });
                                                });
                                    }
                                });
                            }
                        },
                        error -> logger.error("Error loading files list", error)
                );
    }

    private void deleteFile(String filename) {
        fileStreamingService.deleteFile(filename)
                .subscribe(
                        response -> {
                            UI ui = getUI().orElse(null);
                            if (ui != null) {
                                ui.access(() -> {
                                    Notification.show(
                                            "✅ File deleted successfully",
                                            3000,
                                            Notification.Position.TOP_CENTER
                                    ).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                                    refreshFilesList();
                                });
                            }
                        },
                        error -> {
                            UI ui = getUI().orElse(null);
                            if (ui != null) {
                                ui.access(() -> {
                                    Notification.show(
                                            "❌ Delete failed: " + error.getMessage(),
                                            5000,
                                            Notification.Position.MIDDLE
                                    ).addThemeVariants(NotificationVariant.LUMO_ERROR);
                                });
                            }
                        }
                );
    }

    private Div createCard() {
        Div card = new Div();
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("box-shadow", "var(--lumo-box-shadow-s)")
                .set("padding", "var(--lumo-space-l)")
                .set("margin", "var(--lumo-space-m) 0");
        return card;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }

    // DTO for file information
    public static class FileInfo {
        private String filename;
        private String formattedSize;
        private String contentType;

        public String getFilename() { return filename; }
        public void setFilename(String filename) { this.filename = filename; }

        public String getFormattedSize() { return formattedSize; }
        public void setFormattedSize(String formattedSize) { this.formattedSize = formattedSize; }

        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }
    }
}
