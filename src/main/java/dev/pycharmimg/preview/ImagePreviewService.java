package dev.pycharmimg.preview;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.awt.RelativePoint;
import java.awt.Point;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.Future;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

@Service(Service.Level.PROJECT)
public final class ImagePreviewService implements Disposable {
    private Editor activeEditor;
    private String activeUrl;
    private int activeStart;
    private long documentStamp;
    private long generation;
    private RelativePoint anchor;
    private Future<?> task;
    private ImageLoader.Request request;
    private JBPopup popup;

    private final ImageLoader loader = new ImageLoader();
    private final ImageRequestExecutor requests = new ImageRequestExecutor();
    private final Timer hoverDelay = new Timer(400, event -> requestImage());
    private final DocumentListener documentListener = new DocumentListener() {
        @Override
        public void documentChanged(DocumentEvent event) {
            clear();
        }
    };
    private final HierarchyListener hierarchyListener = event -> {
        if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0
                && activeEditor != null && !activeEditor.getContentComponent().isShowing()) {
            clear();
        }
    };

    public ImagePreviewService() {
        hoverDelay.setRepeats(false);
    }

    void mouseMoved(EditorMouseEvent event) {
        Editor editor = event.getEditor();
        if (event.getArea() != EditorMouseEventArea.EDITING_AREA || !event.isOverText()
                || editor.isDisposed() || !editor.getContentComponent().isShowing()) {
            clear();
            return;
        }

        Document document = editor.getDocument();
        int offset = event.getOffset();
        if (offset < 0 || offset >= document.getTextLength()) {
            clear();
            return;
        }
        int line = document.getLineNumber(offset);
        int start = document.getLineStartOffset(line);
        int end = document.getLineEndOffset(line);
        if (end - start > ImageLinkDetector.MAX_LINE_LENGTH) {
            clear();
            return;
        }
        String text = document.getText(new TextRange(start, end));
        ImageLinkDetector.Link link = ImageLinkDetector.at(text, offset - start);
        if (link == null) {
            clear();
            return;
        }

        if (activeEditor == editor && activeStart == start + link.start()
                && activeUrl.equals(link.url()) && documentStamp == document.getModificationStamp()
                && (hoverDelay.isRunning() || task != null || popup != null && popup.isVisible())) {
            return;
        }

        clear();
        activeEditor = editor;
        activeUrl = link.url();
        activeStart = start + link.start();
        documentStamp = document.getModificationStamp();
        Point point = event.getMouseEvent().getPoint();
        anchor = new RelativePoint(event.getMouseEvent().getComponent(), new Point(point.x + 16, point.y + 16));
        document.addDocumentListener(documentListener);
        editor.getContentComponent().addHierarchyListener(hierarchyListener);
        hoverDelay.start();
    }

    void mouseExited(EditorMouseEvent event) {
        if (activeEditor == event.getEditor()) {
            clear();
        }
    }

    private void requestImage() {
        if (!isCurrent(generation)) {
            return;
        }
        String url = activeUrl;
        long requestGeneration = generation;
        BufferedImage cached = loader.cached(url);
        if (cached != null) {
            showImage(requestGeneration, cached);
            return;
        }
        request = new ImageLoader.Request();
        ImageLoader.Request currentRequest = request;
        task = requests.submit(() -> {
            BufferedImage image;
            try {
                image = loader.load(url, currentRequest);
            } catch (IOException | IllegalArgumentException ignored) {
                image = null;
            }
            BufferedImage result = image;
            ApplicationManager.getApplication().invokeLater(() -> {
                if (requestGeneration == generation) {
                    task = null;
                    request = null;
                    if (result != null) {
                        showImage(requestGeneration, result);
                    }
                }
            });
        });
    }

    private boolean isCurrent(long requestGeneration) {
        return requestGeneration == generation && activeEditor != null && !activeEditor.isDisposed()
                && activeEditor.getContentComponent().isShowing()
                && activeEditor.getDocument().getModificationStamp() == documentStamp;
    }

    private void showImage(long requestGeneration, BufferedImage image) {
        if (!isCurrent(requestGeneration)) {
            return;
        }
        JLabel label = new JLabel(new ImageIcon(image));
        JPanel content = new JPanel();
        content.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        content.add(label);
        popup = JBPopupFactory.getInstance().createComponentPopupBuilder(content, null)
                .setRequestFocus(false)
                .setFocusable(false)
                .setCancelOnClickOutside(true)
                .setCancelOnOtherWindowOpen(true)
                .setCancelOnWindowDeactivation(true)
                .createPopup();
        popup.show(anchor);
    }

    private void clear() {
        hoverDelay.stop();
        generation++;
        if (task != null) {
            request.cancel();
            requests.cancel(task);
            task = null;
            request = null;
        }
        if (activeEditor != null) {
            activeEditor.getDocument().removeDocumentListener(documentListener);
            activeEditor.getContentComponent().removeHierarchyListener(hierarchyListener);
        }
        if (popup != null) {
            popup.cancel();
            popup = null;
        }
        activeEditor = null;
        activeUrl = null;
        anchor = null;
    }

    @Override
    public void dispose() {
        if (SwingUtilities.isEventDispatchThread()) {
            clear();
            requests.close();
        } else {
            SwingUtilities.invokeLater(() -> {
                clear();
                requests.close();
            });
        }
    }
}
