package dev.pycharmimg.preview;

import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.project.Project;

public final class ImagePreviewMouseListener implements EditorMouseMotionListener, EditorMouseListener {
    @Override
    public void mouseMoved(EditorMouseEvent event) {
        ImagePreviewService service = service(event);
        if (service != null) {
            service.mouseMoved(event);
        }
    }

    @Override
    public void mouseDragged(EditorMouseEvent event) {
        mouseExited(event);
    }

    @Override
    public void mouseExited(EditorMouseEvent event) {
        ImagePreviewService service = service(event);
        if (service != null) {
            service.mouseExited(event);
        }
    }

    @Override
    public void mousePressed(EditorMouseEvent event) {
        mouseExited(event);
    }

    private static ImagePreviewService service(EditorMouseEvent event) {
        Project project = event.getEditor().getProject();
        return project == null || project.isDisposed() ? null : project.getService(ImagePreviewService.class);
    }
}
