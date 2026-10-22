package in.nic.kvkekh.content;

import java.util.List;

/**
 * A resolved navigation entry. Groups have no path of their own and are rendered as disclosure buttons.
 */
public record MenuItem(String label, String path, List<MenuItem> children) {

    public MenuItem {
        children = List.copyOf(children);
    }

    public boolean isGroup() {
        return path == null;
    }

    /** True if this item, or any descendant, links to {@code currentPath}. Used to mark the active menu. */
    public boolean contains(String currentPath) {
        if (currentPath == null) {
            return false;
        }
        if (currentPath.equals(path)) {
            return true;
        }
        return children.stream().anyMatch(child -> child.contains(currentPath));
    }
}
