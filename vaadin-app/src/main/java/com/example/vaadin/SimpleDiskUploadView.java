package com.example.vaadin;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

/**
 * Simple view for file upload to disk using XHR JavaScript
 */
@Route("simple-disk-upload")
@PermitAll
public class SimpleDiskUploadView extends VerticalLayout {

    private final Div statusDiv;
    private final Paragraph statusText;

    public SimpleDiskUploadView() {
        setSpacing(true);
        setPadding(true);
        setWidth("100%");
        
        // Title
        H2 title = new H2("Simple File Upload to Disk");
        add(title);
        
        // Description
        Paragraph description = new Paragraph(
            "Click the button to open the upload dialog. " +
            "Select the classification level and then upload a file. " +
            "The file will be saved to /tmp/uploads/ on the server."
        );
        add(description);
        
        // Status display - initialize BEFORE upload button
        statusDiv = new Div();
        statusDiv.setId("statusDiv");
        statusDiv.getStyle().set("margin-top", "20px");
        
        statusText = new Paragraph("No upload in progress");
        statusText.setId("statusText");
        statusText.getStyle().set("font-weight", "bold");
        
        statusDiv.add(statusText);
        
        // Upload button - opens dialog
        Button openDialogButton = new Button("Open Upload Dialog");
        openDialogButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        openDialogButton.addClickListener(e -> openUploadDialog());
        
        add(openDialogButton);
        add(statusDiv);
    }
    
    private void openUploadDialog() {
        Dialog dialog = new Dialog();
        dialog.setWidth("400px");
        
        VerticalLayout dialogLayout = new VerticalLayout();
        dialogLayout.setSpacing(true);
        dialogLayout.setPadding(false);
        
        // Dialog title
        H3 dialogTitle = new H3("Upload File");
        dialogLayout.add(dialogTitle);
        
        // Classification ComboBox
        ComboBox<String> classificationCombo = new ComboBox<>("File Classification");
        classificationCombo.setItems("Classificato", "Non classificato");
        classificationCombo.setRequired(true);
        classificationCombo.setWidth("100%");
        classificationCombo.setPlaceholder("Select classification...");
        
        dialogLayout.add(classificationCombo);
        
        // Upload button (initially disabled)
        Button uploadButton = new Button("Select and Upload File");
        uploadButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        uploadButton.setEnabled(false);
        uploadButton.setId("uploadButton");
        
        // Enable upload button when classification is selected
        classificationCombo.addValueChangeListener(event -> {
            uploadButton.setEnabled(event.getValue() != null && !event.getValue().isEmpty());
        });
        
        // Upload button click - triggers file selection and upload
        uploadButton.addClickListener(e -> {
            String classification = classificationCombo.getValue();
            if (classification == null || classification.isEmpty()) {
                return;
            }
            
            // Close dialog
            dialog.close();
            
            // Reset status
            statusText.setText("Waiting for file selection...");
            statusText.getStyle().set("color", "black");
            
            // Trigger file selection and upload via XHR
            UI.getCurrent().getPage().executeJs(
                "const input = document.createElement('input');" +
                "input.type = 'file';" +
                "input.onchange = function(event) {" +
                "  const file = event.target.files[0];" +
                "  if (!file) {" +
                "    document.getElementById('statusText').textContent = 'No file selected';" +
                "    return;" +
                "  }" +
                "  " +
                "  const statusText = document.getElementById('statusText');" +
                "  statusText.textContent = 'Uploading ' + file.name + '...';" +
                "  statusText.style.color = 'black';" +
                "  " +
                "  const xhr = new XMLHttpRequest();" +
                "  xhr.open('POST', '/api/simple-upload/disk', true);" +
                "  xhr.setRequestHeader('Content-Type', 'application/octet-stream');" +
                "  xhr.setRequestHeader('X-Filename', file.name);" +
                "  xhr.setRequestHeader('X-Classification', $0);" +
                "  " +
                "  xhr.upload.onprogress = function(e) {" +
                "    if (e.lengthComputable) {" +
                "      const percent = Math.round((e.loaded / e.total) * 100);" +
                "      statusText.textContent = 'Uploading ' + file.name + ': ' + percent + '%';" +
                "    }" +
                "  };" +
                "  " +
                "  xhr.onload = function() {" +
                "    if (xhr.status === 200) {" +
                "      const response = JSON.parse(xhr.responseText);" +
                "      statusText.textContent = '✓ Upload completed: ' + response.filename + ' (' + response.size + ' bytes) - Classification: ' + response.classification + ' - Path: ' + response.path;" +
                "      statusText.style.color = 'green';" +
                "    } else {" +
                "      statusText.textContent = '✗ Upload failed: ' + xhr.statusText;" +
                "      statusText.style.color = 'red';" +
                "    }" +
                "  };" +
                "  " +
                "  xhr.onerror = function() {" +
                "    statusText.textContent = '✗ Upload error: Network error';" +
                "    statusText.style.color = 'red';" +
                "  };" +
                "  " +
                "  xhr.send(file);" +
                "};" +
                "input.click();",
                classification
            );
        });
        
        // Cancel button
        Button cancelButton = new Button("Cancel");
        cancelButton.addClickListener(e -> dialog.close());
        
        // Buttons layout
        HorizontalLayout buttonsLayout = new HorizontalLayout(uploadButton, cancelButton);
        buttonsLayout.setSpacing(true);
        dialogLayout.add(buttonsLayout);
        
        dialog.add(dialogLayout);
        dialog.open();
    }
}
