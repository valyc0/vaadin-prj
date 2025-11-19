package com.example.vaadin;

import com.example.common.dto.FileInfoDTO;
import com.example.vaadin.layout.MainLayout;
import com.example.vaadin.service.FileStreamingService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * View per visualizzare e gestire i file caricati su MinIO
 */
@Route(value = "files", layout = MainLayout.class)
@AnonymousAllowed
public class FilesView extends VerticalLayout {

    private static final Logger logger = LoggerFactory.getLogger(FilesView.class);

    private final FileStreamingService fileStreamingService;
    private final Grid<FileInfoDTO> grid;
    private final Span fileCountSpan;

    public FilesView(FileStreamingService fileStreamingService) {
        this.fileStreamingService = fileStreamingService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        // Header
        HorizontalLayout headerLayout = new HorizontalLayout();
        headerLayout.setWidthFull();
        headerLayout.setJustifyContentMode(JustifyContentMode.BETWEEN);
        headerLayout.setAlignItems(Alignment.CENTER);

        H2 title = new H2("📁 File Manager - MinIO");
        title.getStyle().set("margin", "0");

        fileCountSpan = new Span("0 file");
        fileCountSpan.getStyle()
            .set("color", "var(--lumo-secondary-text-color)")
            .set("font-size", "var(--lumo-font-size-m)");

        HorizontalLayout titleLayout = new HorizontalLayout(title, fileCountSpan);
        titleLayout.setAlignItems(Alignment.BASELINE);
        titleLayout.setSpacing(true);

        Button refreshButton = new Button("Aggiorna", VaadinIcon.REFRESH.create());
        refreshButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        refreshButton.addClickListener(e -> loadFiles());

        headerLayout.add(titleLayout, refreshButton);

        // Grid
        grid = new Grid<>(FileInfoDTO.class, false);
        grid.setSizeFull();
        grid.setPageSize(50);

        // Colonne
        grid.addColumn(FileInfoDTO::getFileName)
            .setHeader("Nome File")
            .setFlexGrow(3)
            .setSortable(true);

        grid.addColumn(FileInfoDTO::getFormattedSize)
            .setHeader("Dimensione")
            .setWidth("120px")
            .setFlexGrow(0)
            .setSortable(true);

        grid.addColumn(FileInfoDTO::getContentType)
            .setHeader("Tipo")
            .setWidth("180px")
            .setFlexGrow(0)
            .setSortable(true);

        grid.addColumn(FileInfoDTO::getLastModified)
            .setHeader("Ultima Modifica")
            .setWidth("180px")
            .setFlexGrow(0)
            .setSortable(true);

        // Colonna azioni
        grid.addColumn(new ComponentRenderer<>(file -> {
            HorizontalLayout actions = new HorizontalLayout();
            actions.setSpacing(true);

            Button downloadButton = new Button(VaadinIcon.DOWNLOAD.create());
            downloadButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
            downloadButton.getElement().setAttribute("title", "Scarica file");
            downloadButton.addClickListener(e -> downloadFile(file));

            Button detailsButton = new Button(VaadinIcon.INFO_CIRCLE.create());
            detailsButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            detailsButton.getElement().setAttribute("title", "Dettagli file");
            detailsButton.addClickListener(e -> showFileDetails(file));

            Button deleteButton = new Button(VaadinIcon.TRASH.create());
            deleteButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            deleteButton.getElement().setAttribute("title", "Elimina file");
            deleteButton.addClickListener(e -> confirmDelete(file));

            actions.add(downloadButton, detailsButton, deleteButton);
            return actions;
        }))
        .setHeader("Azioni")
        .setWidth("220px")
        .setFlexGrow(0);

        // Card per la grid
        Div gridCard = createCard();
        gridCard.setSizeFull();
        gridCard.add(grid);

        add(headerLayout, gridCard);
        setFlexGrow(1, gridCard);

        // Carica i file all'avvio
        loadFiles();
    }

    private void loadFiles() {
        try {
            logger.info("Loading files from MinIO...");
            
            String[] fileNames = fileStreamingService.listFiles();
            
            if (fileNames == null || fileNames.length == 0) {
                grid.setItems(new ArrayList<>());
                fileCountSpan.setText("0 file");
                Notification.show("ℹ️ Nessun file trovato", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_CONTRAST);
                return;
            }

            List<FileInfoDTO> files = new ArrayList<>();
            
            // Carica i metadata per ogni file
            for (String fileName : fileNames) {
                try {
                    Map<String, Object> metadata = fileStreamingService.getFileMetadata(fileName);
                    
                    FileInfoDTO fileInfo = new FileInfoDTO();
                    fileInfo.setFileName(fileName);
                    
                    if (metadata.containsKey("size")) {
                        Object sizeObj = metadata.get("size");
                        if (sizeObj instanceof Number) {
                            fileInfo.setSize(((Number) sizeObj).longValue());
                        }
                    }
                    
                    if (metadata.containsKey("contentType")) {
                        fileInfo.setContentType((String) metadata.get("contentType"));
                    }
                    
                    if (metadata.containsKey("lastModified")) {
                        fileInfo.setLastModified((String) metadata.get("lastModified"));
                    }
                    
                    if (metadata.containsKey("etag")) {
                        fileInfo.setEtag((String) metadata.get("etag"));
                    }
                    
                    files.add(fileInfo);
                    
                } catch (Exception e) {
                    logger.error("Error loading metadata for file: " + fileName, e);
                    // Aggiungi il file comunque con informazioni minime
                    FileInfoDTO fileInfo = new FileInfoDTO();
                    fileInfo.setFileName(fileName);
                    fileInfo.setContentType("N/A");
                    files.add(fileInfo);
                }
            }

            grid.setItems(files);
            fileCountSpan.setText(files.size() + (files.size() == 1 ? " file" : " file"));
            
            Notification notification = Notification.show(
                "✅ Caricati " + files.size() + " file", 
                3000, 
                Notification.Position.BOTTOM_START
            );
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            
            logger.info("Successfully loaded {} files", files.size());

        } catch (Exception e) {
            logger.error("Error loading files", e);
            Notification notification = Notification.show(
                "❌ Errore nel caricamento dei file: " + e.getMessage(), 
                5000, 
                Notification.Position.MIDDLE
            );
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void showFileDetails(FileInfoDTO file) {
        Dialog dialog = new Dialog();
        dialog.setWidth("500px");

        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);

        H3 title = new H3("📄 Dettagli File");
        title.getStyle().set("margin-top", "0");

        // Dettagli file
        Div detailsDiv = new Div();
        detailsDiv.getStyle()
            .set("background", "var(--lumo-contrast-5pct)")
            .set("padding", "var(--lumo-space-m)")
            .set("border-radius", "var(--lumo-border-radius-m)");

        StringBuilder details = new StringBuilder();
        details.append("📌 Nome: ").append(file.getFileName()).append("\n");
        details.append("📏 Dimensione: ").append(file.getFormattedSize()).append("\n");
        details.append("📋 Tipo: ").append(file.getContentType() != null ? file.getContentType() : "N/A").append("\n");
        details.append("🕐 Ultima Modifica: ").append(file.getLastModified() != null ? file.getLastModified() : "N/A").append("\n");
        if (file.getEtag() != null) {
            details.append("🔖 ETag: ").append(file.getEtag()).append("\n");
        }

        Span detailsSpan = new Span(details.toString());
        detailsSpan.getStyle()
            .set("white-space", "pre-wrap")
            .set("font-family", "monospace")
            .set("font-size", "var(--lumo-font-size-s)");

        detailsDiv.add(detailsSpan);

        Button closeButton = new Button("Chiudi", e -> dialog.close());
        closeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        layout.add(title, detailsDiv, closeButton);
        dialog.add(layout);
        dialog.open();
    }

    private void confirmDelete(FileInfoDTO file) {
        Dialog confirmDialog = new Dialog();
        confirmDialog.setWidth("400px");

        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);

        H3 title = new H3("⚠️ Conferma Eliminazione");
        title.getStyle().set("margin-top", "0").set("color", "var(--lumo-error-color)");

        Span message = new Span("Sei sicuro di voler eliminare il file:");
        Span fileName = new Span(file.getFileName());
        fileName.getStyle()
            .set("font-weight", "bold")
            .set("color", "var(--lumo-error-color)");

        HorizontalLayout buttons = new HorizontalLayout();
        buttons.setWidthFull();
        buttons.setJustifyContentMode(JustifyContentMode.END);
        buttons.setSpacing(true);

        Button cancelButton = new Button("Annulla", e -> confirmDialog.close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button deleteButton = new Button("Elimina", e -> {
            deleteFile(file);
            confirmDialog.close();
        });
        deleteButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        deleteButton.setIcon(VaadinIcon.TRASH.create());

        buttons.add(cancelButton, deleteButton);

        layout.add(title, message, fileName, buttons);
        confirmDialog.add(layout);
        confirmDialog.open();
    }

    private void downloadFile(FileInfoDTO file) {
        try {
            logger.info("Initiating streaming download for file: {}", file.getFileName());
            
            // Crea URL locale che passa attraverso il controller proxy di Vaadin
            // Questo permette un vero streaming senza caricare il file in memoria nel browser
            String downloadUrl = "/api/download/" + java.net.URLEncoder.encode(file.getFileName(), "UTF-8");
            
            logger.info("Using streaming proxy URL: {}", downloadUrl);
            
            // Usa un semplice anchor con download diretto - il controller gestirà lo streaming
            // Questo è molto più efficiente del fetch + blob per file grandi
            getUI().ifPresent(ui -> ui.getPage().executeJs(
                "const a = document.createElement('a');" +
                "a.href = $0;" +
                "a.download = '';" + // Il nome viene dal Content-Disposition header
                "document.body.appendChild(a);" +
                "a.click();" +
                "setTimeout(() => document.body.removeChild(a), 100);",
                downloadUrl
            ));
            
            Notification notification = Notification.show(
                "📥 Download avviato: " + file.getFileName(), 
                2000, 
                Notification.Position.BOTTOM_START
            );
            notification.addThemeVariants(NotificationVariant.LUMO_PRIMARY);
            
        } catch (Exception e) {
            logger.error("Error downloading file: " + file.getFileName(), e);
            Notification notification = Notification.show(
                "❌ Errore nel download: " + e.getMessage(), 
                5000, 
                Notification.Position.MIDDLE
            );
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void deleteFile(FileInfoDTO file) {
        try {
            logger.info("Deleting file: {}", file.getFileName());
            
            Map<String, Object> response = fileStreamingService.deleteFile(file.getFileName());
            
            Notification notification = Notification.show(
                "✅ File eliminato: " + file.getFileName(), 
                3000, 
                Notification.Position.BOTTOM_START
            );
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            
            // Ricarica la lista
            loadFiles();
            
        } catch (Exception e) {
            logger.error("Error deleting file: " + file.getFileName(), e);
            Notification notification = Notification.show(
                "❌ Errore nell'eliminazione del file: " + e.getMessage(), 
                5000, 
                Notification.Position.MIDDLE
            );
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private Div createCard() {
        Div card = new Div();
        card.getStyle()
            .set("background", "var(--lumo-base-color)")
            .set("border-radius", "var(--lumo-border-radius-m)")
            .set("box-shadow", "var(--lumo-box-shadow-s)")
            .set("padding", "var(--lumo-space-m)");
        return card;
    }
}
